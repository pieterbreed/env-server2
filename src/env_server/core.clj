(ns env-server.core
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [compojure.core :as compcore]
            [compojure.handler :as comphandler]
            [compojure.route :as comproute]
            [digest :as digest]
            [org.httpkit.server :as httpkit]
            [ring.middleware.format :as format]
            [ring.middleware.reload :as reload]
            [ring.util.request :as request]
            [ring.util.response :as response]
            [env-server.utils :refer [strings?]]
            [env-server.db-interface :refer :all]
            [slingshot.slingshot :refer [throw+ try+]])
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
  {:pre [(or (nil? db)
             (map? db))
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
  {:pre [(contains? db :backing-type)]}
  (-get-names (get-db-value db) :applications))

(defn get-application-versions
  [db name]
  {:pre [(contains? db :backing-type)
         (string? name)]}
  (-> (get-db-value db)
      (-app-or-error name)
      -get-versions))

(defn add-application-settings
  [db name settings]
  {:pre [(contains? db :backing-type)
         (string? name)
         (or (set? settings)
             (nil? settings))]}
  (let [effective-settings (or settings #{})
        v (-create-set-hash name effective-settings)]
    (modify-db-value db [:applications name v] {:settings effective-settings})
    v))

(defn create-application
  ([db name]
     {:pre [(contains? db :backing-type)]}
     (add-application-settings db name nil))
  ([db name settings]
     (add-application-settings db name settings)))

(defn get-application-settings
  [db name version]
  {:pre [(contains? db :backing-type)
         (string? name)
         (string? version)]}
  (let [app (-app-and-version-or-error (get-db-value db) name version)]
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
  {:pre [(contains? db :backing-type)]}
  (-get-names (get-db-value db) :environments))

(defn get-environment-versions
  [db name]
  {:pre [(contains? db :backing-type)
         (string? name)]}
  (let [env (-env-or-error (get-db-value db) name)]
    (-get-versions env)))

(defn get-environment-data
  [db name v]
  {:pre [(contains? db :backing-type)
         (string? name)
         (string? v)]}
  (-env-and-version-or-error (get-db-value db) name v))

(defn create-environment
  [db name kvps base]
  {:pre [(contains? db :backing-type)
         (map? kvps)
         (every? string? (keys kvps))
         (every? string? (vals kvps))
         (string? name)
         (or (nil? base)
             (and (vector? base)
                  (string? (first base))
                  (string? (second base))))]}
  (let [dbval (get-db-value db)
        base-data (if-let [[bname bver] base]
                    (-env-and-version-or-error dbval bname bver)
                    {})
        effective-data (merge base-data kvps)
        v (-create-map-hash name effective-data)]
    (modify-db-value db [:environments name v] effective-data)
    v))

(defn realize-application
  "Realizes an application in an environment if its possible. IE, provides values for all of the keys that the application requires or throws an error"
  [db [appname appver] [envname envver]]
  {:pre [(contains? db :backing-type)
         (strings? [appname appver envname envver])]}
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


;; -------------------- DATABASE IMPLEMENTATIONS --------------------

;; -------------------- IN-MEMORY --------------------

(defn create-in-memory-backing-store
  "Creates a backing store that is an atom"
  [v]
  {:backing-type :in-memory
   :value (atom v)})

(defmethod get-db-value :in-memory [db]
  (-> db :value deref))

(defmethod modify-db-value :in-memory [db keys value]
  "Change the value of the db by passing it through changefn. The result is the new value of the db"
  (swap! (:value db) #(assoc-in % keys value)))

;; -------------------- NOT SURE WHAT NEXT --------------------

(defonce DB (create-in-memory-backing-store nil))

;; -------------------- CUSTOM MIDDLEWARE --------------------

(defn -wrap-not-found-error
  [handler error-type]
  (fn [request]
    (try+
     (handler request)
     (catch
         #(and (map? %)
               (contains? % :type)
               (= (:type %)
                  error-type))
         error
       (response/not-found error)))))

(defn wrap-app-not-found-error
  "Tries to catch errors with type :env-server.core/application-name-not-found and returns a decent 404 for those"
  [handler]
  (-wrap-not-found-error handler ::application-name-not-found))

(defn wrap-app-version-not-found-error
  "Tries to catch errors with type :env-server.core/application-version-not-found and returns a 404 instead"
  [handler]
  (-wrap-not-found-error handler ::application-version-not-found))

(defn wrap-env-not-found-error
  [handler]
  (-wrap-not-found-error handler ::environment-name-not-found))

(defn wrap-env-version-not-found-error
  [handler]
  (-wrap-not-found-error handler ::environment-version-not-found))

(defn wrap-bad-request-error
  "Turns any ::bad-request exceptions into 500s"
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch #(and (map? %)
                  (contains? % :type)
                  (= ::bad-request (:type %)))
         error
       (-> (response/response (:message error))
           (response/content-type "text/plain")
           (response/status 400))))))

(defn wrap-app-not-realizable-in-environment-error
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch #(and (map? %)
                  (contains? % :type)
                  (= ::app-not-realizable-in-environment (:type %)))
         error
       (-> (response/response (str "Application cannot be realized in this environment The following keys are missng: "
                                   (reduce str (:missing-keys error))))
           (response/content-type "text/plain")
           (response/status 400))))))

