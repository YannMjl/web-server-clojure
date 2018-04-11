(ns report.core

  (:use [clojure.set]
        [clojure.java.io]
        [ring.middleware.json]
        [compojure.core :refer :all]
        [ring.middleware.token-authentication])

  (:require [clojure.java.io]
            [clojure.data.csv]
            [cheshire.core :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]

            [clojure.java.jdbc :as cj]
            [clj-time.coerce :as clt]
            [environ.core :refer [env]]

    ;       [buddy.hashers :as hashers]
    ;       [buddy.auth.accessrules :refer [restrict]]
    ;       [buddy.auth.backends.session :refer [session-backend]]
    ;       [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [ring.util.http-response :as http]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [response]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]])

  )

;----------------------------------------------------------------------------------------------------------------------*

;convert string to integer
(defn to-int [s]
  (if (integer? s)
    s
    (try
      (Integer. s)
      (catch Exception e nil))
    )
  )

;connect to the database
(def db-url (System/getenv "DATABASE_URL"))
;(def db-url (env :database-url))

;----------------------------------------------------------------------------------------------------------------------*
;this section content functions that do multiple operation on the csv file in order to                                 *
;get its content and generate a report                                                                                 *
;----------------------------------------------------------------------------------------------------------------------*

(defn read-file [file]
  (clojure.string/split-lines (slurp file)))

