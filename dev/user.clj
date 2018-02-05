(ns user

  (:require [report.core :as rc]
            [report.system :as rs]
            [clojure.repl :refer :all]
            [environ.core :refer [env]]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            )

  )

(def system nil)

(defn init
  "Constructs the current development system."
  []
  (let [port (Integer. (env :port "5000"))]

    (alter-var-root #'system
                    (constantly (rs/system rc/myroutes port )))

    )
  )

(defn start []
  "Starts the current development system."
  (alter-var-root #'system rs/start)
  )

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (rs/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))