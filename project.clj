(defproject report "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.postgresql/postgresql "42.1.4.jre7"]
                 [org.clojure/java.jdbc "0.7.4"]
                 [org.clojure/data.csv "0.1.4"]

                 [ring "1.6.3"]
                 [ring-cors "0.1.11"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-mock "0.3.2"]
                 [ring/ring-json "0.5.0-beta1"]
                 [ring/ring-jetty-adapter "1.6.3"]

                 [csv-map "0.1.2"]
                 [clj-x256 "0.0.1"]
                 [clj-http "3.7.0"]
                 [clj-time "0.14.2"]
                 [environ "1.1.0"]

                 [compojure "1.6.0"]
                 [cheshire "5.8.0"]
                 [reloaded.repl "0.2.4"]
                 [com.stuartsierra/component "0.3.2"]
                 [javax.servlet/servlet-api "3.0-alpha-1"]]

  :min-lein-version "2.0.0"

  :plugins [[lein-ring "0.12.2"]
            [lein-heroku "0.5.3"]
            [lein-environ "1.1.0"]
            [environ/environ.lein "0.3.1"]]

  :hooks [environ.leiningen.hooks]

  :ring {:handler report.core/app-handler
         :auto-reload? true
         :auto-refresh? false}

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/java.classpath "0.2.3"]
                                  [org.clojure/tools.namespace "0.3.0-alpha4"]]
                   }
             :uberjar {:aot
                       :all
                       }
             :production {:env {:production true}}
             }



  )
