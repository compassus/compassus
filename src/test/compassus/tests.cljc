(ns compassus.tests
  (:require #?@(:cljs [[cljs.test :refer-macros [deftest testing is are use-fixtures]]
                       [om.next :as om :refer-macros [defui]]
                       [cljsjs.react]]
                :clj  [[clojure.test :refer [deftest testing is are use-fixtures]]
                       [cellophane.next :as om :refer [defui]]])
            [compassus.core :as c]))

(def ^:dynamic *app*)

(defui Home
  static om/IQuery
  (query [this]
    [:home/title :home/content]))

(defui About
  static om/IQuery
  (query [this]
    [:about/title :about/content]))

(def init-state
  {:home/title "Home page"
   :home/content "Lorem ipsum dolor sit amet."})

(defmulti app-read om/dispatch)

(defmethod app-read :index
  [{:keys [state query]} k _]
  {:value (select-keys @state query)})

(defmulti app-mutate om/dispatch)

(defn wrap-app [test-fn]
  (binding [*app* (c/application
                    {:routes {:index (c/index-route Home)
                              :about About}
                     :reconciler-opts
                     {:state (atom init-state)
                      :parser (om/parser {:read app-read
                                          :mutate app-mutate})}})]
    ;; make setTimeout execute synchronously
    #?(:cljs (set! js/setTimeout (fn [f t] (f))))
    (test-fn)))

(use-fixtures :each wrap-app)

(deftest test-create-app
  (is (instance? compassus.core.CompassusApplication *app*))
  (is (om/reconciler? (c/get-reconciler *app*)))
  (is (= (-> @(c/get-reconciler *app*)
           (dissoc ::c/route))
        init-state))
  (is (contains? @(c/get-reconciler *app*) ::c/route))
  (is (some? (c/root-class *app*)))
  (is (fn? (c/root-class *app*)))
  (is (some? #?(:clj  (-> (c/root-class *app*) meta :component-name)
                :cljs (.. (c/root-class *app*) -prototype))))
  #?(:cljs (is (true? (.. (c/root-class *app*) -prototype -om$isComponent)))
     :clj  (is (string? (-> (c/root-class *app*) meta :component-name)))))

(deftest test-make-root-class
  (let [root (#'c/make-root-class {:routes {:index Home
                                            :about About}})]
    (is (fn? root))
    #?(:cljs (is (true? (.. root -prototype -om$isComponent)))
       :clj  (is (string? (-> root meta :component-name))))
    (is (= (om/get-query root)
           [::c/route
            {::c/route-data {:index [:home/title :home/content]
                             :about [:about/title :about/content]}}]))))

(deftest test-compassus-app?
  (is (false? (c/application? nil)))
  (is (false? (c/application? (c/get-reconciler *app*))))
  (is (false? (c/application? {})))
  (is (true? (c/application? *app*))))

