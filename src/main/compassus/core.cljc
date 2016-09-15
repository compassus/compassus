(ns compassus.core
  #?(:clj (:refer-clojure :exclude [read]))
  (:require #?@(:cljs [[goog.object :as gobj]
                       [om.next :as om :refer-macros [ui]]]
                :clj  [[cellophane.next :as om :refer [ui]]])
            [om.util :as util]
            [om.next.impl.parser :as parser]))

(defn get-reconciler
  "Returns the Om Next reconciler for the given Compassus application."
  [app]
  (-> app :config :reconciler))

(defn root-class
  "Returns the application's root class."
  [app]
  (-> app :config :root-class))

(defn- make-root-class
  [{:keys [routes wrapper history]}]
  (let [route->query (into {}
                       (map (fn [[route class]]
                              (when (om/iquery? class)
                                [route (om/get-query class)])))
                       routes)
        route->factory (zipmap (keys routes)
                               (map om/factory (vals routes)))
        {:keys [setup teardown]} history]
    (ui
      static om/IQuery
      (query [this]
        [::route {::route-data route->query}])
      Object
      (componentDidMount [this]
        (when setup
          (setup)))
      (componentWillUnmount [this]
        (when teardown
          (teardown)))
      (render [this]
        (let [props (om/props this)
              route (::route props)
              route-data (::route-data props)
              factory (get route->factory route)]
          (if wrapper
            (wrapper {:owner   this
                      :factory factory
                      :props   route-data})
            (factory route-data)))))))

(defrecord ^:private CompassusApplication [config state])