(defn get-org-id [file]
  (apply str (->> (clojure.string/split-lines file)
                  (map #(clojure.string/split % #","))
                  (map #(nth % 1))
                  (map #(clojure.string/split % #"/"))
                  (map #(nth % 0))
                  (map #(clojure.string/replace % "\"" ""))))
  )

(defn get-org-memory [org-name file-line]
  (->> (filter #(= org-name (get-org-id %)) file-line)
       (map #(clojure.string/split % #","))
       (map #(nth % 2))
       (map #(re-find #"(\d+)" %))
       (map second)
       (map to-int)
       (reduce +)
       (hash-map :name org-name :size))
  )

(defn get-org-and-id [file-line org-list]
  (map (fn [org-name]
         (get-org-memory org-name file-line)) org-list)
  )

(defn get-lines-report [file-lines]
  (let [org-list (distinct (map #(get-org-id %) file-lines))]
    (get-org-and-id file-lines org-list))
  )

(defn file-report [file]
  (let [file-line (read-file file)]
    (get-lines-report file-line))
  )

;----------------------------------------------------------------------------------------------------------------------*
;this section content functions that connect and upload a report into the database                                     *
;                                                                                                                      *
;----------------------------------------------------------------------------------------------------------------------*

(defn insert-report-to-database [report date]
  (let [_name (:name report)
        _size (:size report)
        _date (clt/to-sql-date (clt/to-string date))]

    (cj/insert! db-url :cloudrepo_report {:organization _name
                                      :size         _size
                                      :date         _date})
    )
  )

(defn upload-report-to-database [report date]
  (map #(insert-report-to-database % date) report)
  )

(defn upload-file [file]
  (let [file-name (file :filename)
        size (file :size)]
    (do
      (file-report file))
    )
  )

;----------------------------------------------------------------------------------------------------------------------*
;this section content queries functions that read the report data stored in the postgres                               *
;database which returns a sub report by organization name or the data uploaded                                         *
;----------------------------------------------------------------------------------------------------------------------*

(defn get-full-report []
  (cj/query db-url ["SELECT DISTINCT ON (organization)\n
                     organization, size, date\n
                     FROM cloudrepo_report\n
                     ORDER BY organization"])
  )

(defn get-full-report-of-date []
  (cj/query db-url ["SELECT DISTINCT ON (date)\n
                     organization, size, date\n
                     FROM cloudrepo_report\n
                     ORDER BY date DESC"])
  )

(defn view-by-organization [org-name]

  (cj/query db-url ["SELECT
                    \"organization\", \"size\", \"date\"
                    FROM \"cloudrepo_report\"
                    WHERE (\"organization\" = ?) ORDER BY \"date\" DESC" org-name]))

(defn view-by-organization-chart [org-name]

  (cj/query db-url ["SELECT
                    \"size\", \"date\"
                    FROM \"cloudrepo_report\"
                    WHERE (\"organization\" = ?) ORDER BY \"date\" ASC" org-name]))

(defn view-by-date [input]

  (let [_date (clt/to-sql-date (clt/to-string input))]

    (cj/query db-url ["SELECT
                      \"organization\", \"size\", \"date\"
                      FROM \"cloudrepo_report\"
                      WHERE (\"date\" = ?) ORDER BY \"size\" DESC" _date])

    )
  )

(defn view-by-date-chart [input]

  (let [_date (clt/to-sql-date (clt/to-string input))]

    (cj/query db-url ["SELECT
                      \"organization\", \"size\"
                      FROM \"cloudrepo_report\"
                      WHERE (\"date\" = ?) ORDER BY \"organization\" ASC" _date])

    )
  )

(defn delete-by-name [org-name]

  (cj/query db-url ["DELETE FROM
                     \"cloudrepo_report\"
                     WHERE (\"organization\" = ?) RETURNING *" org-name]
            )

  )

(defn delete-by-date [input]

  (let [_date (clt/to-sql-date (clt/to-string input))]

    (cj/query db-url ["DELETE FROM
                      \"cloudrepo_report\"
                      WHERE (\"date\" = ?) RETURNING *" _date])

    )
  )

(defn delete-full-report []

  (cj/query db-url ["DELETE FROM
                    \"cloudrepo_report\" RETURNING *"]
            )

  )


;----------------------------------------------------------------------------------------------------------------------*
;this section content authentication functions                                                                         *
;                                                                                                                      *
;----------------------------------------------------------------------------------------------------------------------*

(defn isAuthenticated [username password]
  (and (= username "admin")
       (= password "pass")
       {:token "#You_Are_In!"})
  )

(defn authenticated? [token]
  (= token "#You_Are_In!")
  )


;----------------------------------------------------------------------------------------------------------------------*
;this section content functions for routes and route handler                                                           *
;REST API configurations are also set here                                                                             *
;----------------------------------------------------------------------------------------------------------------------*

(defn app-handler [request]
  {:status  200
   :headers {"Content-Type"                     "application/json"
             "Access-Control-Allow-Origin"      "*"
             "Access-Control-Allow-Credentials" "false"
             "Access-Control-Allow-Methods"     "POST, GET, DELETE, OPTIONS"
             "Access-Control-Allow-Headers"     "Accept, Content-Type"}

   :body    (generate-string request)}
  )

(defroutes login-route

           (GET "/" []
             (apply str "<h1>Hello Welcome! This is a report page of CloudRepo users</h1>")
             )

           (POST "/login" {params :params
                           :as    req
                           }

             (let [username (get params "username")
                   password (get params "password")
                   ]

               (generate-string (isAuthenticated username password))
               )

             )

           ;(route/not-found "<h1>not logged in  or does not exit</h1>")

           )

(defroutes protected-routes

           (GET "/report" []
             (println "in get report")
             (generate-string (get-full-report))

             )

           (GET "/date" []
             (generate-string (get-full-report-of-date))

             )

           (GET "/date/:input" [input]
             (generate-string (view-by-date input))

             )

           (GET "/date-chart/:input" [input]
             (generate-string (view-by-date-chart input))

             )

           (GET "/name/:input" [input]
             (generate-string (view-by-organization input))

             )

           (GET "/org/:input" [input]
             (generate-string (view-by-organization-chart input))

             )

           (GET "/delete-date/:input" [input]
             (generate-string (delete-by-date input))

             )

           (GET "/delete-name/:input" [input]
             (generate-string (delete-by-name input))

             )

           (GET "/delete-all-record" []
             (generate-string (delete-full-report))

             )

           (POST "/file" {params :params
                          :as    req
                          }

                 (let [fileparam (get params "file")
                       file (:tempfile fileparam)
                       dateparam (get params "date")
                       date (clt/to-string dateparam)
                       report (file-report file)]

                    (generate-string (upload-report-to-database report date))
                   )

                 )

           (route/not-found "<h1>Page not found or does not exit</h1>")

           )


;----------------------------------------------------------------------------------------------------------------------*
;Behold, our middleware! Note that it's common to prefix our middleware name                                           *
;with "wrap-", since it surrounds any routes an other middleware "inside"                                              *
;                                                                                                                      *
; We can attach our middleware directly to the main application handler. All                                           *
; requests/responses will be "filtered" through our logging handler.                                                   *
;----------------------------------------------------------------------------------------------------------------------*

(defn allow-cross-origin
  "Middleware function to allow cross origin requests from browsers.

  When a browser attempts to call an API from a different domain, it makes an OPTIONS request first to see the server's
  cross origin policy.  So, in this method we return that when an OPTIONs request is made.

  Additionally, for non OPTIONS requests, we need to just returm the 'Access-Control-Allow-Origin' header or else the browser won't read the data properly.

  The above notes are all based on how Chrome works. "
  ([handler]
   (allow-cross-origin handler "*"))
  ([handler allowed-origins]
   (fn [request]
     (if (= (request :request-method) :options)
       (-> (http/ok)                                        ; Don't pass the requests down, just return what the browser needs to continue.
           (assoc-in [:headers "Access-Control-Allow-Origin"] allowed-origins)
           (assoc-in [:headers "Access-Control-Allow-Methods"] "GET,POST,DELETE")
           (assoc-in [:headers "Access-Control-Allow-Headers"] "X-Requested-With,Content-Type,Cache-Control,Origin,Accept,Authorization")
           (assoc :status 200))
       (-> (handler request)                                ; Pass the request on, but make sure we add this header for CORS support in Chrome.
           (assoc-in [:headers "Access-Control-Allow-Origin"] allowed-origins))))))

(defn wrap-log-request [handler]
  (fn [req]             ; return handler function
    (println req)       ; perform logging
    (handler req))      ; pass the request through to the inner handler
  )
(def log-route
  (-> login-route
      wrap-log-request
      wrap-json-response))

(def secured-routes
  (-> protected-routes
      wrap-log-request
      wrap-json-response
      ;(wrap-token-authentication authenticated?)
      )
  ; With this middleware in place, we are all set to parse JSON request bodies and
  ; serve up JSON responses
  )

(def main-routes
  (-> (routes log-route secured-routes)
      (allow-cross-origin)
    )
  )

;----------------------------------------------------------------------------------------------------------------------*
;this section content the main function that start the server                                                          *
;on local host port 5000                                                                                               *
;----------------------------------------------------------------------------------------------------------------------*

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]

    (jetty/run-jetty (wrap-cors (wrap-multipart-params main-routes)
                                :access-control-allow-methods #{:get :post :delete :options}
                                :access-control-allow-headers #{:accept :content-type}
                                :access-control-allow-origin [#"https://yannmjl.github.io" #"http://localhost:4200"]
                                )
                     {:port port :join? false}
                     )

    )

  )

;----------------------------------------------------------------------------------------------------------------------*
