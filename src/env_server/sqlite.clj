(ns env-server.sqlite
  (:require [env-server.db-interface :refer :all]
            [korma.db :as db]
            [korma.core :as sql]
            ;; [lobos.connectivity :as lconn]
            ;; [lobos.core :as lcore]
            ;; [lobos.schema :as lschema]
            ))

(defn -create-tables
  "Creates the tables that this impl will need"
  []
  nil)

;; entity definitions

(sql/defentity dbvalues
  (sql/table :values)
  (sql/entity-fields :cljvalue))

;; interface implementation

(defn create-sqlite-backing-store
  "Creates a backing store that creates the backing store in a sqlite database specified with dbfile"
  [dbfile]
  {:backing-type :sqlite
   :value (db/create-db (db/sqlite3 {:db dbfile}))})

(defmethod get-db-value :sqlite [dbv]
  (db/with-db dbv
    (sql/select
     (sql/fields :value)
     (sql/limit 1)
     (sql/order :id :DESC))))

(defmethod modify-db-value :sqlite [dbv keys value]
  nil)