(deftest test-get-current-route
  (is (thrown-with-msg? #?(:clj  AssertionError
                           :cljs js/Error)
        #"Assert failed:" (c/current-route nil)))
  (is (thrown-with-msg? #?(:clj  AssertionError
                           :cljs js/Error)
        #"Assert failed:" (c/current-route 2)))
  (is (= (c/current-route *app*) :index)))

(deftest test-index-route
  (let [app1 (c/application
               {:routes {:index Home
                         :about (c/index-route About)}
                :reconciler-opts
                {:state init-state
                 :parser (om/parser {:read app-read})}})
        app2 (c/application
               {:routes {:index (c/index-route Home)
                         :about About}
                :reconciler-opts
                {:state init-state
                 :parser (om/parser {:read app-read})}})]
    (is (thrown-with-msg? #?(:clj  AssertionError
                             :cljs js/Error)
          #"Assert failed:" (c/index-route nil)))
    (is (thrown-with-msg? #?(:clj  AssertionError
                             :cljs js/Error)
          #"Assert failed:" (c/index-route app1)))
    (is (thrown-with-msg? #?(:clj  AssertionError
                             :cljs js/Error)
          #"Assert failed:" (c/index-route (c/get-reconciler app1))))
    (is (= (c/current-route app1) :about))
    (is (= (c/current-route app2) :index))))

(deftest test-parsing
  (testing "generate parser"
    (let [user-parser (om/parser {:read app-read})
          om-parser (#'c/make-parser user-parser)
          st (merge init-state {::c/route :index})]
      (is (fn? om-parser))
      (is (= (om-parser {:state (atom st)} (om/get-query (c/root-class *app*)))
             {::c/route :index
              ::c/route-data init-state}))))
  (testing "parser integration in compassus app"
    (let [user-parser (-> *app* :config :parser)
          om-parser   (-> *app* c/get-reconciler :config :parser)]
      (is (some? user-parser))
      (is (some? om-parser))
      (is (not= user-parser om-parser)))))

(deftest test-set-route!
  (is (thrown-with-msg? #?(:clj  AssertionError
                           :cljs js/Error)
        #"Assert failed:" (c/set-route! nil :about)))
  (is (thrown-with-msg? #?(:clj  AssertionError
                           :cljs js/Error)
        #"Assert failed:" (c/set-route! 3 :about)))
  (is (thrown-with-msg? #?(:clj  AssertionError
                           :cljs js/Error)
        #"Assert failed:" (c/set-route! "foo" :about)))
  (is (thrown-with-msg? #?(:clj  AssertionError
                           :cljs js/Error)
        #"Assert failed:" (c/set-route! "foo" :about {:queue? true})))
  (let [prev-route (c/current-route *app*)]
    (c/set-route! *app* :about {:queue? false})
    (is (not= prev-route (c/current-route *app*)))
    (is (= (c/current-route *app*) :about))))

(defui Post
  static om/Ident
  (ident [this {:keys [id]}]
    [:post/by-id id])
  static om/IQuery
  (query [this]
    [:id :title :content :author]))

(defui PostList
  static om/IQuery
  (query [this]
    [{:posts/list (om/get-query Post)}]))

(def posts-init-state
  {:posts/list [{:id 0
                 :author "Laura Smith"
                 :title "A Post!"
                 :content "Lorem ipsum dolor sit amet, quem atomorum te quo"}
                {:id 1
                 :author "Jim Jacobs"
                 :title "Another Post!"
                 :content "Lorem ipsum dolor sit amet, quem atomorum te quo"}]})

(defn posts-read
  [{:keys [state query]} k _]
  (let [st @state]
    {:value (om/db->tree query st st)}))

(deftest test-app-normalizes-state-maps
  (let [app (c/application {:routes {:posts PostList}
                            :reconciler-opts {:state posts-init-state
                                              :parser (om/parser {:read posts-read})}})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (not= posts-init-state (dissoc @r ::c/route)))
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
           posts-init-state))))

(defmulti local-parser-read om/dispatch)
(defmethod local-parser-read :index
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {target true})))

(defmethod local-parser-read :about
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {target true})))

(defmethod local-parser-read :other
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {target (assoc ast :query [:changed/key :updated/ast])})))

(defmulti local-parser-mutate om/dispatch)
(defmethod local-parser-mutate 'fire/missiles!
  [{:keys [target]} _ _]
  {target true})

(defmulti remote-parser-read om/dispatch)
(defmethod remote-parser-read :index
  [_ _ _]
  {:value init-state})

(defmulti remote-parser-mutate om/dispatch)

(defmethod remote-parser-mutate 'fire/missiles!
  [_ _ _]
  {:action (fn [] {:missiles/fired? 3})})

(defui Other
  static om/IQuery
  (query [this]
    [:other/key :foo :bar]))

(deftest test-remote-integration
  (let [remote-parser (om/parser {:read   remote-parser-read
                                  :mutate remote-parser-mutate})
        app (c/application
              {:routes {:index (c/index-route Home)
                        :about About
                        :other Other}
               :reconciler-opts
               {:state  (atom {})
                :parser (om/parser {:read   local-parser-read
                                    :mutate local-parser-mutate})
                :remotes [:some-remote]
                :merge  c/compassus-merge
                :send   (fn [{:keys [some-remote]} cb]
                          (cb (remote-parser {} some-remote)))}})
        r (c/get-reconciler app)]
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [{:index (om/get-query Home)}]}))
    (c/mount! app nil)
    (is (= (dissoc @(c/get-reconciler app) ::c/route) init-state))
    (is (= (om/gather-sends (#'om/to-env r)
             '[(fire/missiles! {:how-many 42})] [:some-remote])
           {:some-remote '[(fire/missiles! {:how-many 42})]}))
    (om/transact! r '[(fire/missiles! {:how-many 3})])
    (is (= (-> @r (get 'fire/missiles!) :result)
           {:missiles/fired? 3}))
    (c/set-route! app :about)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [{:about (om/get-query About)}]}))
    (c/set-route! app :other)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [{:other [:changed/key :updated/ast]}]}))))

(deftest test-override-merge
  (let [remote-parser (om/parser {:read   remote-parser-read
                                  :mutate remote-parser-mutate})
        app (c/application
              {:routes {:index (c/index-route Home)
                        :about About}
               :reconciler-opts
               {:state  (atom {})
                :parser (om/parser {:read   local-parser-read
                                    :mutate local-parser-mutate})
                :merge  (fn [reconciler state res query]
                          (om/default-merge reconciler state {:foo res} query))
                :send   (fn [{:keys [remote]} cb]
                          (cb (remote-parser {} remote)))}})
        r (c/get-reconciler app)]
    (c/mount! app nil)
    (is (contains? @(c/get-reconciler app) :foo))))

(def idents-state
  {:item/by-id {0 {:id 0 :name "some item"}}})

(defmulti ident-parser-read om/dispatch)

(defmethod ident-parser-read :item/by-id
  [{:keys [state query-root]} k _]
  (if state
    ;; local parser
    (let [st @state
          val (get-in st query-root)]
      (if val
        {:value val}
        {:remote true}))
    ;; remote parser
    {:value idents-state}))

(deftest test-ident-routes
  (let [app (c/application
              {:routes {:index Home
                        [:item/by-id 0] (c/index-route About)}
               :reconciler-opts
               {:state  (atom idents-state)
                :parser (om/parser {:read ident-parser-read})}})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
           {:id 0
            :name "some item"})))
  (let [app-parser (om/parser {:read ident-parser-read})
        app (c/application
              {:routes {:index Home
                        [:item/by-id 0] (c/index-route About)}
               :reconciler-opts
               {:state  {}
                :parser app-parser
                :merge c/compassus-merge
                :send   (fn [{:keys [remote]} cb]
                          (cb (app-parser {} remote)))}})
        r (c/get-reconciler app)]
    (c/mount! app nil)
    (is (= (dissoc @(-> r :config :state) ::om/tables ::c/route)
           idents-state))))

