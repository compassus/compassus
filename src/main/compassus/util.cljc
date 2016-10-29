(ns compassus.util
  (:require [om.util :as om-util]
            [om.next.impl.parser :as parser]))

(defn collect [key mixins]
  (->> (map (fn [m] (get m key)) mixins)
       (remove nil?)))

(defn collect-1 [key mixins]
  (first (keep key mixins)))