(defn wrap-wellknown-errors
  "Wraps the known errors"
  [handler]
  (-> handler
      wrap-app-not-found-error
      wrap-app-version-not-found-error
      wrap-env-not-found-error
      wrap-env-version-not-found-error
      wrap-bad-request-error
      wrap-app-not-realizable-in-environment-error))

(defn wrap-formats
  "Wraps the different formats of the response and request objects"
  [handler]
  (fn [req]
    (let [new-handler (format/wrap-restful-format handler :formats [:json :yaml])]
      (new-handler req))))

(defn guard-parameter-strings-map
  "validate that both keys and values are strings in the map parameter"
  [m]
  (let [is-valid (or (nil? m)
                     (and (map? m)
                          (every? string? (keys m))
                          (every? string? (vals m))))]
    (if (not is-valid)
      (throw+ {:type ::bad-request
               :message "The body must be a key-value map of strings for keys and values"})
      m)))

(defn guard-parameter-string-set
  "validate that the parameter can be turned into a set that contains only strings"
  [p]
  (let [is-valid (or
                  (nil? p)
                  (and (coll? p)
                       (every? string? p)))]
    (if (not is-valid)
      (throw+ {:type ::bad-request
               :message "The body must contain a sequence of string values"})
      (set p))))

;; -------------------- ROUTING --------------------


(compcore/defroutes application-routes
  (compcore/routes
   (compcore/GET "/" [] (response/response (get-application-names DB)))
   (compcore/GET "/:name" [name] (response/response (get-application-versions DB name)))
   (compcore/GET "/:name/:version" [name version] (response/response (get-application-settings DB name version)))
   (compcore/POST "/:name" [name :as {settings :body-params :as request-map}]
                  (let [settings (guard-parameter-string-set settings)
                        version (create-application DB name settings)
                        url (str (request/request-url request-map)
                                 "/"
                                 version)]
                    (-> (response/redirect-after-post url)
                        (assoc :body url))))))

(compcore/defroutes environment-routes
  (compcore/routes
   (compcore/GET "/" [] (response/response (get-environment-names DB)))
   (compcore/GET "/:name" [name] (response/response (get-environment-versions DB name)))
   (compcore/GET "/:name/:version" [name version] (response/response (get-environment-data DB name version)))
   (compcore/POST "/:name" [name :as {kvps :body-params :as request-map}]
                  (let [kvps (guard-parameter-strings-map kvps)
                        version (create-environment DB name kvps nil)
                        url (str (request/request-url request-map)
                                 "/"
                                 version)]
                    (-> (response/redirect-after-post url)
                        (assoc :body url))))))

(compcore/defroutes all-routes
  (wrap-wellknown-errors
   (wrap-formats
    (compcore/routes
     (compcore/context "/apps" [] application-routes)
     (compcore/context "/env" [] environment-routes)
     (compcore/GET "/realize/:app/:appver/in/:environment/:envver"
                   [app appver environment envver]
                   (response/response 
                    (realize-application DB [app appver] [environment envver])))
     (compcore/GET "/" [] "Hello world3!"))))
  (comproute/not-found "Not found :("))

(def in-dev? true)

;; -------------------- MAIN ENTRYPOINT --------------------

(defn -main
  [& args]
  (let [handler (if in-dev?
                  (reload/wrap-reload (comphandler/api #'all-routes))
                  (comphandler/api all-routes))]
    (httpkit/run-server handler {:port 9090})))
