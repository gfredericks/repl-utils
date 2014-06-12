(defproject com.gfredericks/repl-utils "0.1.14-SNAPSHOT"
  :description "Gary Fredericks' repl utils."
  :url "https://github.com/fredericksgary/repl-utils"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.cemerick/pomegranate "0.3.0"]
                 [org.clojure/clojure "1.6.0"]
                 [potemkin "0.3.4"]]
  :lein-release {:deploy-via :clojars})
