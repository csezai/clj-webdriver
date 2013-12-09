(ns clj-webdriver.test.util
  (:use [clojure.test :only [deftest]]
        [clojure.template :only [apply-template]]
        [ring.adapter.jetty :only [run-jetty]]
        [clj-webdriver.driver :only [init-driver]]
        [clj-webdriver.test.config])
  (:require [clj-webdriver.test.example-app.core :as web-app]
            [clojure.tools.logging :as log])
  (:import java.io.File))

;; System checks
(defn chromium-installed?
  []
  (.exists (File. "/usr/lib/chromium-browser/chromium-browser")))

(defn chromium-preferred?
  "If a Chromium installation can be detected and the `WEBDRIVER_USE_CHROMIUM` environment variable is defined, return true."
  []
  (log/info "Chromium installation detected. Using Chromium instead of Chrome.")
  (and (chromium-installed?)
       (get (System/getenv) "WEBDRIVER_USE_CHROMIUM")))

;; Fixtures
(defn start-server [f]
  (loop [server (run-jetty #'web-app/routes {:port test-port, :join? false})]
    (if (.isStarted server)
      (do
        (f)
        (.stop server))
      (recur server))))

;; Utilities
(defmacro thrown?
  "Return truthy if the exception in `klass` is thrown, otherwise return falsey (nil) (code adapted from clojure.test)"
  [klass & forms]
  `(try ~@forms
        false
        (catch ~klass e#
          true)))

(defn exclusive-between
  "Ensure a number is between a min and a max, both exclusive"
  [n min max]
  (and (> n min)
       (< n max)))

(defn multirember
  "Remove every occurrence of an item from a list, recurring into nested lists where necessary. Pulled from my exercises while working through The Little Schemer."
  [item l]
  (cond
   (empty? l) '()

   (not (list? (first l)))
   (cond
    (= (first l) item) (multirember item (next l))
    :else (cons (first l) (multirember item (next l))))

   :else (cons (multirember item (first l)) (multirember item (next l)))))

(defmacro deftest-template-param
  "Create two deftest's for the price of one. Every instance of `__` in the body of this macro will be (1) replaced with `optional-param` for one deftest definition and (2) will be deleted for the other deftest definition generated by this macro. If there are no replacements to be made, the macro will not produce an extra deftest.

   Note: Though this macro looks like it fills the same role as clojure.template/apply-template, the apply-template macro can only replace templated values with other values. We do that (using apply-template), but we also produce a deftest form that completely removes the templated portion, since the intention is to template in an optional argument to functions.

   Example:
     (deftest-template-param test-attribute my-driver
       (is (= (attribute __ \"div\" \"class\") \"active\")))

     ;; Would expand to:
     (do
       (deftest test-attribute
         (is (= (attribute \"div\" \"class\") \"active\")))
       (deftest test-attribute-with-optional-param
         (is (= (attribute my-driver \"div\" \"class\") \"active\"))))"
  [test-name optional-param & body]
  (let [body-no-param (multirember '__ body)
        body-with-param (apply-template '[__] body [optional-param])
        test-name-no-param test-name
        test-name-with-param (symbol (str test-name "-with-optional-param"))]
    (if (= body-no-param body-with-param)
      `(deftest ~test-name-no-param
         ~@body-no-param)
      `(do
         (deftest ~test-name-no-param
           ~@body-no-param)
         (deftest ~test-name-with-param
           ~@body-with-param)))))