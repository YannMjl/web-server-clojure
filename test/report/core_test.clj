(ns report.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock ]
            [report.core :refer :all])
  )

(deftest a-test

  (testing "FIXME, I fail."
    (is (= 1 1)))

  ;-----------------------------------------------------------------------------------------*
  ; use ring.mock to test API's services through their exposed endpoints                    *
  ; testing API responses to various requests                                               *
  ;-----------------------------------------------------------------------------------------*

  (testing "report data endpoint"
    (let [response (myroutes (mock/request :get "/report"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "date in report endpoint"
    (let [response (myroutes (mock/request :get "/date"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "report by date endpoint"
    (let [response (myroutes (mock/request :get "/date/:input"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing " report by date chart-data endpoint"
    (let [response (myroutes (mock/request :get "/date-chart/:input"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "report by org endpoint"
    (let [response (myroutes (mock/request :get "/name/:input"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "report by org chart-data endpoint"
    (let [response (myroutes (mock/request :get "/org/:input"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  ;-----------------------------------------------------------------------------------------*

  (testing "delete report by date endpoint"
    (let [response (myroutes (mock/request :get "/delete-date/:input"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "delete report by org endpoint"
    (let [response (myroutes (mock/request :get "/delete-name/:input"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "delete all record endpoint"
    (let [response (myroutes (mock/request :get "/delete-all-record"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  (testing "uploading a file report"
    (let [response (myroutes (mock/request :post "/file"))]
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "Content-Type"]) "application-json"))))

  ;-----------------------------------------------------------------------------------------*

  (testing "not-found route"
    (let [response (myroutes (mock/request :get "/bogus-route"))]
      (is (= (:status response) 404))))

  )
