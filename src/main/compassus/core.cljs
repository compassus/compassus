(ns compassus.core
  (:require [goog.object :as gobj]
            [om.next :as om :refer-macros [ui]]))

(defn get-reconciler [app]
  (-> app :config :reconciler))

;; TODO: this is the root-class, what about when it turns into a component?
(defn app-root [app]
  "Return the application's root class."
  (-> app :config :root-class))

(defn mount! [app target]
  (let [reconciler (get-reconciler app)
        root (app-root app)]
    (om/add-root! reconciler root target)))

(defn- make-root-class
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

(defprotocol ICompassus
  (-mount! [this target]))

(defrecord CompassusApplication [config state])

(defn application? [x]
  (instance? CompassusApplication x))

(defn index-route [class]
  {:pre [(fn? class)]}
  (vary-meta class assoc ::index-route true))

(defn current-route [x]
  {:pre [(or (om/reconciler? x) (application? x) (om/component? x))]}
  (let [reconciler (cond-> x
                     (application? x) get-reconciler
                     (om/component? x) om/get-reconciler)
        st @(om/app-state reconciler)]
    (get st ::route)))

;; TODO:
;; - `set-route!` or `update-route!`?
;; - explore if calling `transact!` on the reconciler doesn't already perform follow-on reads
(defn update-route!
  ([x next-route]
   (update-route! x next-route {:queue? true}))
  ([x next-route {:keys [queue?]}]
   {:pre [(or (om/reconciler? x) (application? x) (om/component? x))
          (or (vector? next-route) (keyword? next-route))]}
   (let [reconciler (cond-> x
                      (application? x) get-reconciler
                      (om/component? x) om/get-reconciler)
         next-route (cond-> next-route
                      (keyword? next-route) (vector '_))]
     (om/transact! reconciler (cond-> `[(update-route! {:route ~next-route})]
                                queue? (conj ::route-data))))))

(defn- generate-parser-read [user-parser]
  (fn read [{:keys [state query target] :as env} k params]
    (let [st @state
          top-level-prop? (nil? query)
          [route _] (get st ::route)
          query (cond-> query
                  ;; not full-query
                  (map? query) (select-keys [route]))]
      {:value
       (if top-level-prop?
         (get st k)
         (let [ret (user-parser (merge env {:parser user-parser}) [query] target)]
           (get ret route)))})))

(defn- generate-parser-mutate [user-parser]
  (fn mutate [{:keys [state target ast] :as env} k params]
    (let [tx [(om/ast->query ast)]
          update-route? (symbol-identical? k 'compassus.core/update-route!)]
      (if update-route?
        (let [{:keys [route]} params]
          {:value {:keys [::route ::route-data]}
           :action #(swap! state assoc ::route route)})
        ;; TODO: this is potentially a problem for returning mutation results
        ;; maybe we need to wrap it inside an `:action` thunk
        ;; probably also problematic
        (user-parser (assoc env :parser user-parser) tx target)))))

;; TODO:
;; - probably need to make this a multimethod, because of ::route and ::route-data
;; - check behavior for (not= target nil)
;; - check behavior for `full-query`
(defn- make-parser [user-parser]
  (om/parser {:read (generate-parser-read user-parser)
              :mutate (generate-parser-mutate user-parser)}))


(defn- find-index-route [routes]
  (let [first-route (ffirst routes)
        index-route (reduce (fn [_ [k class]]
                              (when (-> class meta ::index-route)
                                (reduced k))) routes)]
    (or index-route first-route)))

(defn- normalize-routes [routes index-route]
  (let [class (get routes index-route)]
    (cond-> routes
      (instance? MetaFn class) (assoc index-route (gobj/get class "afn")))))

(defn- process-reconciler-opts
  [{:keys [state parser] :as reconciler-opts} route->component route-info]
  (let [normalize? (not (satisfies? IAtom state))
        merged-query (transduce (map om/get-query)
                       (completing into) [] (vals route->component))
        route-info {::route route-info}
        state (if normalize?
                (atom (merge (om/tree->db merged-query state true)
                             route-info))
                (swap! state merge route-info))]
    (merge reconciler-opts
           {:state state
            :parser (make-parser parser)})))

;; TODO:
;; - should routes be idents or just keywords?
(defn application [{:keys [routes wrapper reconciler-opts] :as opts}]
  (let [index-route (find-index-route routes)
        route-info  (cond-> index-route
                      (keyword? index-route) (vector '_))
        route->component (normalize-routes routes index-route)
        reconciler-opts' (process-reconciler-opts reconciler-opts route->component route-info)
        reconciler (om/reconciler reconciler-opts')
        opts' (merge opts {:routes route->component
                           :reconciler-opts reconciler-opts'})]
    (CompassusApplication.
      {:route->component route->component
       :parser           (:parser reconciler-opts)
       :reconciler       reconciler
       :root-class       (make-root-class opts')}
      (atom {}))))


;; TODO:
;; - docstrings
;; - codox
