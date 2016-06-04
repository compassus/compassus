(ns compassus.devcards.core
  (:require [compassus.core :as c]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [om.util :as omu]
            [devcards.core :as dc :refer-macros [defcard dom-node]]))

;; =============================================================================
;; Setup

(enable-console-print!)

(defn init! []
  (dc/start-devcard-ui!))

;; =============================================================================
;; Examples

(defmulti read om/dispatch)
(defmulti mutate om/dispatch)

(defmethod read :default
  [{:keys [state query]} k _]
  {:value (get @state k)})

#_(defmethod read :route/data
  [{:keys [state query] :as env} k _]
  (let [st @state
        [handler route] (::c/route st)
        route (cond-> route
                (omu/unique-ident? route) pop)
        query (cond-> query
                ;; not full-query
                (map? query) (get handler))]
                                        ;(println "query" query "remote" (parser/query->ast query))
    {:value (om/db->tree query st st)}))

(defmethod read ::c/route-data
   [{:keys [state query]} k _]
   (let [st @state
         route (get st ::c/route)
         route (cond-> route
                 (= (second route) '_) pop)]
     {:value (get-in st route)}))


;; (defmethod read :route/data
;;    [{:keys [state query]} k _]
;;    (let [st @state
;;          route (get st :app/route)
;;          route (cond-> route
;;                  (= (second route) '_) pop)]
;;      {:value (get-in st route)}))

;; (defmethod read :app/route
;;    [{:keys [state query]} k _]
;;    (let [st @state]
;;      {:value (get st k)}))

;; (defmethod mutate 'change/route!
;;   [{:keys [state]} _ {:keys [route]}]
;;   {:value {:keys [:app/route]}
;;    :action #(swap! state assoc :app/route route)})

(defui Home
  static om/IQuery
  (query [this]
    [:home/title :home/content])
  Object
  (render [this]
    (let [{:keys [home/title home/content]} (om/props this)]
      (dom/div nil
        (dom/h3 nil title)
        (dom/p nil (str content))))))

(defui About
  static om/IQuery
  (query [this]
    [:about/title :about/content])
  Object
  (render [this]
    (let [{:keys [about/title about/content]} (om/props this)]
      (dom/div nil
        (dom/h3 nil title)
        (dom/p nil (str content))))))

(def route->component
  )

(def route->factory
  (zipmap (keys route->component)
    (map om/factory (vals route->component))))

(def route->query
  (zipmap (keys route->component)
    (map om/get-query (vals route->component))))

(defn- change-route [c route e]
  (.preventDefault e)
  (om/transact! c `[(~'change/route! {:route ~route})]))

(defn wrapper [{:keys [owner factory props]}]
  (let [route "cena"]
    (dom/div #js {:style #js {:margin "0 auto"
                              :height 250
                              :width 500
                              :backgroundColor "oldlace"}}
      (dom/div #js {:style #js {:minWidth "100%"
                                :minHeight "48px"
                                :lineHeight "48px"
                                :verticalAlign "middle"
                                :borderBottomWidth "2px"
                                :borderBottomStyle "solid"}}
        (dom/h2 #js {:style #js {:margin 0
                                 :textAlign "center"
                                 :lineHeight "48px"}}
          "Om Next Routing"))
      (dom/div #js {:style #js {:display "inline-block"
                                :width "25%"
                                :minHeight "80%"
                                :verticalAlign "top"
                                :backgroundColor "gainsboro"}}
        (dom/ul nil
          (dom/li #js {:style #js {:marginTop "20px"}}
            (dom/a #js {:href "#"
                        :style (when (= (first route) :app/home)
                                 #js {:color "black"
                                      :cursor "text"})
                        :onClick #(change-route owner '[:app/home _] %)}
              "Home"))
          (dom/li #js {:style #js {:marginTop "5px"}}
            (dom/a #js {:href "#"
                        :style (when (= (first route) :app/about)
                                 #js {:color "black"
                                      :cursor "text"})
                        :onClick #(change-route owner '[:app/about _] %)}
              "About")))
        (dom/p #js {:style #js {:textAlign "center"
                                :textDecoration "underline"
                                :marginBottom "5px"
                                :marginTop "30px"
                                :fontWeight "bold"}}
          "Current route:")
        (dom/p #js {:style #js {:textAlign "center"
                                :margin 0
                                :color "red"}}
          (str (pr-str route))))
      (dom/div #js {:style #js {:display "inline-block"
                                :width "70%"
                                :minHeight "70%"
                                :verticalAlign "top"
                                :padding "12.5px 12.5px 12.5px 10.5px"
                                :borderLeftWidth "2px"
                                :borderLeftStyle "solid"}}
        (factory props)))))

(defonce app-state
  {:app/route '[:app/home _]
   :app/home {:home/title "Home page"
              :home/content "This is the homepage. There isn't a lot to see here."}
   :app/about {:about/title "About page"
               :about/content "This is the about page, the place where one might write things about their own self."}})

(def app
  (c/application {:routes {:app/home Home
                           :app/about About}
                  :wrapper wrapper
                  :reconciler-opts {:state (atom app-state)
                                    :parser (om/parser {:read read :mutate mutate})}}))

(defcard example-page-card
  (dom-node
    (fn [_ node]
      (c/mount! app node))))
