(def +version+ "0.1.0")

(set-env!
 :source-paths    #{"src/main"}
 :resource-paths  #{"resources"}
 :dependencies '[[org.clojure/clojurescript   "1.9.89"         :scope "provided"]
                 [org.omcljs/om               "1.0.0-alpha37"  :scope "provided"]
                 [com.ladderlife/cellophane   "0.3.2"          :scope "provided"]
                 [com.cognitect/transit-clj   "0.8.285"        :scope "test"]
                 [devcards                    "0.2.1-7"        :scope "test"]
                 [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                 [pandeiro/boot-http          "0.7.3"          :scope "test"]
                 [adzerk/boot-cljs            "1.7.228-1"      :scope "test"]
                 [adzerk/boot-cljs-repl       "0.3.2"          :scope "test"]
                 [adzerk/boot-test            "1.1.1"          :scope "test"]
                 [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"]
                 [adzerk/boot-reload          "0.4.8"          :scope "test"]
                 [adzerk/bootlaces            "0.1.13"         :scope "test"]
                 [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"   :scope "test"]
                 [weasel                      "0.7.0"          :scope "test"]
                 [boot-codox                  "0.9.5"          :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :as cr :refer [cljs-repl-env start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test :as bt-clj]
 '[adzerk.bootlaces      :refer [bootlaces! push-release]]
 '[clojure.tools.namespace.repl :as repl]
 '[crisptrutski.boot-cljs-test :as bt-cljs]
 '[pandeiro.boot-http :refer [serve]]
 '[codox.boot :refer [codox]])

(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
  pom {:project 'compassus
       :version +version+})

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
    (cljs-repl-env)
    (reload)
    (speak)
    (cljs :source-map true
          :compiler-options {:devcards true
                             :parallel-build true}
          :ids #{"js/devcards"})
    (target :dir #{"target"})))

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
      (bt-cljs/test-cljs :js-env :node
        :suite-ns 'compassus.runner
        :cljs-opts {:parallel-build true}
        :exit? exit?))))

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
      :source-uri "https://github.com/anmonteiro/compassus/blob/master/{filepath}#L{line}")))

(deftask release-devcards []
  (set-env! :source-paths #(conj % "src/devcards"))
  (cljs :optimizations :advanced
        :ids #{"js/devcards"}
        :compiler-options {:devcards true
                           :elide-asserts true}))

(deftask release-gh-pages []
  (comp
    (doc)
    (release-devcards)
    (sift :move {#"^index.html" "devcards/index.html"
                 #"^js/"  "devcards/js/"})
    (target)))
