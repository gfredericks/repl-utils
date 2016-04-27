(defproject com.gfredericks/repl-utils "0.2.9"
  :description "Gary Fredericks' repl utils."
  :url "https://github.com/fredericksgary/repl-utils"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.cemerick/pomegranate "0.3.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/java.classpath "0.2.2"]
                 [org.clojure/tools.analyzer.jvm "0.6.5"]
                 [org.clojure/tools.namespace "0.2.10"]]
  :deploy-repositories [["releases" :clojars]])
