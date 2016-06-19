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

(defn wrap-app [test-fn]
  (binding [*app* (c/application
                    {:routes {:index (c/index-route Home)
                              :about About}
                     :reconciler-opts
                     {:state (atom init-state)
                      :parser (om/parser {:read app-read})}})]
    ;; make setTimeout execute synchronously
    #?(:cljs (set! js/setTimeout (fn [f t] (f))))
    (test-fn)))

(use-fixtures :each wrap-app)

(deftest test-create-app
  (is (instance? compassus.core.CompassusApplication *app*))
  (is (om/reconciler? (c/get-reconciler *app*)))
  (is (= (-> (c/get-reconciler *app*)
           om/app-state
           deref
           (dissoc ::c/route))
        init-state))
  (is (contains? (-> (c/get-reconciler *app*)
                   om/app-state
                   deref)
        ::c/route))
  (is (some? (c/root-class *app*)))
  (is (fn? (c/root-class *app*)))
  (is (some? #?(:clj  (-> (c/root-class *app*) meta :component-name)
                :cljs (.. (c/root-class *app*) -prototype))))
  #?(:cljs (is (true? (.. (c/root-class *app*) -prototype -om$isComponent)))))

(deftest test-make-root-class
  (let [root (#'c/make-root-class {:routes {:index Home
                                          :about About}})]
    (is (fn? root))
    #?(:cljs (is (true? (.. root -prototype -om$isComponent))))
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
    (is (not= posts-init-state (dissoc @(om/app-state r) ::c/route)))
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/root-class app)))
               (get ::c/route-data))
           posts-init-state))))

(defmulti local-parser-read om/dispatch)
(defmethod local-parser-read :index
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {:remote true})))

(defmethod local-parser-read :about
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {:remote true})))

(defmethod local-parser-read :other
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {:remote (assoc ast :query [:changed/key :updated/ast])})))

(defmulti local-parser-mutate om/dispatch)
(defmethod local-parser-mutate 'fire/missiles!
  [{:keys [target]} _ _]
  {:remote true})

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
                :merge  c/compassus-merge
                :send   (fn [{:keys [remote]} cb]
                          (cb (remote-parser {} remote)))}})
        r (c/get-reconciler app)]
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:remote])
           {:remote [{:index (om/get-query Home)}]}))
    (c/mount! app nil)
    (is (= (dissoc @(om/app-state (c/get-reconciler app)) ::c/route) init-state))
    (is (= (om/gather-sends (#'om/to-env r)
             '[(fire/missiles! {:how-many 42})] [:remote])
           {:remote '[(fire/missiles! {:how-many 42})]}))
    (om/transact! r '[(fire/missiles! {:how-many 3})])
    (is (= (-> @(om/app-state r) (get 'fire/missiles!) :result)
           {:missiles/fired? 3}))
    (c/set-route! app :about)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:remote])
           {:remote [{:about (om/get-query About)}]}))
    (c/set-route! app :other)
    (is (= (om/gather-sends (#'om/to-env r)
             (om/get-query (c/root-class app)) [:remote])
           {:remote [{:other [:changed/key :updated/ast]}]}))))

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
    (is (contains? @(om/app-state (c/get-reconciler app)) :foo))))

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
