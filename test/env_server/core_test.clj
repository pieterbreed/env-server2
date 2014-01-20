(ns env-server.core-test
  (:require [clojure.test :refer :all]
            [env-server.core :refer :all]))

(deftest application-tests
  (testing "That the application hash algorithm is consistent"
    (let [v (-create-hash "name" #{"one" "two" "three"})]
      (is (= v
             (-create-hash "name" #{"one" "two" "three"})))))
  
  (testing "That you can create an application without specifying data"
    (is (-> nil
            (create-application "path/to/app")
            nil?
            not)))

  (testing "That you can create an application and specify simple data"
    (is (-> nil
            (create-application "path/to/app"
                                #{"key1" "key2"}))))
  
  (testing "When you create an application you can retrieve versions for it"
    (is (-> nil
            (create-application "path/to/app")
            first
            (get-application-versions "path/to/app")
            count
            (= 1))))

  (testing "When you create an app without data, you can add data later"
    (is (-> nil
            (create-application "path/to/app")
            first
            (add-application-settings "path/to/app" #{"one" "two" "three"}))))

  (testing "When an application has settings they can be retrieved later"
    (let [settings #{"one" "two" "three"}
          name "path/to/app"
          version (-create-hash name settings)]
      (is (= settings
             (-> nil
                 (create-application name settings)
                 first
                 (get-application-settings name version)))))))

(deftest environment-tests
  (testing "That environments can be added to a new db"
    (let [[db v] (create-environment nil "name" {"key1" "value1"
                                                 "key2" "value2"}
                                     nil)]
      (is (-> db
              get-environment-names
              count
              (= 1)))
      (is (-> db
              (get-environment-versions "name")
              count
              (= 1)))))

  (testing "That created environments has their own data and values"
    (let [name "name"
          kvps {"key1" "value1"
                "key2" "value2"}
          [db v] (create-environment nil name kvps nil)]
      (is (= (get-environment-data db name v)
             kvps)))))

