(ns compassus.core
  (:require [om.next :as om :refer-macros [ui]]))


(defn get-reconciler [app]
  (-> app :config :reconciler))

(defn app-root [app]
  (-> app :config :root))

(defn mount! [app target]
  (let [reconciler (get-reconciler app)
        root (app-root app)]
    (om/add-root! reconciler root target)))

(defn- make-root-component
  [{:keys [routes wrapper]}]
  (let [route->query   (zipmap (keys routes)
                               (map om/get-query (vals routes)))
        route->factory (zipmap (keys routes)
                               (map om/factory (vals routes)))]
    (ui
      static om/IQuery
      (query [this]
        [::route {::route-data route->query}])
      Object
      (render [this]
        (let [props (om/props this)
              [route _] (::route props)
              route-data (::route-data props)
              factory (get route->factory route)]
          (if wrapper
            (wrapper {:owner   this
                      :factory factory
                      :props   route-data})
            (factory route-data)))))))

(defrecord CompassusApplication [config])

;; TODO:
;; - figure out how to specify the initial route for the app
;; - normalize according to first route?
;; - should routes be idents or just keywords?
(defn application [{:keys [routes wrapper reconciler-opts] :as opts}]
  (let [{:keys [state]} reconciler-opts
        state (cond-> state
                (satisfies? IAtom state) deref)
        route-info [(ffirst routes) '_]
        state (merge state {::route route-info})
        reconciler-opts (merge reconciler-opts {:state (atom state)})
        reconciler (om/reconciler reconciler-opts)
        app-root (make-root-component opts)]
    (CompassusApplication.
      {:route->component routes
       :reconciler       reconciler
       :root             app-root})))
