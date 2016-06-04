(set-env!
 :source-paths    #{"src/main"}
 :resource-paths  #{"resources"}
 :dependencies '[[org.clojure/clojurescript   "1.9.36"         :scope "provided"]
                 [org.omcljs/om               "1.0.0-alpha36"  :scope "provided"]
                 [com.cognitect/transit-clj   "0.8.285"        :scope "test"]
                 [devcards                    "0.2.1-7"        :scope "test"]
                 [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                 [pandeiro/boot-http          "0.7.3"          :scope "test"]
                 [adzerk/boot-cljs            "1.7.228-1"      :scope "test"]
                 [adzerk/boot-cljs-repl       "0.3.1"          :scope "test"]
                 [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"] 
                 [adzerk/boot-reload          "0.4.8"          :scope "test"]
                 [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"   :scope "test"]
                 [weasel                      "0.7.0"          :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :as cr :refer [cljs-repl-env start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[clojure.tools.namespace.repl :as repl]
 '[crisptrutski.boot-cljs-test :refer [test-cljs]]
 '[pandeiro.boot-http :refer [serve]])

(deftask devcards []
  (set-env! :source-paths #(conj % "src/devcards"))
  (comp
    (serve)
    (watch)
    (cljs-repl-env)
    (reload)
    (speak)
    (cljs :source-map true :compiler-options {:devcards true})
    (target :dir #{"target"})))

(ns-unmap 'boot.user 'test)

(deftask test []
  (set-env! :source-paths #(conj % "src/test"))
  (test-cljs :js-env :node :suite-ns 'compassus.runner))

(deftask auto-test []
  (comp
    (watch)
    (speak)
    (test)))
