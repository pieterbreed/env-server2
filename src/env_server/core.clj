(ns env-server.core
  (:require [clojure.string :as str]
            [digest :as digest]
            [slingshot.slingshot :refer [throw+]]
            [clojure.set :as set])
  (:gen-class))

;; -------------------- UTILS --------------------

(defn -create-hash
  [name settings]
  (->> settings
       (map identity)
       sort
       ((fn [c] (conj c name)))
       (reduce #(str %1 ":" %2))
       str/upper-case
       digest/md5))

;; -------------------- APPLICATIONS --------------------

(defn get-application-names
  [db]
  (->> db
       :applications
       keys
       (apply hash-set)))

(defn -app-or-error
  [db name]
  (let [res (get-in db [:applications name])]
    (if (nil? res)
      (throw+ {:type ::application-name-not-found
               :requested-app-name name
               :available-app-names (get-application-names db)})
      res)))

(defn -app-versions
  [app]
  (->> app
       keys
       (apply hash-set)))

(defn -app-and-version-or-error
  [db name ver]
  (let [app (-app-or-error db name)
        res (get app ver)]
    (if (nil? res)
      (throw+ {:type ::application-version-not-found
               :app-name name
               :requested-version ver
               :available-versions (-app-versions app)})
      res)))

(defn get-application-versions
  [db name]
  (let [app (-app-or-error db name)]
    (-app-versions app)))

(defn add-application-settings
  [db name settings]
  (let [v (-create-hash name settings)]
    (-> db
        (assoc-in [:applications name v] {:settings settings})
        (vector v))))

(defn create-application
  ([db name]
     (-> db
         (add-application-settings name nil)))
  ([db name settings]
     (-> db
         (add-application-settings name settings))))

(defn get-application-settings
  [db name version]
  (let [app (-app-and-version-or-error db name version)]
    (:settings app)))

;; -------------------- ENVIRONMENTS --------------------

(defn get-environment-names
  [db]
  (->> db
       :environments
       keys
       (apply hash-set)))

(defn get-environment-versions
  [db name]
  (->> (get-in db [:environments name] {})
       keys
       (apply hash-set)))

(defn get-environment-data
  [db name v]
  (if-let [data (get-in db [:environments name v])]
    data
    (cond
     (nil? (get-in db [:environments name])) (throw+ {:type ::environment-name-not-found
                                                      :requested-env-name name})
     :else (throw+ {:type ::environment-version-not-found
                    :env-name name
                    :requested-version v
                    :available-versions (get-environment-versions db name)}))))

(defn create-environment
  [db name kvps base]
  (let [base-data (if-let [[bname bver] base]
                    (get-environment-data db bname bver)
                    {})
        effective-data (merge base-data kvps)
        v (-create-hash name (keys effective-data))]
    (vector 
     (-> db
         (assoc-in [:environments name v] effective-data))
     v)))

(defn realize-application
  "Realizes an application in an environment if its possible. IE, provides values for all of the keys that the application requires or throws an error"
  [db [appname appver] [envname envver]]
  (let [app-settings (get-application-settings db appname appver)
        data (get-environment-data db envname envver)
        data-keys (->> data
                       keys
                       (apply hash-set))]
    (if (= app-settings data-keys)
      data
      (throw+ {:type ::app-not-realizable-in-environment
               :missing-keys (set/difference app-settings data-keys)
               :app-name appname
               :app-version appver
               :env-name envname
               :env-version envver}))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
