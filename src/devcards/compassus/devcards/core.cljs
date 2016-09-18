(ns compassus.devcards.core
  (:require [compassus.core :as c]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [om.util :as omu]
            [devcards.core :as dc :refer-macros [defcard dom-node]]))

;; =============================================================================
;; Setup

(defn init! []
  (enable-console-print!)
  (dc/start-devcard-ui!))

;; =============================================================================
;; Without normalization

(defmulti read om/dispatch)

(defmethod read :default
  [{:keys [state query]} k _]
  {:value (get @state k)})

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

(defn- change-route [c route e]
  (.preventDefault e)
  (c/set-route! c route))

(defn wrapper [{:keys [owner factory props]}]
  (let [route (c/current-route owner)]
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
                        :style (when (= route :app/home)
                                 #js {:color "black"
                                      :cursor "text"})
                        :onClick #(change-route owner :app/home %)}
              "Home"))
          (dom/li #js {:style #js {:marginTop "5px"}}
            (dom/a #js {:href "#"
                        :style (when (= route :app/about)
                                 #js {:color "black"
                                      :cursor "text"})
                        :onClick #(change-route owner :app/about %)}
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
  {:app/home {:home/title "Home page"
              :home/content "This is the homepage. There isn't a lot to see here."}
   :app/about {:about/title "About page"
               :about/content "This is the about page, the place where one might write things about their own self."}})

(def app
  (c/application {:routes {:app/home (c/index-route Home)
                           :app/about About}
                  :mixins [(c/wrap-render wrapper)]
                  :reconciler-opts {:state (atom app-state)
                                    :parser (om/parser {:read read})}}))

(defcard denormalized-simple-example
  "An example without normalization."
  (dom-node
    (fn [_ node]
      (c/mount! app node))))

;; =============================================================================
;; With normalization

(def notes-app-state
  {:notes/list [{:id 0 :note/title "Some note" :note/content "The note's content"
                 :note/authors [{:id 101 :user/name "Alice Brown"}]}
                {:id 1 :note/title "Untitled" :note/content "TODOs this week"
                 :note/authors [{:id 101 :user/name "Alice Brown"}]}]
   :users/list [{:id 101 :user/name "Alice Brown" :user/notes [{:id 0} {:id 1}]}
                {:id 102 :user/name "Bob Atkins" :user/notes []}]})

(declare Note)

(defui Author
  static om/Ident
  (ident [this {:keys [id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:id :user/name])
  Object
  (render [this]
    (dom/span nil (-> (om/props this) :user/name))))

(def author (om/factory Author {:keyfn :id}))

(defui User
  static om/Ident
  (ident [this {:keys [id]}]
    [:user/by-id id])
  static om/IQuery
  (query [this]
    [:id :user/name {:user/notes (om/get-query Note)}])
  Object
  (render [this]
    (let [{:keys [user/name user/notes]} (om/props this)]
      (dom/li #js {:style #js {:marginBottom "40px"}}
        (dom/p nil name)
        (dom/h4 #js {:style #js {:marginLeft "15px"
                                 :marginBottom "0px"}} "Notes owned:")
        (dom/ul nil
          (map #(dom/li #js {:key (:id %)} (:note/title %)) notes))))))

(def user (om/factory User {:keyfn :id}))

(defui Note
  static om/Ident
  (ident [this {:keys [id]}]
    [:note/by-id id])
  static om/IQuery
  (query [this]
    [:id :note/title :note/content {:note/authors (om/get-query Author)}])
  Object
  (render [this]
    (let [{:keys [id note/title note/content note/authors]} (om/props this)]
      (dom/div #js {:style #js {:border "1px solid black"
                                :padding "10px"}}
        (dom/h2 #js {:style #js {:marginBottom "10px"}} title)
        (dom/h4 #js {:style #js {:marginTop "10px"}}
          "owned by: "
          (interpose ", " (map author authors)))
        (dom/p nil content)
        (dom/button #js {:onClick #(om/transact! this `[(note/share! {:note ~id
                                                                      :user 102}) :users])}
          "Add Bob to note")))))

(def note (om/factory Note {:keyfn :id}))

(defui NoteList
  static om/IQuery
  (query [this]
    [{:notes/list (om/get-query Note)}])
  Object
  (render [this]
    (let [{notes :notes/list} (om/props this)]
      (dom/div #js {:style #js {:width "300px"
                                :display "table-cell"
                                :borderRight "black 1px solid"}}
        (dom/h1 nil
          "Notes   "
          (dom/a #js {:href "#"
                      :onClick #(change-route this :users %)}
            "(See users)"))
        (map note notes)))))

