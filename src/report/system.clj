(ns report.system

  (:use [compojure.core :refer :all])

  (:require [cheshire.core :refer :all]
            [environ.core :refer [env]]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            )

  )

;-----------------------------------------------------------------------------------------*
;this section content a construction function for the application                         *
;It specify configuration of parameters and will create instances and return a data       *
;as a map                                                                                 *
;-----------------------------------------------------------------------------------------*

(defn system[route port]
  "Returns a new instance of the whole application."

  {:db        (System/getenv "DATABASE_URL")

   :handler   {:status  200
               :headers {"Content-Type"                     "application/json"
                         "Access-Control-Allow-Origin"      "*"
                         "Access-Control-Allow-Credentials" "false"
                         "Access-Control-Allow-Methods"     "POST, GET, OPTIONS"
                         "Access-Control-Allow-Headers"     "Accept, Content-Type"}
               }

   :route     route

   :webserver nil

   :port      port
   }
  )

;-----------------------------------------------------------------------------------------*
;this section content functions that start and stop the web server                        *
;in that they return a new value representing the "started" or "stopped" system,          *
;but they also have to perform side effects along the way, such as opening a connection   *
;to a database or starting a web server.                                                  *
;-----------------------------------------------------------------------------------------*

(defn start [system]
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  (let [port (Integer. (env :port "5000"))
        server (jetty/run-jetty (wrap-cors (wrap-multipart-params (:route system))
                                           :access-control-allow-methods [:get :post :delete :options]
                                           :access-control-allow-headers ["Accept, Content-Type"]
                                           :access-control-allow-origin [#"http://localhost:4200"  #"https://yannmjl.github.io"]
                                           )
                                {:port port :join? false})]
    (assoc system :webserver server))

  )
(defn stop [system]
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."

  (when (:webserver system)

    (.stop (:webserver system))

    (assoc system :webserver nil)
    )

  )

;-----------------------------------------------------------------------------------------*
