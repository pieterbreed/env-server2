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
          version (-create-app-hash name settings)]
      (is (= settings
             (-> nil
                 (create-application name settings)
                 first
                 (get-application-settings name version)))))))

