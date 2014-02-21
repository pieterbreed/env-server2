(defproject env-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [digest "1.4.3"]
                 [slingshot "0.10.3"]
                 [ring/ring-devel "1.2.1"]
                 [ring/ring-core "1.2.1"]
                 [compojure "1.1.6"]
                 [http-kit "2.0.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring/ring-json "0.2.0"]
                 [ring-middleware-format "0.3.2"]]
  :main ^:skip-aot env-server.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
