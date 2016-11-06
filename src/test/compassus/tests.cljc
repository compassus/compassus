(ns compassus.tests
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require #?@(:cljs [[cljsjs.react]
                       [goog.object :as gobj]])
            [clojure.core.async :refer [<! close! chan take! #?@(:clj [go <!!])]]
            [clojure.test :refer [deftest testing is are use-fixtures #?(:cljs async)]]
            [om.next :as om :refer [defui ui]]
            [om.next.impl.parser :as parser]
            [om.next.protocols :as p]
            [compassus.core :as c]))

(def test-utils #?(:cljs js/React.addons.TestUtils))

;; http://stackoverflow.com/a/30781278/3417023
(defn test-async
  "Asynchronous test awaiting ch to produce a value or close."
  [ch]
  #?(:clj
     (<!! ch)
     :cljs
     (async done
       (take! ch (fn [_] (done))))))

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
   :home/content "Lorem ipsum dolor sit amet."
   :about/title "About page"
   :about/content "Lorem ipsum dolor sit amet."})

(defmulti app-read om/dispatch)

(defmethod app-read :default
  [{:keys [state query]} k _]
  {:value (select-keys @state query)})

(defmulti app-mutate om/dispatch)

(defn set-app! []
  (set! *app*
    (c/application
      {:routes {:index Home
                :about About}
       :index-route :index
       :reconciler (om/reconciler
                     {:state (atom init-state)
                      :parser (c/parser {:read app-read
                                         :mutate app-mutate})})})))

(defn unset-app! []
  (set! *app* nil))

#?(:clj  (use-fixtures :each
           (fn [test-fn]
             (binding [*app* nil]
               (set-app!)
               (test-fn)
               (unset-app!))))
   :cljs (use-fixtures :each
           {:before set-app!
            :after unset-app!}))

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
                         :about About}
                :index-route :about
                :reconciler (om/reconciler
                              {:state init-state
                               :parser (c/parser {:read app-read})})})
        app2 (c/application
               {:routes {:index Home
                         :about About}
                :index-route :index
                :reconciler (om/reconciler
                              {:state init-state
                               :parser (c/parser {:read app-read})})})]
    (is (= (c/current-route app1) :about))
    (is (= (c/current-route app2) :index))))

(deftest test-parsing
  (testing "generate parser"
    (let [parser (c/parser {:read app-read})
          st (merge init-state {::c/route :index})]
      (is (fn? parser))
      (is (= (parser {:state (atom st)} (om/get-query (c/root-class *app*)))
             {::c/route :index
              ::c/route-data (select-keys init-state (om/get-query Home))})))))

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

