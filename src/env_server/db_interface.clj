(ns env-server.db-interface)

;; -------------------- DATABASE INTERFACE --------------------

(defmulti get-db-value
  "Gets/refreshes the database value from the backing store."
  :backing-type)
(defmulti modify-db-value
  "Takes two parameters, the first, a vector, describing the key to set similar to assoc-in, the second the value to set"
  (fn [db path value]
    (:backing-type db)))