(defmethod local-parser-read :foo
  [{:keys [route]} _ _]
  {:value {:test-route route}})

(deftest test-route-in-user-parser
  (let [app (c/application
              {:routes {:index Home
                        :foo   (c/index-route About)}
               :reconciler-opts
               {:state  (atom {})
                :parser (om/parser {:read   local-parser-read
                                    :mutate local-parser-mutate})}})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
           {:test-route :foo}))))

(defn remote-normalization-local-read
  [{:keys [state query]} k _]
  (let [st @state]
    {:remote true}))

(defn remote-normalization-read
  [_ _ _]
  {:value posts-init-state})

(deftest test-remote-normalization
  (let [remote-parser (om/parser {:read remote-normalization-read})
        app (c/application {:routes {:posts PostList}
                            :reconciler-opts {:state {}
                                              :merge c/compassus-merge
                                              :send (fn [{:keys [remote]} cb]
                                                      (cb (remote-parser {} remote) remote))
                                              :parser (om/parser {:read remote-normalization-local-read})}})
        r (c/get-reconciler app)]
    (c/mount! app nil)
    (is (contains? @r :posts/list))
    (is (contains? @r :post/by-id))
    (is (= (keys (:post/by-id @r)) [0 1]))
    (is (= (:posts/list @r) [[:post/by-id 0] [:post/by-id 1]]))))