(defui User
  static om/Ident
  (ident [this {:keys [id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:id :name]))

(defui UserList
  static om/IQuery
  (query [this]
    [{:users/list (om/get-query User)}]))

(def users-init-state
  {:users/list [{:id 0 :name "Alice"}
                {:id 1 :name "Bob"}]})

(defn posts-read
  [{:keys [state query]} k _]
  (let [st @state]
    {:value (om/db->tree query st st)}))

(deftest test-app-normalizes-state-maps
  (let [app (c/application {:routes {:posts PostList}
                            :reconciler (om/reconciler
                                          {:state posts-init-state
                                           :parser (c/parser {:read posts-read})})})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (not= posts-init-state (dissoc @r ::c/route ::om/tables)))
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
           posts-init-state)))
  (let [app (c/application {:routes {:posts PostList
                                     :users UserList}
                            :reconciler (om/reconciler
                                          {:state (merge posts-init-state
                                                    users-init-state)
                                           :parser (c/parser {:read posts-read})})})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (not= (merge posts-init-state
                users-init-state)
              (dissoc @r ::c/route ::om/tables)))
    (is (= (:users/list @r)
           [[:user/by-id 0] [:user/by-id 1]]))
    (is (= (get-in @r [:user/by-id 1])
           {:id 1 :name "Bob"}))))

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
  {:value (select-keys init-state (om/get-query Home))})

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
        c (chan)
        app (c/application
              {:routes {:index Home
                        :about About
                        :other Other}
               :index-route :index
               :reconciler (om/reconciler
                             {:state  (atom {})
                              :parser (c/parser {:read   local-parser-read
                                                  :mutate local-parser-mutate})
                              :remotes [:some-remote]
                              :merge  c/compassus-merge
                              :send   (fn [{:keys [some-remote]} cb]
                                        (cb (remote-parser {} some-remote))
                                        (close! c))})})
        r (c/get-reconciler app)]
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [[{:index (om/get-query Home)}]]}))
    (c/mount! app nil)
    (is (= (dissoc @(c/get-reconciler app) ::c/route) (select-keys init-state (om/get-query Home))))
    (is (= (om/gather-sends (#'om/to-env r)
             '[(fire/missiles! {:how-many 42})] [:some-remote])
           {:some-remote ['[(fire/missiles! {:how-many 42})]]}))
    (c/set-route! app :about)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [[{:about (om/get-query About)}]]}))
    (c/set-route! app :other)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [[{:other [:changed/key :updated/ast]}]]}))
    (om/transact! r '[(fire/missiles! {:how-many 3})])
    (test-async
      (go
        (let [_ (<! c)]
          (is (= (-> @r (get 'fire/missiles!) :result)
                 {:missiles/fired? 3})))))))

(deftest test-override-merge
  (let [remote-parser (om/parser {:read   remote-parser-read
                                  :mutate remote-parser-mutate})
        app (c/application
              {:routes {:index Home
                        :about About}
               :index-route :index
               :reconciler
               (om/reconciler
                 {:state  (atom {})
                  :parser (c/parser {:read   local-parser-read
                                      :mutate local-parser-mutate})
                  :merge  (fn [reconciler state res query]
                            (om/default-merge reconciler state {:foo res} query))
                  :send   (fn [{:keys [remote]} cb]
                            (cb (remote-parser {} remote)))})})
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
                        [:item/by-id 0] About}
               :index-route [:item/by-id 0]
               :reconciler (om/reconciler
                             {:state  (atom idents-state)
                              :parser (c/parser {:read ident-parser-read})})})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
           {:id 0
            :name "some item"})))
  (let [remote-parser (om/parser {:read ident-parser-read})
        app (c/application
              {:routes {:index Home
                        [:item/by-id 0] About}
               :index-route [:item/by-id 0]
               :reconciler (om/reconciler
                             {:state  {}
                              :parser (c/parser {:read ident-parser-read})
                              :merge c/compassus-merge
                              :send   (fn [{:keys [remote]} cb]
                                        (cb (remote-parser {} remote)))})})
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
                        :foo   About}
               :index-route :foo
               :reconciler (om/reconciler
                             {:state  (atom {})
                              :parser (c/parser {:read   local-parser-read
                                                 :mutate local-parser-mutate})})})
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
                            :reconciler
                            (om/reconciler
                              {:state {}
                               :merge c/compassus-merge
                               :send (fn [{:keys [remote]} cb]
                                       (cb (remote-parser {} remote) remote))
                               :parser (c/parser {:read remote-normalization-local-read})})})
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
                             :reconciler (om/reconciler
                                           {:state {}
                                            :parser (c/parser {:mutate change-remote-mutation-local-mutate})})})
         r (c/get-reconciler app)]
    (is (= (om/gather-sends (#'om/to-env r)
             '[(do/stuff! {:when :now})] [:remote])
           {:remote ['[(do/stuff! {:when :later})]]}))
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
           (merge init-state {::c/route :index
                              :some/action 1})))
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
               :reconciler (om/reconciler
                             {:state  (atom {:app/title "Some App"
                                             :app/menu {:foo "foo"
                                                        :bar "bar"}})
                              :parser (c/parser {:read   transact-read
                                                 :mutate app-mutate})})})
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
             '[(some/action!) :remote/key] [:remote])
           {:remote [[:remote/key]]}))
    (is (= (om/gather-sends (#'om/to-env r)
             '[(some/action!) {:remote/key [:foo :bar]}] [:remote])
           {:remote [[{:remote/key [:foo :bar]}]]}))))

