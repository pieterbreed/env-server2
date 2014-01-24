(ns env-server.core
  (:require [clojure.string :as str]
            [digest :as digest]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.set :as set]
            [ring.middleware.reload :as reload]
            [ring.util.response :as response]
            [compojure.core :as compcore]
            [compojure.handler :as comphandler]
            [compojure.route :as comproute]
            [org.httpkit.server :as httpkit]
            [ring.middleware.json :as ringjson])
  
  (:gen-class))

;; -------------------- UTILS --------------------

(defn -or-value
  "Returns the default (first) parameter's value if the second is nil, else the second param value"
  [d v]
  (if (nil? v) d v))

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
  {:pre [(or (nil? db)
             (map? db))
         (keyword? key)]}
  (->> db
       key
       keys
       (apply hash-set)))

(defn -get-versions
  [m]
  {:pre [(or (nil? m)
             (map? m))]}
  (->> m
       keys
       (apply hash-set)))

;; -------------------- APPLICATIONS --------------------

(defn -app-or-error
  [db name]
  {:pre [(or (nil? db)
             (map? db))
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
  {:pre [(or (nil? db)
             (map? db))]}
  (-get-names db :applications))

(defn get-application-versions
  [db name]
  {:pre [(or (nil? db)
             (map? db))
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

;; -------------------- DATABASE --------------------

(def DB (atom nil))

;; -------------------- CUSTOM MIDDLEWARE --------------------

(defn wrap-app-not-found-error
  "Tries to catch errors with type :env-server.core/application-name-not-found and returns a decent 404 for those"
  [handler]
  (fn [request]
    (try+
     (handler request)
     (catch
         #(and (map? %)
               (contains? % :type)
               (= (:type %)
                  ::application-name-not-found))
         error
       (response/not-found "Look! I'm a 404!")))))

;; -------------------- ROUTING --------------------

(compcore/defroutes application-routes
  (compcore/GET "/" [] (response/response (get-application-names @DB)))
  (compcore/GET "/:name" [name] (response/response (get-application-versions @DB name)))
  (compcore/GET "/:name/:version" [name version] (response/response (get-application-settings @DB name version)))
  (compcore/GET "/test" [] (response/response ["testing" "aganai"])))

(compcore/defroutes all-routes
  (ringjson/wrap-json-response
   (compcore/context "/apps" [] application-routes)
   (compcore/GET "/" [] "Hello world3!"))
  (comproute/not-found "Not found :("))

(def in-dev? true)

;; -------------------- MAIN ENTRYPOINT --------------------

(defn -main
  [& args]
  (let [handler (if in-dev?
                  (reload/wrap-reload (comphandler/api #'all-routes))
                  (comphandler/api all-routes))]
    (httpkit/run-server handler {:port 9090})))