(defmulti change-remote-mutation-local-mutate om/dispatch)

(defmethod change-remote-mutation-local-mutate 'do/stuff!
  [{:keys [ast]} _ _]
  {:remote (assoc-in ast [:params :when] :later)})

(defmethod change-remote-mutation-local-mutate 'other/stuff!
  [_ _ _]
  {:action (fn [])})

(deftest test-change-remote-mutation
  (let  [app (c/application {:routes {:posts PostList}
                             :reconciler-opts {:state {}
                                               :parser (om/parser {:mutate change-remote-mutation-local-mutate})}})
         r (c/get-reconciler app)]
    (is (= (om/gather-sends (#'om/to-env r)
             '[(do/stuff! {:when :now})] [:remote])
           {:remote '[(do/stuff! {:when :later})]}))
    (is (empty? (om/gather-sends (#'om/to-env r)
                  '[(other/stuff!)] [:remote])))))

(defmethod app-mutate 'some/action!
  [{:keys [state]} _ _]
  {:value {:some-value 42}
   :action #(swap! state update-in [:some/action]  (fnil inc 0))})

(defmethod app-mutate 'some/error!
  [{:keys [state]} _ _]
  {:action #(throw #?(:clj  (Exception. "'some/action error")
                      :cljs (js.Error. "'some/action error")))})

(deftest test-local-mutation
  (c/mount! *app* nil)
  (let [r (c/get-reconciler *app*)
        p (-> r :config :parser)
        mut-ret (p (#'om/to-env r) '[(some/action!)])
        mut-err (p (#'om/to-env r) '[(some/error!)])]
    (is (contains? (get mut-ret 'some/action!) :some-value))
    (is (contains? (get mut-ret 'some/action!) :result))
    (is (= (get-in mut-ret ['some/action! :result])
           {:home/title "Home page"
            :home/content "Lorem ipsum dolor sit amet."
            ::c/route :index
            :some/action 1}))
    (is (contains? (get mut-err 'some/error!) :om.next/error))
    (is (not (contains? (get mut-err 'some/error!) :result)))))

(defui Menu
  static om/IQuery
  (query [this]
    [:foo :bar]))

(defui Index
  static om/IQuery
  (query [this]
    [{:app/menu (om/get-query Menu)} :app/title]))

(defmulti transact-read om/dispatch)

(defmethod transact-read :index
  [{:keys [state]} _ _]
  {:value @state})

(defmethod transact-read :default
  [{:keys [state]} k _]
  {:value (get @state k)})

(defmethod transact-read :remote/key
  [_ _ _]
  {:remote true})

(deftest test-transact-re-reads
  (let [app (c/application
              {:routes {:index Index}
               :reconciler-opts
               {:state  (atom {:app/title "Some App"
                               :app/menu {:foo "foo"
                                          :bar "bar"}})
                :parser (om/parser {:read   transact-read
                                    :mutate app-mutate})}})
        r (c/get-reconciler app)
        p (-> r :config :parser)
        reread-ret1 (p (#'om/to-env r) '[(some/action!) :app/menu])
        reread-ret2 (p (#'om/to-env r) '[(some/action!) :app/menu :app/title])
        reread-ret3 (p (#'om/to-env r) `[(some/action!) {:app/menu ~(om/get-query Menu)}])]
    (is (contains? reread-ret1 'some/action!))
    (is (contains? reread-ret1 :app/menu))
    (is (= (:app/menu reread-ret1)
          {:foo "foo"
           :bar "bar"}))
    (is (contains? reread-ret2 'some/action!))
    (is (contains? reread-ret2 :app/menu))
    (is (contains? reread-ret2 :app/title))
    (is (= (:app/title reread-ret2) "Some App"))
    (is (= (om/gather-sends (#'om/to-env r)
             `[(some/action!) :remote/key] [:remote])
           {:remote [:remote/key]}))
    (is (= (om/gather-sends (#'om/to-env r)
             `[(some/action!) {:remote/key [:foo :bar]}] [:remote])
           {:remote [{:remote/key [:foo :bar]}]}))))
