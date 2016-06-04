(ns compassus.tests
  (:require [cljs.test :refer-macros [deftest testing is are]]
            [om.next :as om :refer-macros [defui]]
            [compassus.core :as c]
            [cljsjs.react]))

(defui Home
  static om/IQuery
  (query [this]
    [:home/title :home/content]))

(defui About
  static om/IQuery
  (query [this]
    [:about/title :about/content]))

(deftest test-create-app
  (let [init-state {:app/home
                    {:home/title "Home page"
                     :home/content "Lorem ipsum dolor sit amet."}}
        app (c/application {:routes {:index Home
                                     :about About}
                            :reconciler-opts {:state init-state}})]
    (is (instance? c/CompassusApplication app))
    (is (om/reconciler? (c/get-reconciler app)))
    (is (= (-> (c/get-reconciler app)
               om/app-state
               deref
               (dissoc ::c/route))
           init-state))
    (is (contains? (-> (c/get-reconciler app)
                       om/app-state
                       deref)
                   ::c/route))
    (is (some? (c/app-root app)))
    (is (some? (.. (c/app-root app) -prototype)))
    (is (true? (.. (c/app-root app) -prototype -om$isComponent)))))

(deftest test-make-root-component
  (let [routes {:index Home
                :about About}
        root (c/make-root-component {:routes routes})]
    (is (true? (.. root -prototype -om$isComponent)))
    (is (= (om/get-query root)
           [::c/route
            {::c/route-data {:index [:home/title :home/content]
                             :about [:about/title :about/content]}}]))))

(deftest test-normalize-against-index)

;; TODOs:
;; - parser
;; - update-route!
