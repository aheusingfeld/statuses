(ns statuses.configuration)

(def default-config
  {:title              "Status Updates"
   :database-path      "data/db.json"
   :save-interval      1
   :host               "localhost"
   :http-port          8080
   :external-url       "http://localhost:8080"
   :external-url-path  "/statuses"
   :run-mode           :dev
   ; {username} is replaced with the username
   :avatar-url         "https://example.com/ldap/users/{username}/avatar/32x32"
   :profile-url-prefix "https://example.com/ldap/users/"
   :entry {
           :min-length 1
           :max-length 140}
   ; set the following parameters to enable openID connect authentication
   :oauth-server-authorize-uri nil
   :oauth-server-token-uri     nil
   :oauth-server-userinfo-uri  nil
   :oauth-client-id            nil
   :oauth-client-secret        nil
   })

(def config-holder (atom default-config))

(defn config
  ([] @config-holder)
  ([key] (get @config-holder key)))

(defn init! [path]
  (if path
    (try
      (println "Initializing configuration from" path ":")
      (swap! config-holder merge (read-string (slurp path)))
      (catch java.io.FileNotFoundException e
        (println "Configuration not found, exiting.")
        (System/exit -1)))
    (println "Using default configuration."))
  (try
    (println "Initializing commit revision from" path ":")
    (swap! config-holder merge {:version (slurp "headrev.txt")})
    (println "Version is" (config :version))
    (catch java.io.FileNotFoundException e
      (println "Version not found, continuing anyway"))))

