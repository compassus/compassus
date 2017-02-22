(def +version+ "1.0.0-alpha2")

(set-env!
 :source-paths    #{"src/main"}
 :resource-paths  #{"resources"}
 :dependencies '[[org.clojure/clojure         "1.9.0-alpha14"  :scope "provided"]
                 [org.clojure/clojurescript   "1.9.473"        :scope "provided"
                  :classifier "aot"]
                 [org.clojure/clojurescript   "1.9.473"        :scope "provided"]
                 [org.omcljs/om               "1.0.0-alpha47"  :scope "provided"]
                 [cljsjs/react-with-addons    "15.3.1-0"       :scope "test"]
                 [cljsjs/react-dom            "15.3.1-0"       :scope "test"]
                 [com.cognitect/transit-clj   "0.8.297"        :scope "test"]
                 [org.clojure/core.async      "0.3.426"        :scope "test"]
                 [devcards                    "0.2.2"          :scope "test"]
                 [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                 [pandeiro/boot-http          "0.7.6"          :scope "test"]
                 [adzerk/boot-cljs            "2.0.0-SNAPSHOT" :scope "test"]
                 [adzerk/boot-cljs-repl       "0.3.3"          :scope "test"]
                 [adzerk/boot-test            "1.2.0"          :scope "test"]
                 [crisptrutski/boot-cljs-test "0.3.0"          :scope "test"]
                 [doo                         "0.1.7"          :scope "test"]
                 [adzerk/boot-reload          "0.5.1"          :scope "test"]
                 [adzerk/bootlaces            "0.1.13"         :scope "test"]
                 [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"   :scope "test"]
                 [weasel                      "0.7.0"          :scope "test"]
                 [boot-codox                  "0.10.3"         :scope "test"]]
 :exclusions '[org.clojure/clojure org.clojure/clojurescript
               cljsjs/react cljsjs/react-dom])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :as cr :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test :as bt-clj]
 '[adzerk.bootlaces      :refer [bootlaces! push-release push-snapshot]]
 '[clojure.tools.namespace.repl :as repl]
 '[crisptrutski.boot-cljs-test :as bt-cljs]
 '[pandeiro.boot-http :refer [serve]]
 '[codox.boot :refer [codox]])

(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
  pom {:project 'compassus
       :version +version+
       :description "A routing library for Om Next."
       :url "https://github.com/compassus/compassus"
       :scm {:url "https://github.com/compassus/compassus"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build-jar []
  (set-env! :resource-paths #{"src/main"})
  (adzerk.bootlaces/build-jar))

(deftask release-clojars! []
  (comp
    (build-jar)
    (push-release)))

(deftask deps [])

(deftask devcards []
  (set-env! :source-paths #(conj % "src/devcards"))
  (comp
    (serve)
    (watch)
    (cljs-repl)
    (reload)
    (speak)
    (cljs :source-map true
          :compiler-options {:devcards true
                             :parallel-build true}
          :ids #{"js/devcards"})
    (target)))

(deftask testing []
  (set-env! :source-paths #(conj % "src/test"))
  identity)

(deftask test-clj []
  (comp
    (testing)
    (bt-clj/test)))

(deftask test-cljs
  [e exit?     bool  "Enable flag."]
  (let [exit? (cond-> exit?
                (nil? exit?) not)]
    (comp
      (testing)
      (bt-cljs/test-cljs
        :js-env :node
        :namespaces #{'compassus.tests}
        :cljs-opts {:parallel-build true}
        :exit? exit?))))

(ns-unmap 'boot.user 'test)

(deftask test []
  (comp
    (test-clj)
    (test-cljs)))

(deftask auto-test []
  (comp
    (watch)
    (speak)
    (test-cljs :exit? false)))

(deftask doc []
  (comp
    (codox :name "Compassus"
      :version +version+
      :description "A routing library for Om Next."
      :output-path (str "doc/" +version+)
      :source-uri (str "https://github.com/compassus/compassus/tree/" +version+ "/{filepath}#L{line}")
      :exclude-vars #"^(map)?->\p{Upper}|ui_")))

(deftask release-devcards []
  (set-env! :source-paths #(conj % "src/devcards"))
  (cljs :optimizations :advanced
        :ids #{"js/devcards"}
        :compiler-options {:devcards true
                           :elide-asserts true
                           :parallel-build true
                           :verbose true
                           :compiler-stats true
                           :closure-defines {'goog/DEBUG false}}))

(deftask release-gh-pages []
  (comp
    (doc)
    (release-devcards)
    (sift :move {#"^index.html" "devcards/index.html"
                 #"^js/"  "devcards/js/"})
    (target)))