(alter-meta! #'->CompassusApplication assoc :private true)
(alter-meta! #'map->CompassusApplication assoc :private true)

(defn application?
  "Returns true if x is a Compassus application"
  [x]
  (instance? CompassusApplication x))

(defn mount!
  "Given a Compassus application and a target root DOM node, mount the
   application. Analogous to `om.next/add-root!`."
  [app target]
  {:pre [(application? app)]}
  (let [reconciler (get-reconciler app)
        root (root-class app)]
    (om/add-root! reconciler root target)))

(defn index-route
  "Specifies that the given class is the index route of the application"
  [class]
  {:pre [(fn? class)]}
  (with-meta {:class class} {::index-route true}))

(defn current-route
  "Returns the current application route. x might be the application,
   the reconciler or a component instance."
  [x]
  {:pre [(or (om/reconciler? x) (application? x) (om/component? x))]}
  (let [reconciler (cond-> x
                     (application? x) get-reconciler
                     (om/component? x) om/get-reconciler)
        st @(om/app-state reconciler)]
    (get st ::route)))

(defn set-route!
  "Given a reconciler, Compassus application or component, update the application's
   route. `next-route` may be a keyword or an ident. Takes an optional third
   options argument, a map with the following supported options:

     :queue? - boolean indicating if the application root should be queued for
               re-render. Defaults to true.

     :params - map of parameters that will be merged into the application state.

     tx      - transaction(s) (e.g.: `'(do/it!)` or `'[(do/this!) (do/that!)]`)
               that will be run after the mutation that changes the route. Can be
               used to perform additional setup for a given route (such as setting
               the route's parameters).
     "
  ([x next-route]
   (set-route! x next-route {:queue? true}))
  ([x next-route {:keys [queue? params tx]}]
   {:pre [(or (om/reconciler? x) (application? x) (om/component? x))
          (or (util/ident? next-route) (keyword? next-route))]}
   (let [reconciler (cond-> x
                      (application? x) get-reconciler
                      (om/component? x) om/get-reconciler)
         tx (when-not (nil? tx)
              (cond->> tx
                (not (vector? tx)) vector))]
     (om/transact! reconciler
       (cond-> (into `[(set-route! ~(merge {:route next-route} params))] tx)
         queue?
         (into (om/transform-reads reconciler [::route-data])))))))

(defn- infer-query
  [{:keys [query]} route]
  (when-let [subq (cond-> query
                    (map? query) (get route))]
    [{route subq}]))

(defn- default-method-impl
  [multi-fn {:keys [target] :as env} key params]
  (let [methods              (methods multi-fn)
        [dispatch submethod] (if-let [method (get methods [:default key])]
                               [[:default key] method]
                               (if (nil? target)
                                 [[target :default] (get methods [target :default])]
                                 [[:default :default] (get methods [:default :default])]))]
    (if submethod
      (do
        #?(:clj  (.addMethod ^clojure.lang.MultiFn multi-fn dispatch submethod)
           :cljs (-add-method multi-fn dispatch submethod))
        (submethod env key params))
      (throw
        (ex-info (str "Missing multimethod implementation for dispatch value " dispatch)
          {:type :error/missing-method-implementation})))))

(defn dispatch
  "Helper function for implementing Compassus internal read and mutate multimethods.
   Dispatches on the remote target and the parser dispatch key."
  [{:keys [target]} key _]
  [target key])

(defmulti ^:private read dispatch)

(defmethod read :default
  [env key params]
  (default-method-impl read env key params))

(defmethod read [:default ::route]
  [{:keys [state]} key _]
  {:value (get @state key)})

(defmethod read [nil ::route-data]
  [{:keys [route user-parser] :as env} key params]
  (let [query (infer-query env route)
        ret (user-parser env query)]
    {:value (get ret route)}))

(defmethod read [:default ::route-data]
  [{:keys [target ast route user-parser] :as env} key params]
  (let [query (infer-query env route)
        ret (user-parser env query target)]
    (when-not (empty? ret)
      {target (parser/expr->ast (first ret))})))

(defmethod read [nil :default]
  [{:keys [ast user-parser] :as env} key params]
  (let [query [(parser/ast->expr ast)]
        ret (user-parser env query)]
    {:value (get ret key)}))

(defmethod read [:default :default]
  [{:keys [target ast route user-parser] :as env} key params]
  (let [query [(parser/ast->expr ast)]
        ret (user-parser env query target)]
    (when-not (empty? ret)
      {target (parser/expr->ast (first ret))})))

(defmulti ^:private mutate dispatch)

(defmethod mutate :default
  [env key params]
  (default-method-impl mutate env key params))

(defmethod mutate [nil :default]
  [{:keys [ast user-parser] :as env} key _]
  (let [tx [(om/ast->query ast)]
        {:keys [result om.next/error] :as ret} (get (user-parser env tx) key)]
    {:value (dissoc ret :result ::om/error)
     :action #(or result (throw error))}))

(defmethod mutate [:default :default]
  [{:keys [target ast user-parser] :as env} key params]
  (let [tx [(om/ast->query ast)]
        [ret] (user-parser env tx target)]
    {target (cond-> ret
              (some? ret) parser/expr->ast)}))

(defmethod mutate [:default 'compassus.core/set-route!]
  [{:keys [state] :as env} key {:keys [route] :as params}]
  (let [params (dissoc params :route)]
    {:value {:keys (into [::route ::route-data] (keys params))}
     :action #(swap! state merge {::route route} params)}))

(defn- generate-parser-fn [f user-parser]
  (fn [{:keys [state] :as env} key params]
    (let [route (get @state ::route)
          env'  (merge env {:user-parser user-parser
                            :route route})]
      (f env' key params))))

(defn- make-parser [user-parser]
  (om/parser {:read   (generate-parser-fn read user-parser)
              :mutate (generate-parser-fn mutate user-parser)}))

(defn- find-index-route [routes]
  (reduce (fn [fst [k class]]
            (if (-> class meta ::index-route)
              (reduced k)
              fst)) (ffirst routes) routes))

(defn- normalize-routes [routes index-route]
  (let [class (get routes index-route)]
    (cond-> routes
      (map? class) (assoc index-route (:class class)))))

(defn compassus-merge
  "Helper function to replace `om.next/default-merge`. Unwraps the current route
   from the remote response and merges that into the state instead."
  [reconciler state res query]
  (let [route (get state ::route)
        mutation? (symbol? (ffirst res))
        query' (if-not mutation?
                 (get (first query) route)
                 query)]
    (om/default-merge reconciler state
      (cond-> res
        (not mutation?) (get route)) query')))

(defn- make-migrate-fn [migrate]
  (fn migrate-fn
    ([app-state-pure query tempids]
     (migrate-fn app-state-pure query tempids nil))
    ([app-state-pure query tempids id-key]
     (merge (select-keys app-state-pure [::route])
       (migrate app-state-pure query tempids id-key)))))

(defn- process-reconciler-opts
  [{:keys [state parser migrate]
    :or {migrate #'om/default-migrate}
    :as reconciler-opts} route->component index-route]
  (let [normalize? (not #?(:clj  (instance? clojure.lang.Atom state)
                           :cljs (satisfies? IAtom state)))
        route-info {::route index-route}
        state (if normalize?
                (let [merged-query (transduce (map om/get-query)
                                     (completing into) [] (vals route->component))]
                  (atom (merge (om/tree->db merged-query state true)
                               route-info)))
                (doto state
                  (swap! merge route-info)))]
    (merge reconciler-opts
           {:state state
            :parser (make-parser parser)
            :migrate (make-migrate-fn migrate)}
           (when normalize?
             {:normalize true}))))

(defn application
  "Construct a Compassus application from a configuration map.

   Required parameters:

     :routes          - a map of route handler (keyword) to the Om Next component
                        that knows how to present that route. The function
                        `compassus.core/index-route` must be used to define the
                        application's starting route.

                        Example: {:index (compassus/index-route Index)
                                  :about About}

     :reconciler-opts - a map of options used to construct the Om Next reconciler.
                        Valid options can be found in the following link:

                        https://github.com/omcljs/om/wiki/Documentation-%28om.next%29#reconciler-1

   Optional parameters:

     :wrapper         - a function or an Om Next component factory that will wrap
                        all the routes in the application. Useful for applications
                        that have a common layout for every route.

                        It will be passed a map with the following keys (props in
                        the case of a component factory):

                        :owner   - the parent component instance

                        :factory - the component factory for the current route

                        :props   - the props for the current route.

     :history         - a map with keys `:setup` and `:teardown`. Values should
                        be functions of no arguments that will be called when the
                        application mounts and unmounts, respectively. Used to
                        set up / teardown browser history listeners.
   "
  [{:keys [routes wrapper reconciler-opts] :as opts}]
  (let [index-route (find-index-route routes)
        route->component (normalize-routes routes index-route)
        reconciler-opts' (process-reconciler-opts reconciler-opts route->component index-route)
        reconciler (om/reconciler reconciler-opts')
        opts' (merge opts {:routes route->component
                           :reconciler-opts reconciler-opts'})]
    (CompassusApplication.
      {:route->component route->component
       :parser           (:parser reconciler-opts)
       :reconciler       reconciler
       :root-class       (make-root-class opts')}
      (atom {}))))
