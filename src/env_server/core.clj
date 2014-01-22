(ns env-server.core
  (:require [clojure.string :as str]
            [digest :as digest]
            [slingshot.slingshot :refer [throw+]]
            [clojure.set :as set])
  (:gen-class))

;; -------------------- UTILS --------------------

(defn -create-hash
  [name settings]
  {:pre [(string? name)
         (sequential? settings)]}
  (->> settings
       sort
       ((fn [c] (conj c name)))
       (reduce #(str %1 ":" %2))
       str/upper-case
       digest/md5))

(defn -create-set-hash
  [name set]
  {:pre [(string? name)
         (set? set)]}
  (-create-hash name (map identity set)))

(defn -create-map-hash
  [name m]
  {:pre [(string? name)
         (map? m)]}
  (-create-hash name (->> m
                          (map (fn [[k v]] (str k ":" v))))))

(defn -get-names
  [db key]
  {:pre [(map? db)
         (keyword? key)]}
  (->> db
       key
       keys
       (apply hash-set)))

(defn -get-versions
  [m]
  {:pre [(map? m)]}
  (->> m
       keys
       (apply hash-set)))

;; -------------------- APPLICATIONS --------------------

(defn -app-or-error
  [db name]
  {:pre [(map? db)
         (string? name)]}
  (if-let [res (get-in db [:applications name])]
    res
    (throw+ {:type ::application-name-not-found
             :requested-app-name name
             :available-app-names (-get-names db :applications)})))

(defn -app-and-version-or-error
  [db name ver]
  {:pre [(map? db)
         (string? name)
         (string? ver)]}
  (let [app (-app-or-error db name)]
    (if-let [res (get app ver)]
      res
      (throw+ {:type ::application-version-not-found
               :app-name name
               :requested-version ver
               :available-versions (-get-versions app)}))))

(defn get-application-names
  [db]
  {:pre [(map? db)]}
  (-get-names db :applications))

(defn get-application-versions
  [db name]
  {:pre [(map? db)
         (string? name)]}
  (let [app (-app-or-error db name)]
    (-get-versions app)))

(defn add-application-settings
  [db name settings]
  {:pre [(or (nil? db)
             (map? db))
         (string? name)
         (or (set? settings)
             (nil? settings))]}
  (let [effective-settings (or settings #{})
        v (-create-set-hash name effective-settings)]
    (-> db
        (assoc-in [:applications name v] {:settings effective-settings})
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

(defn -env-or-error
  [db name]
  {:pre [(map? db)
         (string? name)]}
  (if-let [env (get-in db [:environments name])]
    env
    (throw+ {:type ::environment-name-not-found
             :requested-name name
             :available-env-names (-get-names db :environments)})))

(defn -env-and-version-or-error
  [db name ver]
  {:pre [(map? db)
         (string? name)
         (string? ver)]}
  (let [env (-env-or-error db name)]
    (if-let [ver (get env ver)]
      ver
      (throw+ {:type ::environment-version-not-found
               :requested-env-name name
               :requested-version ver
               :available-versions (-get-versions env)}))))

(defn get-environment-names
  [db]
  {:pre [(map? db)]}
  (-get-names db :environments))

(defn get-environment-versions
  [db name]
  {:pre [(map? db)
         (string? name)]}
  (let [env (-env-or-error db name)]
    (-get-versions env)))

(defn get-environment-data
  [db name v]
  {:pre [(map? db)
         (string? name)
         (string? v)]}
  (-env-and-version-or-error db name v))

(defn create-environment
  [db name kvps base]
  {:pre [(or (map? db)
             (nil? db))
         (map? kvps)
         (string? name)
         (or (nil? base)
             (and (vector? base)
                  (string? (first base))
                  (string? (second base))))]}
  (let [base-data (if-let [[bname bver] base]
                    (get-environment-data db bname bver)
                    {})
        effective-data (merge base-data kvps)
        v (-create-map-hash name effective-data)]
    (vector 
     (-> db
         (assoc-in [:environments name v] effective-data))
     v)))

(defn realize-application
  "Realizes an application in an environment if its possible. IE, provides values for all of the keys that the application requires or throws an error"
  [db [appname appver] [envname envver]]
  (let [app-settings (get-application-settings db appname appver)
        data (-> (get-environment-data db envname envver)
                 (select-keys app-settings))
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
