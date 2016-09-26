(ns compassus.util)

(defn collect [key mixins]
  (->> (map (fn [m] (get m key)) mixins)
       (remove nil?)))

(defn collect-1 [key mixins]
  (first (keep key mixins)))

(defn collect* [keys mixins]
  (->> (mapcat (fn [m] (map (fn [k] (get m k)) keys)) mixins)
       (remove nil?)))
