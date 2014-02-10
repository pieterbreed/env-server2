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

(deftest util-tests
  (testing "Utils:"
    (testing "that set hashes are consistent"
      (let [v (-create-set-hash "name" #{"three" "one" "two"})]
        (is (= v
               (-create-set-hash "name" #{"one" "two" "three"})))))
    (testing "that map hashes are consistent"
      (let [v (-create-map-hash "name" {"one" "1" "two" "2" "three" "3"})]
        (is (= v
               (-create-map-hash "name" {"three" "3" "two" "2" "one" "1"})))))
    (testing "that or-value returns the default if the second argument is nil"
      (= 1
         (-or-value 1 nil)))
    (testing "that or-value returns the second argument if it is not nil"
      (= 2
         (-or-value 1 2)))
    (testing "that the -db-curry works"
      (is (= 1 ((-db-curry + 1) 0)))
      (is (= 6 ((-db-curry + 1) 5))))

    (testing "that strings? returns true if all are strings"
      (is (= true (strings? '("a" "b" "c" "d"))))
      (is (= true (strings? ["a" "b" "c" "d"])))
      (is (= true (strings? #{"a" "b" "c" "d"}))))

    (testing "that strings? returns false if at least one item is not a string"
      (is (= false (strings? '("a" "b" "c" 1 "d"))))
      (is (= false (strings? ["a" "b" "c" 2 "d"])))
      (is (= false (strings? #{"a" "b" "c" 3 "d"}))))))

;; all of the tests assume the in-memory database (for now)

(deftest empty-db-tests
  (testing "That for an empty (nil) db you can"
    (testing "query the list of applications and get an empty list back"
      (let [db (create-in-memory-backing-store nil)
            res (get-application-names db)]
        (is (and (set? res)
                 (= 0 (count res))))))

    (testing "That you can query the versions of a non-existent application and get a meaningful error"
      (let [db (create-in-memory-backing-store nil)
            res (thrown-map-as-value-or-error (get-application-versions db "name"))]
        (is (= :env-server.core/application-name-not-found
               (:type  res)))))))

(deftest application-tests
  (testing "That you can create an application without specifying data"
    (let [db (create-in-memory-backing-store nil)]
      (is (-> db
              (create-application "path/to/app")
              nil?
              not))))

  (testing "That you can create an application and specify simple data"
    (let [db (create-in-memory-backing-store nil)]
      (is (-> db
              (create-application "path/to/app"
                                  #{"key1" "key2"})))))
  
  (testing "When you create an application you can retrieve versions for it"
    (let [db (create-in-memory-backing-store nil)]
      (do
        (create-application db "path/to/app"))
      (is (-> (get-application-versions db "path/to/app")
              count
              (= 1)))))

  (testing "When you create an app without data, you can add data later"
    (let [db (create-in-memory-backing-store nil)]
      (do
        (create-application db "path/to/app")
        (add-application-settings db "path/to/app" #{"one" "two" "three"}))))

  (testing "When an application has settings they can be retrieved later"
    (let [settings #{"one" "two" "three"}
          name "path/to/app"
          version (-create-set-hash name settings)
          db (create-in-memory-backing-store nil)]
      (do
        (create-application db name settings))
      (is (= settings
             (get-application-settings db name version)))))

  (testing "Expect errors when requesting"
    (let [db (create-in-memory-backing-store nil)
          err-type :env-server.core/application-name-not-found
          appver (create-application db "app" #{"one" "two"})]
      (testing "unset applications"
        (is (= err-type
               (-> (get-application-versions db "test")
                   thrown-map-as-value-or-error
                   :type)))
        (testing "bad versions of good applications"
          (is (= err-type
                 (-> (get-application-settings db "test" "fake_version")
                     thrown-map-as-value-or-error
                     :type))))))))


(deftest environment-tests
  (testing "That environments can be added to a new db"
    (let [db (create-in-memory-backing-store nil)
          v1 (create-environment db "name" {"key1" "value1"
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
    (let [db (create-in-memory-backing-store nil)
          v1 (create-environment db "name1" {"key1" "value1"} nil)
          v2 (create-environment db "name2" {"key2" "value2"} ["name1" v1])]
      (is ( = {"key1" "value1"
               "key2" "value2"}
              (get-environment-data db "name2" v2)))))

  (testing "That created environments has their own data and values"
    (let [name "name"
          kvps {"key1" "value1"
                "key2" "value2"}
          db (create-in-memory-backing-store nil)
          v (create-environment db name kvps nil)]
      (is (= (get-environment-data db name v)
             kvps))))

  (testing "Expect errors when requesting"
    (testing "bad environment name from"
      (let [db (create-in-memory-backing-store nil)
            appver (create-application db "app" #{"one" "two"})
            envver (create-environment db "env" {"one" "1" "two" "2"} nil)
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
    (let [db (create-in-memory-backing-store nil)
          appv (create-application db "app" #{"key1" "key2"})
          envv (create-environment db "env" {"key1" "value1"
                                             "key2" "value2"}
                                   nil)]
      (is (-> (realize-application db ["app" appv] ["env" envv])
              (= {"key1" "value1"
                  "key2" "value2"})))))
  (testing "that if the env does not have all of the keys for the app, then an error is raised"
    (let [db (create-in-memory-backing-store nil)
          appv (create-application db "app" #{"key1" "key2" "key3"})
          envv (create-environment db "env" {"key1" "value1"
                                              "key2" "value2"}
                                   nil)]
          (is (= :env-server.core/app-not-realizable-in-environment
                 (try+
                  (realize-application db
                                       ["app" appv]
                                       ["env" envv])
                  (catch [:type :env-server.core/app-not-realizable-in-environment] {:keys [type]}
                    type))))))

  (testing "that the realized application gets only the keys it requires"
    (let [db (create-in-memory-backing-store nil)
          appv (create-application db "app" #{"key2" "key3"})
          envv (create-environment db "env" {"key1" "1"
                                             "key2" "2"
                                             "key3" "3"}
                                   nil)]
      (is (= {"key2" "2"
              "key3" "3"}
             (realize-application db
                                  ["app" appv]
                                  ["env" envv]))))))

(deftest custom-error-to-response-middleware-tests
  (testing "That when requests are made for invalid objects good HTTP statusses are returned: "
    (testing "404 for"
      (testing "application name that has not been defined"
        (let [error {:type :env-server.core/application-name-not-found,
                     :requested-app-name "name",
                     :available-app-names #{"one" "two"}}
              handler (fn [r] (throw+ error))
              wrapped-handler (wrap-app-not-found-error handler)
              {:keys [status body]} (wrapped-handler nil)]
          (is (= 404 status))
          (is (= body error))))
      (testing "bad application version"
        (let [error {:type :env-server.core/application-version-not-found,
                     :app-name "name"
                     :requested-version "version",
                     :available-versions #{"one" "two"}}
              handler (fn [r] (throw+ error))
              wrapped-handler (wrap-app-version-not-found-error handler)
              {:keys [status body]} (wrapped-handler nil)]
          (is (= 404 status))
          (is (= body error)))))))

(deftest database-interface-tests
  (testing "That the memory interface works with database values"
    (let [memdb (create-in-memory-backing-store nil)])))

