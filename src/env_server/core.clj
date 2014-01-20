(ns env-server.core
  (:require [clojure.string :as str]
            [digest :as digest])
  (:gen-class))

(defn -create-hash
  [name settings]
  (->> settings
       (map identity)
       sort
       ((fn [c] (conj c name)))
       (reduce #(str %1 ":" %2))
       str/upper-case
       digest/md5))

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

(defn get-application-names
  [db]
  (->> db
       :applications
       keys
       (apply hash-set)))

(defn get-application-versions
  [db name]
  (-> db
      (get-in [:applications name])
      keys))

(defn get-application-settings
  [db name version]
  (get-in db [:applications name version :settings]))

(defn create-environment
  [db name kvps base]
  (let [v (-create-hash name (keys kvps))]
    (vector 
     (-> db
         (assoc-in [:environments name v] kvps))
     v)))

(defn get-environment-names
  [db]
  (->> db
       :environments
       keys
       (apply hash-set)))

(defn get-environment-versions
  [db name]
  (-> 
   (get-in db [:environments name] {})
   keys))

(defn get-environment-data
  [db name v]
  (get-in db [:environments name v]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
