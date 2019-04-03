(defproject com.gfredericks/repl-utils "0.2.19-SNAPSHOT"
  :description "Gary Fredericks' repl utils."
  :url "https://github.com/fredericksgary/repl-utils"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/java.classpath "0.3.0"]
                 [org.clojure/tools.analyzer.jvm "0.7.2"]
                 [org.clojure/tools.namespace "0.2.10"]]
  :deploy-repositories [["releases" :clojars]])
