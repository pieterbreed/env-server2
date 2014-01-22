(ns env-server.core-test
  (:require [clojure.test :refer :all]
            [env-server.core :refer :all]
            [slingshot.slingshot :refer [try+ throw+]]))

(defmacro thrown-map-as-value-or-error
  "Executes a body of code in a try+ block, catches exceptions that are maps and return those as the value of the expression. If the code block does not throw an error, an error will be thrown indicating that the expected error was not caught."
  [& body]
  `(try+
    (let [res# ~@body]
      (throw+ {:message  "Did not throw :("
               :actual-result res#}))
    (catch map? ~'t
      ~'t)))

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
                 (get-application-settings name version))))))

  (testing "Expect errors when requesting"
    (let [[db appver] (create-application nil "app" #{"one" "two"})
          err-type :env-server.core/application-name-not-found]
      (testing "unset applications"
        (is (= err-type
               (-> (get-application-versions db "test")
                   thrown-map-as-value-or-error
                   :type))))
      (testing "bad versions of good applications"
        (is (= err-type
               (-> (get-application-settings db "test" "fake_version")
                   thrown-map-as-value-or-error
                   :type)))))))

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

  (testing "That environments can be added and based on other environments"
    (let [[db1 v1] (create-environment nil "name1" {"key1" "value1"} nil)
          [db2 v2] (create-environment db1 "name2" {"key2" "value2"} ["name1" v1])]
      (is (not (nil? db2)))
      (is ( = {"key1" "value1"
               "key2" "value2"}
              (get-environment-data db2 "name2" v2)))))

  (testing "That created environments has their own data and values"
    (let [name "name"
          kvps {"key1" "value1"
                "key2" "value2"}
          [db v] (create-environment nil name kvps nil)]
      (is (= (get-environment-data db name v)
             kvps))))

  (testing "Expect errors when requesting"
    (testing "bad environment name from"
      (let [[db0 appver] (create-application nil "app" #{"one" "two"})
            [db envver] (create-environment db0 "env" {"one" "1" "two" "2"} nil)
            expected-exception-type :env-server.core/environment-name-not-found]
        (testing "get-environment-versions"
          (is (= expected-exception-type
                 (-> (get-environment-versions db "fake")
                     thrown-map-as-value-or-error
                     :type))))
        (testing "get-environment-data"
          (is (= expected-exception-type
                 (-> (get-environment-data db "fake" "fake_version")
                     thrown-map-as-value-or-error
                     :type))))
        (testing "realize-application"
          (is (= expected-exception-type
                 (-> (realize-application db ["app" appver] ["fake" "fake_version"])
                     thrown-map-as-value-or-error
                     :type))))))))

(deftest realizing-of-applications-in-environments
  (testing "that if the environment has all the settings as keys then the application is realized in that environment"
    (let [[db1 appv] (create-application nil "app" #{"key1" "key2"})
          [db2 envv] (create-environment db1 "env" {"key1" "value1"
                                                    "key2" "value2"}
                                         nil)]
      (is (-> (realize-application db2 ["app" appv] ["env" envv])
              (= {"key1" "value1"
                  "key2" "value2"})))))
  (testing "that if the env does not have all of the keys for the app, then an error is raised"
        (let [[db1 appv] (create-application nil "app" #{"key1" "key2" "key3"})
              [db2 envv] (create-environment db1 "env" {"key1" "value1"
                                                        "key2" "value2"}
                                             nil)]
          (is (= :env-server.core/app-not-realizable-in-environment
                 (try+
                  (realize-application db2
                                       ["app" appv]
                                       ["env" envv])
                  (catch [:type :env-server.core/app-not-realizable-in-environment] {:keys [type]}
                    type)))))))

