(ns compassus.core
  (:require [goog.object :as gobj]
            [om.next :as om :refer-macros [ui]]
            [om.next.impl.parser :as parser]))

(defn get-reconciler [app]
  (-> app :config :reconciler))

;; TODO: this is the root-class, what about when it turns into a component?
;; change name to `root-class`
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
                                queue?
                                (into (om/transform-reads reconciler [::route-data])))))))

(defn- infer-query
  [{:keys [query]} route]
  [{route (cond-> query
            (map? query) (get route))}])

(defn dispatch
  "Helper function for implementing the read and mutate multimethods.
   Dispatches on the remote target and the parser dispatch key."
  [{:keys [target]} key _]
  [target key])

(defmulti read dispatch)

(defmethod read :default
  [{:keys [target] :as env} key params]
  (let [dispatch  [:default key]
        submethod (get-method read dispatch)
        this      (get-method read :default)]
    (if (and submethod (not= submethod this))
      (do
        (-add-method read dispatch submethod)
        (submethod env key params))
      (throw
        (ex-info (str "Missing multimethod implementation for dispatch value " dispatch)
          {:type :error/missing-method-implementation})))))

(defmethod read [nil ::route]
  [{:keys [state]} key _]
  {:value (get @state key)})

(defmethod read [nil ::route-data]
  [{:keys [state user-parser] :as env} key params]
  (let [st @state
        [route _] (get st ::route)
        query (infer-query env route)
        ret (user-parser env query)]
    {:value (get ret route)}))

(defmethod read [:default ::route]
  [{:keys [state]} key _]
  {:value (get @state key)})

(defmethod read [:default ::route-data]
  [{:keys [state target ast user-parser] :as env} key params]
  (let [st @state
        [route _] (get st ::route)
        query (infer-query env route)
        ret (user-parser env query target)]
    (when-not (empty? ret)
      {:remote (parser/expr->ast (:query ast))})))

(defmulti mutate dispatch)

(defmethod mutate :default
  [{:keys [target] :as env} key params]
  (let [methods              (methods mutate)
        [dispatch submethod] (if-let [method (get methods [:default key])]
                               [[:default key] method]
                               (if (nil? target)
                                 [[target :default] (get methods [target :default])]
                                 [[:default :default] (get methods [:default :default])]))]
    (if submethod
      (do
        (-add-method mutate dispatch submethod)
        (submethod env key params))
      (throw
        (ex-info (str "Missing multimethod implementation for dispatch value " dispatch)
          {:type :error/missing-method-implementation})))))

(defmethod mutate [nil :default]
  [{:keys [ast user-parser] :as env} _ _]
  (let [tx [(om/ast->query ast)]]
    {:action #(user-parser env tx)}))

(defmethod mutate [:default :default]
  [{:keys [target ast user-parser] :as env} key params]
  (let [tx [(om/ast->query ast)]]
    {:remote (not (empty? (user-parser env tx target)))}))

(defmethod mutate [:default 'compassus.core/update-route!]
  [{:keys [state] :as env} key params user-parser]
  (let [{:keys [route]} params]
    {:value {:keys [::route ::route-data]}
     :action #(swap! state assoc ::route route)}))

(defn- generate-parser-read [user-parser]
  (fn [env key params]
    (read (assoc env :user-parser user-parser) key params)))

(defn- generate-parser-mutate [user-parser]
  (fn [env key params]
    (mutate (assoc env :user-parser user-parser) key params)))

(defn- make-parser [user-parser]
  (om/parser {:read   (generate-parser-read user-parser)
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
                (doto state
                  (swap! merge route-info)))]
    (merge reconciler-opts
           {:state state
            :parser (make-parser parser)}
           (when normalize?
             {:normalize true}))))

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
