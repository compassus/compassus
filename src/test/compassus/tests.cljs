(ns compassus.tests
  (:require [cljs.test :refer-macros [deftest testing is are use-fixtures]]
            [om.next :as om :refer-macros [defui]]
            [compassus.core :as c]
            [cljsjs.react]))

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
    (test-fn)))

(use-fixtures :each wrap-app)

(deftest test-create-app
  (is (instance? c/CompassusApplication *app*))
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
  (is (some? (c/app-root *app*)))
  (is (some? (.. (c/app-root *app*) -prototype)))
  (is (true? (.. (c/app-root *app*) -prototype -om$isComponent))))

(deftest test-make-root-class
  (let [root (c/make-root-class {:routes {:index Home
                                          :about About}})]
    (is (true? (.. root -prototype -om$isComponent)))
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
  (is (thrown-with-msg? js/Error #"Assert failed:" (c/current-route nil)))
  (is (thrown-with-msg? js/Error #"Assert failed:" (c/current-route 2)))
  (is (= (c/current-route *app*) '[:index _])))

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
    (is (thrown-with-msg? js/Error #"Assert failed:" (c/index-route nil)))
    (is (thrown-with-msg? js/Error #"Assert failed:" (c/index-route app1)))
    (is (thrown-with-msg? js/Error #"Assert failed:" (c/index-route (c/get-reconciler app1))))
    (is (= (c/current-route app1) '[:about _]))
    (is (= (c/current-route app2) '[:index _]))))

(deftest test-parsing
  (testing "generate parser"
    (let [user-parser (om/parser {:read app-read})
          om-parser (c/make-parser user-parser)
          st (merge init-state {::c/route '[:index _]})]
      (is (fn? om-parser))
      (is (= (om-parser {:state (atom st)} (om/get-query (c/app-root *app*)))
             {::c/route '[:index _]
              ::c/route-data init-state}))))
  (testing "parser integration in compassus app"
    (let [user-parser (-> *app* :config :parser)
          om-parser   (-> *app* c/get-reconciler :config :parser)]
      (is (some? user-parser))
      (is (some? om-parser))
      (is (not= user-parser om-parser)))))

(deftest test-update-route!
  (is (thrown-with-msg? js/Error #"Assert failed:" (c/update-route! nil :about)))
  (is (thrown-with-msg? js/Error #"Assert failed:" (c/update-route! 3 :about)))
  (is (thrown-with-msg? js/Error #"Assert failed:" (c/update-route! "foo" :about)))
  (is (thrown-with-msg? js/Error #"Assert failed:" (c/update-route! "foo" :about {:queue? true})))
  (let [prev-route (c/current-route *app*)]
    (c/update-route! *app* :about {:queue? false})
    (is (not= prev-route (c/current-route *app*)))
    (is (= (c/current-route *app*) '[:about _]))))

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
    (is (= (-> (p {:state (-> r :config :state)} (om/get-query (c/app-root app)))
               (get ::c/route-data))
           posts-init-state))))

(defmulti remote-test-read om/dispatch)

(defmethod remote-test-read :index
  [{:keys [state query target ast] :as env} _ _]
  (let [st @state]
    (if (some st query)
      {:value st}
      {:remote true})))

(deftest test-remote-integration
  (let [app (c/application
              {:routes {:index (c/index-route Home)
                        :about About}
               :reconciler-opts
               {:state  (atom {})
                :parser (om/parser {:read remote-test-read})
                :send   (fn [_ cb]
                          (cb init-state))}})
        r (c/get-reconciler app)]
    (is (= (om/gather-sends (om/to-env r)
             (om/get-query (c/app-root app)) [:remote])
           {:remote [{:index (om/get-query Home)}]}))
    (c/mount! app nil)
    (is (= (dissoc @(om/app-state (c/get-reconciler app)) ::c/route) init-state))))

;; TODOs:
;; - test remote calls to both read & mutate
;; - remote mutations
;; - history
;; - secretary example
;; - bidi example
;; - (silk example)