(defui Person
  static om/Ident
  (ident [this {:keys [db/id]}]
    [:person/by-id id])
  static om/IQuery
  (query [this]
    [:db/id :person/name]))

(defui People
  static om/IQuery
  (query [this]
    [{:people (om/get-query Person)}]))

(deftest test-remote-migrate
  (testing "default-migrate"
    (let [tmpid (om/tempid)
          app (c/application
                {:routes {:index People}
                 :reconciler (om/reconciler
                               {:state (atom {:people [[:person/by-id tmpid]]
                                              :person/by-id {tmpid {:db/id tmpid
                                                                    :person/name "Joe"}}})
                                :normalize true
                                :id-key :db/id
                                :parser (c/parser {:read transact-read})})})
          r (c/get-reconciler app)]
      (c/mount! app nil)
      (is (om/tempid? (-> @r :person/by-id ffirst)))
      (om/merge! r
        {'some/action! {:tempids {[:person/by-id tmpid] [:person/by-id 42]}}}
        (om/get-query People))
      (is (not (om/tempid? (-> @r :person/by-id ffirst))))
      (is (= (-> @r :person/by-id ffirst) 42))
      (is (contains? @r ::c/route))))
  (testing "custom migrate"
    (let [tmpid (om/tempid)
          app (c/application
                {:routes {:index People}
                 :reconciler
                 (om/reconciler
                   {:state (atom {:people [[:person/by-id tmpid]]
                                  :person/by-id {tmpid {:db/id tmpid
                                                        :person/name "Joe"}}})
                    :migrate (fn [app-state-pure query tempids id-key]
                               (assoc (om/default-migrate app-state-pure query tempids id-key)
                                 :random/key 42))
                    :normalize true
                    :id-key :db/id
                    :parser (c/parser {:read transact-read})})})
          r (c/get-reconciler app)]
      (c/mount! app nil)
      (is (om/tempid? (-> @r :person/by-id ffirst)))
      (om/merge! r
        {'some/action! {:tempids {[:person/by-id tmpid] [:person/by-id 42]}}}
        (om/get-query People))
      (is (not (om/tempid? (-> @r :person/by-id ffirst))))
      (is (= (-> @r :person/by-id ffirst) 42))
      (is (contains? @r ::c/route))
      (is (contains? @r :random/key)))))

(defui StaticRoute)

(deftest test-routes-without-query
  (testing "static route is not the index route"
    (let [app (c/application {:routes {:index Home
                                       :static StaticRoute}
                              :index-route :index
                              :reconciler (om/reconciler
                                            {:state (atom init-state)
                                             :parser (c/parser {:read app-read})})})
          r (c/get-reconciler app)
          p (-> r :config :parser)]
      (c/mount! app nil)
      (is (= (c/current-route app) :index))
      (is (= (om/get-query (c/root-class app))
             [::c/route {::c/route-data {:index [:home/title :home/content]}}]))
      (c/set-route! app :static)
      (is (not (contains?
                 (p {:state (-> r :config :state)}
                    (om/get-query (c/root-class app)))
                 ::c/route-data)))
      (is (empty? (om/gather-sends (#'om/to-env r)
                    (om/get-query (c/root-class app)) [:remote])))))
  (testing "static route as the index route"
    (let [app (c/application {:routes {:static StaticRoute
                                       :about About}
                              :index-route :static
                              :reconciler (om/reconciler
                                            {:state (atom init-state)
                                             :parser (c/parser {:read app-read})})})
          r (c/get-reconciler app)
          p (-> r :config :parser)]
      (c/mount! app nil)
      (is (= (c/current-route app) :static))
      (is (= (om/get-query (c/root-class app))
             [::c/route {::c/route-data {:about [:about/title :about/content]}}]))
      (is (not (contains?
                 (p {:state (-> r :config :state)}
                    (om/get-query (c/root-class app)))
                 ::c/route-data)))
      (is (empty? (om/gather-sends (#'om/to-env r)
                    (om/get-query (c/root-class app)) [:remote])))
      (c/set-route! app :about)
      (is (= (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
             {::c/route :about
              ::c/route-data (select-keys init-state (om/get-query About))}))
      (is (empty? (om/gather-sends (#'om/to-env r)
                    (om/get-query (c/root-class app)) [:remote]))))))

(defmethod app-mutate 'set-params!
  [{:keys [state]} _ params]
  {:action #(swap! state merge params)})

(deftest test-compassus-8
  (let [prev-route (c/current-route *app*)
        tx (c/set-route! *app* :about {:params {:route-params {:foo 42}}})]
    (is (= (-> tx (get-in ['compassus.core/set-route! :keys]))
           [::c/route ::c/route-data :route-params]))
    (is (not= prev-route (c/current-route *app*)))
    (is (= (c/current-route *app*) :about))
    (is (= (-> @(c/get-reconciler *app*) :route-params) {:foo 42}))
    (is (not (contains? @(c/get-reconciler *app*) :route))))
  (let [prev-route (c/current-route *app*)]
    (c/set-route! *app* :about {:tx '[(set-params! {:some-params {:foo 42}})]})
    (is (= (-> @(c/get-reconciler *app*) :some-params) {:foo 42})))
  (let [prev-route (c/current-route *app*)]
    (c/set-route! *app* :about {:params {:other-params {:foo 42}}
                                :tx     '(set-params! {:more-stuff {:bar 42}})})
    (is (= (-> @(c/get-reconciler *app*) :other-params) {:foo 42}))
    (is (= (-> @(c/get-reconciler *app*) :more-stuff) {:bar 42}))))

(defmulti mixins-read om/dispatch)

(defmethod mixins-read :index
  [{:keys [state query]} _ _]
  {:value (select-keys @state query)})

(defmethod mixins-read :default
  [{:keys [state]} k _]
  {:value (get @state k)})

(deftest test-mixins
  (testing "wrapper mixin"
    (let [update-atom (atom {})
          wrapper (fn [{:keys [owner factory render props]}]
                    (swap! update-atom assoc :wrapped-render true
                      :props props)
                    (factory props))
          #?@(:cljs [shallow-renderer (.createRenderer test-utils)])
          app (c/application {:routes {:index Home}
                              :index-route :index
                              :mixins [(c/wrap-render wrapper)]
                              :reconciler
                              (om/reconciler
                                {:state (atom init-state)
                                 :parser (c/parser {:read app-read})
                                 :root-unmount (fn [_])
                                 #?@(:cljs [:root-render (fn [c target]
                                                           (.render shallow-renderer c))])})})
          c (c/mount! app :target)]
      #?(:clj (p/-render c))
      (is (= (:props @update-atom) (select-keys init-state (om/get-query Home))))
      (is (true? (-> @update-atom :wrapped-render)))
      (om/remove-root! (c/get-reconciler app) :target)))
  (testing "wrapper's parent is the root class"
    (let [parent-atom (atom {})
          wrapper (ui
                    Object
                    (render [this]
                      (let [{:keys [owner factory render props]} (om/props this)]
                        (factory props))))]
      (with-redefs [om/factory (fn [class]
                                 (fn self
                                   ([] (self nil))
                                   ([props & children]
                                    (swap! parent-atom assoc class @#'om/*parent*)
                                    (let [t (if-not (nil? @#'om/*reconciler*)
                                              (p/basis-t @#'om/*reconciler*)
                                              0)]
                                      #?(:clj  (class nil nil
                                                 {:omcljs$value      props
                                                  :omcljs$mounted?   (atom false)
                                                  :omcljs$reconciler @#'om/*reconciler*
                                                  :omcljs$parent     @#'om/*parent*
                                                  :omcljs$depth      @#'om/*depth*}
                                                 nil)
                                         :cljs (js/React.createElement class
                                                 #js {:omcljs$value      (om/om-props props t)
                                                      :omcljs$reconciler om/*reconciler*
                                                      :omcljs$parent     om/*parent*
                                                      :omcljs$depth      om/*depth*}))))))]
        (let [#?@(:cljs [shallow-renderer (.createRenderer test-utils)])
              app (c/application {:routes {:index Home}
                                  :mixins [(c/wrap-render wrapper)]
                                  :reconciler
                                  (om/reconciler
                                    {:state (atom init-state)
                                     :parser (c/parser {:read app-read})
                                     :root-unmount (fn [_])
                                     #?@(:cljs [:root-render (fn [c target]
                                                               (.render shallow-renderer c))])})})
              root (c/root-class app)
              c (c/mount! app :target)]
          #?(:clj (p/-render c))
          (is (= (-> @parent-atom (get wrapper) om/react-type) root))
          (om/remove-root! (c/get-reconciler app) :target)))))
  (testing "lifecycle mixins"
    (let [update-atom (atom {})
          #?@(:cljs [shallow-renderer (.createRenderer test-utils)])
          app (c/application {:routes {:index Home}
                              :mixins [(c/will-mount
                                         (fn [this]
                                           (swap! update-atom update-in
                                             [:will-mount] (fnil inc 0))))
                                       (c/did-mount
                                         (fn [this]
                                           (swap! update-atom update-in
                                             [:did-mount] (fnil inc 0))))
                                       (c/will-unmount
                                         (fn [this]
                                           (swap! update-atom update-in
                                             [:will-unmount] (fnil inc 0))))]
                              :reconciler
                              (om/reconciler
                                {:state (atom {})
                                 :parser (c/parser {:read (fn [_ _ _]
                                                             {:value {}})})
                                 :root-unmount (fn [_]
                                                 #?(:cljs (.unmount shallow-renderer)))
                                 #?@(:cljs [:root-render (fn [c target]
                                                           (.render shallow-renderer c))])})})
          c (c/mount! app :target)]
      #?(:clj (p/-render c))
      (is (= (-> @update-atom :will-mount) 1))
      ;; CLJS-only: server-side doesn't have these lifecycle methods
      ;; https://github.com/facebook/react/commit/941997
      #?(:cljs (.componentDidMount (.getMountedInstance shallow-renderer)))
      #?(:cljs (is (= (-> @update-atom :did-mount) 1)))
      (om/remove-root! (c/get-reconciler app) :target)
      #?(:cljs (is (= (-> @update-atom :will-unmount) 1))))))

(defmulti remote-mixins-read om/dispatch)

(defmethod remote-mixins-read :index
  [{:keys [state query]} _ _]
  {:value (select-keys @state query)})

(defmethod remote-mixins-read :foo
  [{:keys [query target]} _ _]
  {:value {:bar 42}
   target true})

(def update-atom (atom {}))

(defui RenderWrapper
  static om/IQueryParams
  (params [this]
    {:foo [:bar :baz]})
  static om/IQuery
  (query [this]
    '[{:foo ?foo}])
  Object
  (render [this]
    (let [{:keys [props owner factory]} (om/props this)]
      (swap! update-atom :assoc :props (om/props this))
      (factory props))))

(deftest test-iquery-wrapper
  (testing "remote behavior"
    (let [app (c/application
                {:routes {:index Home}
                 :mixins [(c/wrap-render RenderWrapper)]
                 :reconciler
                 (om/reconciler
                   {:state  (atom {})
                    :parser (c/parser {:read remote-mixins-read})
                    :remotes [:some-remote]})})
          r (c/get-reconciler app)
          root (c/root-class app)]
      (is (= (om/get-query root)
            [::c/route
             {::c/route-data {:index [:home/title :home/content]}}
             {::c/mixin-data [{:foo [:bar :baz]}]}]))
      (is (= (om/gather-sends (#'om/to-env r)
               (om/get-query root) [:some-remote])
             {:some-remote [[{:foo [:bar :baz]}]]}))))
  (testing "wrapper updates"
    (reset! update-atom {})
    (let [#?@(:cljs [shallow-renderer (.createRenderer test-utils)])
          app (c/application
                {:routes {:index Home}
                 :mixins [(c/wrap-render RenderWrapper)]
                 :reconciler
                 (om/reconciler
                   {:state  (atom init-state)
                    :parser (c/parser {:read remote-mixins-read})
                    :root-unmount (fn [_])
                    #?@(:cljs [:root-render (fn [c target]
                                              (.render shallow-renderer c))])})})
          r (c/get-reconciler app)
          root (c/root-class app)
          c (c/mount! app :target)
          #?@(:clj [wrapper (p/-render c)])
          wrapper-props #?(:clj  (:omcljs$value (p/-props wrapper))
                           :cljs (-> (.getRenderOutput shallow-renderer)
                                   (gobj/get "props") om/get-props om/unwrap))]
      (is (every? (partial contains? (om/get-computed wrapper-props)) [:owner :factory :props]))
      (is (= (-> wrapper-props :foo) {:bar 42})))))

(defui MixinPostList
  static om/IQuery
  (query [this]
    [:foo]))

(defn mixin-posts-read
  [{:keys [state query]} k _]
  (let [st @state]
    {:value (om/db->tree query (get st k) st)}))

(deftest test-app-normalizes-mixin-queries
  (let [app (c/application {:routes {:posts MixinPostList}
                            :mixins [(c/wrap-render PostList)]
                            :reconciler
                            (om/reconciler
                              {:state posts-init-state
                               :parser (c/parser {:read mixin-posts-read})})})
        r (c/get-reconciler app)
        p (-> r :config :parser)]
    (is (not= posts-init-state (dissoc @r ::c/route ::om/tables)))
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/mixin-data))
           posts-init-state))))

(deftest test-compassus-15
  (with-redefs [om/transform-reads (fn [_ _] [{:foo [:bar]}])
                om/transact! (fn [_ tx] tx)]
    (is (some #{{:foo [:bar]}} (c/set-route! *app* :about)))
    (is (some #{{:foo [:bar]}} (c/set-route! *app* :about {:params {:route-params {:foo 42}}})))))

(defmulti flat-read om/dispatch)

(defmethod flat-read :home/title
  [_ _ _]
  {:value :title})

(defmethod flat-read :home/content
  [_ _ _]
  {:value :content})

(deftest test-compassus-12
  (testing "flattening parser"
    (let [app (c/application
                {:routes {:index Home
                          :about About}
                 :index-route :index
                 :reconciler (om/reconciler
                               {:state (atom init-state)
                                :parser (c/parser {:read flat-read
                                                   :route-dispatch false})})})
          r (c/get-reconciler app)
          p (-> r :config :parser)]
      (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
            {:home/title :title
             :home/content :content}))))
  (testing "backwards compatibility"
    (let [app (c/application
                {:routes {:index Home
                          :about About}
                 :index-route :index
                 :reconciler (om/reconciler
                               {:state (atom init-state)
                                :parser (c/parser {:read (fn [_ _ _] {:value :foo})})})})
          r (c/get-reconciler app)
          p (-> r :config :parser)]
      (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
            :foo)))))

(defmulti local-flat-read om/dispatch)
(defmethod local-flat-read :default
  [{:keys [state target] :as env} k _]
  (let [st @state]
    (if (contains? st k)
      {:value (get st k)}
      {target true})))

(defmethod local-flat-read :other/key
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {target (parser/expr->ast :changed/key)})))

(defmulti remote-flat-read om/dispatch)
(defmethod remote-flat-read :home/title
  [_ _ _]
  {:value :title})

(defmethod remote-flat-read :home/content
  [_ _ _]
  {:value :content})

(deftest test-flat-remote-integration
  (let [remote-parser (om/parser {:read remote-flat-read})
        c (chan)
        app (c/application
              {:routes {:index Home
                        :about About
                        :other Other}
               :index-route :index
               :reconciler (om/reconciler
                             {:state  (atom {})
                              :parser (c/parser {:read   local-flat-read
                                                 :route-dispatch false})
                              :remotes [:some-remote]
                              :send (fn [{:keys [some-remote]} cb]
                                      (cb (remote-parser {} some-remote))
                                      (close! c))})})
        r (c/get-reconciler app)]
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [(om/get-query Home)]}))
    (c/mount! app nil)
    (is (= (dissoc @(c/get-reconciler app) ::c/route) {:home/title :title
                                                       :home/content :content}))
    (c/set-route! app :about)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [(om/get-query About)]}))
    (c/set-route! app :other)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:some-remote])
           {:some-remote [[:changed/key :foo :bar]]}))))

(defn compassus-16-read
  [{:keys [state query target ast] :as env} k _]
  (let [st @state]
    (if (contains? st k)
      {:value st}
      {target true})))

(deftest test-compassus-16
  (let [app (c/application
              {:routes {:index Home
                        :about About}
               :index-route :index
               :reconciler (om/reconciler
                             {:state  (atom {})})})
        r (c/get-reconciler app)
        merge-fn (-> r :config :merge)]
    (is (= (:keys (merge-fn r @r {:index {:foo 42}} nil))
           [:index ::c/route-data]))
    (is (= (:keys (merge-fn r @r {:about {:foo 42}} nil))
           [:about ::c/route-data])))
  (let [app (c/application
              {:routes {:index Home
                        :about About}
               :index-route :index
               :reconciler (om/reconciler
                             {:state (atom {})
                              :parser (c/parser {:read compassus-16-read})
                              :merge c/compassus-merge})})
        r (c/get-reconciler app)
        merge-fn (-> r :config :merge)]
    (c/mount! app nil)
    (is (= (:keys (merge-fn r @r {:index {:foo 42}} nil))
           [:foo ::c/route-data]))
    (c/set-route! app :about)
    (is (= (:keys (merge-fn r @r {:about {:foo 42}} nil))
           [:foo ::c/route-data]))))

(deftest test-compassus-19
  (testing "wrapper mixin"
    (let [wrapper (fn [{:keys [owner factory render props]}]
                    (factory {:changed/props 42}))
          #?@(:cljs [shallow-renderer (.createRenderer test-utils)])
          app (c/application {:routes {:index Home}
                              :index-route :index
                              :mixins [(c/wrap-render wrapper)]
                              :reconciler
                              (om/reconciler
                                {:state (atom init-state)
                                 :parser (c/parser {:read app-read})
                                 :root-unmount (fn [_])
                                 #?@(:cljs [:root-render (fn [c target]
                                                           (.render shallow-renderer c))])})})
          r (c/get-reconciler app)
          c (c/mount! app :target)
          component #?(:clj  (p/-render c)
                       :cljs (.getRenderOutput shallow-renderer))]
      (is (= #?(:clj  (om/props component)
                :cljs (om/unwrap (-> component .-props om/get-props)))
             {:changed/props 42}))
      (is (= #?(:clj  (om/depth component)
                :cljs (-> component .-props (gobj/get "omcljs$depth")))
             1))
      (om/remove-root! (c/get-reconciler app) :target))))
