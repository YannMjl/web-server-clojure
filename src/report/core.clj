(ns report.core

  (:use [clojure.java.io]
        [clojure.set]
        [compojure.core :refer :all])

  (:require [clojure.java.io]
            [clojure.data.csv]
            [cheshire.core :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]

            [clojure.java.jdbc :as cj]
            [clj-time.coerce :as clt]
            [environ.core :refer [env]]

            [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [response]]
            [ring.util.hpp-status :as status]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])

  )

;-----------------------------------------------------------------------------------------*

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
(def db (System/getenv "DATABASE_URL"))
(def db-url (env :database-url))

;-----------------------------------------------------------------------------------------*
;this section content functions that do multiple operation on the csv file in order to    *
;get its content and generate a report                                                    *
;-----------------------------------------------------------------------------------------*

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

;-----------------------------------------------------------------------------------------*
;this section content functions that connect and upload a report into the database        *
;                                                                                         *
;-----------------------------------------------------------------------------------------*

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

;-----------------------------------------------------------------------------------------*
;this section content queries functions that read the report data stored in the postgres  *
;database which returns a sub report by organization name or the data uploaded            *
;-----------------------------------------------------------------------------------------*

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

;-----------------------------------------------------------------------------------------*
;this section content functions for routes and route handler                              *
;REST API configurations are also set here                                                *
;-----------------------------------------------------------------------------------------*

(defn app-handler [request]
  {:status  200
   :headers {"Content-Type"                     "application/json"
             "Access-Control-Allow-Origin"      "*"
             "Access-Control-Allow-Credentials" "false"
             "Access-Control-Allow-Methods"     "POST, GET, DELETE, OPTIONS"
             "Access-Control-Allow-Headers"     "Accept, Content-Type"}

   :body    (generate-string request)}
  )

(defroutes myroutes (GET "/" []
                      (apply str "<h1>Hello Welcome! This is a report page of CloudRepo users</h1>")
                      )

           (GET "/report" []
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


;-----------------------------------------------------------------------------------------*
;this section content the main function that start the server                             *
;on local host port 5000                                                                  *
;-----------------------------------------------------------------------------------------*

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]

    (jetty/run-jetty (wrap-cors (wrap-multipart-params myroutes)
                                :access-control-allow-methods [:get :post :delete :options]
                                :access-control-allow-headers ["Content-Type"]
                                :access-control-allow-origin [#"https://yannmjl.github.io" #"http://localhost:4200"]
                                )
                     {:port port :join? false}
                     )

    )

  )

;-----------------------------------------------------------------------------------------*
