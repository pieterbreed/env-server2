(ns env-server.core-test
  (:require [clojure.test :refer :all]
            [env-server.core :refer :all]))

(deftest create-application-test
  (testing "That you can create an application without specifying data"
    (is (-> nil
            (create-application "path/to/app")
            nil?
            not)))

  (testing "That you can create an application and specify simple data"
    (is (-> nil
            (create-application "path/to/app"
                                #{"key1" "key2"}))))
  
  (testing "When you create an application you can retrieve it again by path"
    (is (-> nil
        (create-application "path/to/app")
        (get-application-by-name "path/to/app")
        nil?
        not)))

  (testing "When you create an app without data, you can add data later"
    (is (-> nil
            (create-application "path/to/app")
            (add-application-settings "path/to/app" #{"one" "two" "three"}))))

  (testing "When an application has settings they can be retrieved later"
    (is (= #{"one" "two" "three"}
           (-> nil
               (create-application "path/to/app" #{"one" "two" "three"})
               (get-application-settings "path/to/app"))))))