(defui UserList
  static om/IQuery
  (query [this]
    [{:users/list (om/get-query User)}])
  Object
  (render [this]
    (let [{users :users/list} (om/props this)]
      (dom/div #js {:style #js {:width "350px"
                                :display "table-cell"
                                :paddingLeft "30px"}}
        (dom/h1 nil
          "Users   "
          (dom/a #js {:href "#"
                      :onClick #(change-route this :notes %)}
            "(See notes)"))
        (map user users)))))

(defmulti notes-read om/dispatch)
(defmulti notes-mutate om/dispatch)

(defmethod notes-read :default
  [{:keys [state query]} k _]
  (let [st @state]
    {:value (om/db->tree query st st)}))

(defn share-note [{:keys [note user]} state]
  (-> state
    (update-in [:note/by-id note :note/authors]
      (fn [authors]
        (cond-> authors
          (not (some #{[:user/by-id user]} authors))
          (conj [:user/by-id user]))))
    (update-in [:user/by-id user :user/notes]
      (fn [notes]
        (cond-> notes
          (not (some #{[:note/by-id note]} notes))
          (conj [:note/by-id note]))))))

(defmethod notes-mutate 'note/share!
  [{:keys [state]} _ params]
  {:action #(swap! state (partial share-note params))})

(def notes-app
  (c/application
    {:routes {:notes NoteList
              :users UserList}
     :reconciler-opts {:state notes-app-state
                       :parser (om/parser {:read notes-read
                                           :mutate notes-mutate})}}))

(defcard normalization-example
  "An example without normalization."
  (dom-node
    (fn [_ node]
      (c/mount! notes-app node))))

;; =============================================================================
;; Idents example

(defmulti idents-read om/dispatch)

(defmethod idents-read :items
  [{:keys [state query]} k _]
  (let [st @state
        res (om/db->tree query st st)]
    {:value res}))

(defmethod idents-read :item/by-id
  [{:keys [state query query-root]} k _]
  {:value (om/db->tree query query-root @state)})

(defui Item
  static om/Ident
  (ident [this {:keys [id]}]
    [:item/by-id id])
  static om/IQuery
  (query [this]
    [:id :name])
  Object
  (render [this]
    (let [{:keys [id name]} (om/props this)]
      (dom/div nil
        (dom/span nil name)))))

(def item (om/factory Item))

(defui ItemList
  static om/IQuery
  (query [this]
    [{:item/list (om/get-query Item)}])
  Object
  (render [this]
    (let [{:keys [item/list]} (om/props this)]
      (dom/div nil
        (dom/h3 nil "Item List")
        (dom/ul nil
          (map (fn [it] (dom/li nil (item it))) list))))))

(defui Wrapper
  Object
  (render [this]
    (let [{:keys [owner factory props]} (om/props this)
          route (c/current-route this)]
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
                          :style (when (= route :items)
                                   #js {:color "black"
                                        :cursor "text"})
                          :onClick #(change-route owner :items %)}
                "ItemList"))
            (dom/li #js {:style #js {:marginTop "5px"}}
              (dom/a #js {:href "#"
                          :style (when (= route [:item/by-id 0])
                                   #js {:color "black"
                                        :cursor "text"})
                          :onClick #(change-route owner [:item/by-id 0] %)}
                "Item 0")))
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
          (factory props))))))

(def idents-wrapper (om/factory Wrapper))

(defonce idents-app-state
  {:item/list [{:id 0 :name "Item 0"}
               {:id 1 :name "Item 1"}
               {:id 2 :name "Item 2"}]})

(def idents-app
  (c/application {:routes {:items (c/index-route ItemList)
                           [:item/by-id 0] Item}
                  :mixins [(c/wrap-render idents-wrapper)]
                  :reconciler-opts {:state idents-app-state
                                    :parser (om/parser {:read idents-read})}}))

(defcard idents-example
  "## Idents in routes

  You can also specify routes as idents. In this case, the app routes are:
  ```clojure
  {:items (c/index-route ItemList)
   [:item/by-id 0] Item}
  ```
  "
  (dom-node
    (fn [_ node]
      (c/mount! idents-app node))))
