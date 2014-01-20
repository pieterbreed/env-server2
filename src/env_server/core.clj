(ns env-server.core
  (:gen-class))

(defn add-application-settings
  [db name settings]
  (-> db
      (assoc-in [:applications name] {:settings settings})))

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

(defn get-application-by-name
  [db name]
  (get-in db [:applications name]))

(defn get-application-settings
  [db name]
  (get-in db [:applications name :settings]))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
