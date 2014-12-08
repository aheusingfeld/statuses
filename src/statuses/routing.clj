(ns statuses.routing
  (:require [compojure.core :refer [DELETE GET POST defroutes]]
            [compojure.route :refer [not-found]]
            [compojure.handler :as handler]
            [ring.util.response :refer [redirect response]]
            [statuses.backend.core :as core]
            [statuses.backend.json :as json]
            [statuses.backend.persistence :refer [db]]
            [statuses.routes :as route]
            [statuses.views.atom :as atom]
            [statuses.views.info :as info-view]
            [statuses.views.main :refer [list-page reply-form]]
            [statuses.views.too-long :as too-long-view]
            [cheshire.core :as cjson]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri get-access-token-from-params]]
            [clj-http.client :as http]
            [statuses.views.layout :as layout]))

(defn user [request]
  (or (get-in request [:session :cemerick.friend/identity :current :profile :sub])
      (get-in request [:headers "remote_user"])
      "guest"))

(defn parse-num [s default]
  (if (nil? s) default (read-string s)))

(defn base-uri [request]
  (str
    (name (or (get-in request [:headers "x-forwarded-proto"]) (:scheme request)))
    "://"
    (get-in request [:headers "host"])))

(defn content-type
  [type body]
  (assoc-in {:body body} [:headers "content-type"] type))

(defn build-query-string
  [m]
  (clojure.string/join "&" (map (fn [[key val]] (str (name key) "=" val)) m)))

(defn next-uri [params request]
  (when (< (:offset params) (core/get-count @db))
    (str (base-uri request) (:uri request) "?" (build-query-string params))))

(defmacro with-etag
  "Ensures body is only evaluated if etag doesn't match. Try to do this in Java, suckers."
  [request etag & body]
  `(let [last-etag# (get-in ~request [:headers "if-none-match"])
         etag-str# (str ~etag)]
     (if (= etag-str# last-etag#)
       {:location (:uri ~request), :status 304, :body ""}
       (assoc-in ~@body [:headers "etag"] etag-str#))))

(defn updates-page [params request]
  (let [next (next-uri (update-in params [:offset] (partial + (:limit params))) request)
        {:keys [limit offset author query format]} params]
    (with-etag request (:time (first (core/get-latest @db 1 offset author query)))
               (let [items (core/label-updates :can-delete?
                                               (partial core/can-delete? @db (user request))
                                               (core/get-latest @db limit offset author query))]
                 (cond
                   (= format "json") (content-type
                                       "application/json"
                                       (json/as-json {:items items, :next next}))
                   (= format "atom") (content-type
                                       "application/atom+xml;charset=utf-8"
                                       (atom/render-atom items
                                                         (str (base-uri request) "/statuses")
                                                         (str (base-uri request)
                                                              "/statuses/updates?"
                                                              (:query-string request))))
                   :else (content-type
                           "text/html;charset=utf-8"
                           (list-page items next (user request) nil)))))))

(defn new-update
  "Handles the request to add a new update. Checks whether the post values 'entry-text' or
  'reply-text' have a valid length and if so, creates the new update."
  [{:keys [form-params] :as request}]
  (let [{:strs [entry-text reply-text reply-to]} form-params
        field-value (or entry-text reply-text "")
        length (.length field-value)]
    (if (core/valid-status-length? length)
      (do (swap! db core/add-update (user request) field-value (parse-num reply-to nil))
          (redirect (route/updates-path)))
      (redirect (route/too-long-path length)))))

(defn keyworded
  "Builds a map with keyword keys from one with strings as keys"
  [m]
  (zipmap (map keyword (keys m)) (vals m)))

(defn transform-params [m defaults]
  (reduce (fn [result [key default]]
            (update-in result [key] #(parse-num % default)))
          (keyworded m)
          defaults))

(defn handle-list-view [request]
  (updates-page (transform-params (:params request) {:limit 25 :offset 0}) request))

(defn delete-entry
  "Deletes an entry if the current user is the author of the entry"
  [id request]
  (if-let [item (core/get-update @db (Integer/parseInt id))]
    (if-not (= (user request) (:author item))
      (response (str "Delete failed as you are not " (:author item)))
      (do (swap! db core/remove-update (Integer/parseInt id))
          (redirect (route/updates-path))))))

(defn page
  "Returns a listing with either the conversation of the specified item or just the item"
  [id request]
  (when-let [item (core/get-update @db (Integer/parseInt id))]
    (let [items (core/get-conversation @db (:conversation item))]
      (list-page (if (empty? items) (list item) items) nil (user request) (:id item)))))

(defn conversation
  "XXX: Only kept for backwards compatibility to not break existing links to /statuses/conversation/123"
  [id request]
  (list-page (core/get-conversation @db (Integer/parseInt id)) nil (user request) nil))

(defn info [request]
  (info-view/render-html (user request) request))

(defn too-long [length request]
  (too-long-view/render-html (user request) length))

(defn replyform
  "Returns a basic HTML form to reply to a certain post."
  [id request]
  (if-let [item (core/get-update @db (Integer/parseInt id))]
    (reply-form (:id item) (:author item))))

(defn render-session-page [request]
  (let [count (:count (:session request) 0)
        session (assoc (:session request) :count (inc count))]
    (layout/default
      "Session page"
      (user request)
      (str "<p>The current session: <pre style=\"text-align:left\">" (cjson/generate-string session {:pretty true}) "</pre></p>"))))

(defroutes app-routes
           (GET    "/"                                              []             (redirect (route/base-path)))
           (GET    route/base-template                              []             (redirect (route/updates-path)))
           (GET    route/updates-template                           []             handle-list-view)
           (POST   route/updates-template                           []             new-update)
           (GET    [route/update-template, :id #"[0-9]+"]           [id :as r]     (page id r))
           (DELETE [route/update-template, :id #"[0-9]+"]           [id :as r]     (delete-entry id r))
           (GET    [route/update-replyform-template, :id #"[0-9]+"] [id :as r]     (replyform id r))
           (GET    route/conversation-template                      [id :as r]     (conversation id r))
           (GET    route/info-template                              []             info)
           (GET    route/too-long-template                          [length :as r] (too-long length r))
           (GET  "/statuses/session"                                []             render-session-page)
           (GET    route/logout-template                            []             (friend/logout* (redirect (route/updates-path))))
           (not-found "Not Found"))






(def config-auth {:roles #{::user ::admin}})

(defn client-config
  [current-config]
  {:client-id (:oauth-client-id current-config)
   :client-secret (:oauth-client-secret current-config)
   :auth-uri (:oauth-server-authorize-uri current-config)
   :token-uri (:oauth-server-token-uri current-config)
   ;; TODO get friend-oauth2 to support :context, :path-info
   :callback {:domain (:external-url current-config) :path (str (:external-url-path current-config) "/oauth2callback")}})

(defn uri-config
  [client-config]
  {:authentication-uri {:url (:auth-uri client-config)
                        :query {:client_id (:client-id client-config)
                                :redirect_uri (format-config-uri client-config)
                                :response_type "code"}}
   :access-token-uri {:url (:token-uri client-config)
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :grant_type "authorization_code"
                              :redirect_uri (format-config-uri client-config)}}})

(defn get-user-profile
  "Call the OpenID provider to retrieve the profile information for the current authenticated user."
  [access-token]
  (let [url (route/user-profile-path access-token)
        response (http/get url {:accept :json})
        profile (cjson/parse-string (:body response) true)]
    profile))

(defn retrieve-user-profile
  "Extracts the access-token to call the user-profile service which returns the user's profile data"
  [creds]
  (let [token (:access-token creds)
        profile (get-user-profile token)]
    {:identity {:token token :profile profile}}))

(defn extract-access-token
  "Alternate function to read the access_token from the HTTP body"
  [request]
  (-> (:body request) cjson/parse-string (get "access_token")))

(defn site
  [current-config]
  "Entry function to be called on server startup"
  (handler/site
    (friend/authenticate
      app-routes
      {:allow-anon?   false
       :login-uri     "/statuses/login"
       :workflows     [(oauth2/workflow
                         {:client-config        (client-config current-config)
                          :uri-config           (uri-config (client-config current-config))
                          ; is called to extract the access_token. This is the chance to retrieve the user profile
                          :credential-fn retrieve-user-profile
                          ; the access_token is returned by the openID server in the HTTP request body after 'access-token-uri' (see above) has been called
                          :access-token-parsefn extract-access-token
                          ; this is called if authorization fails
                          :auth-error-fn render-session-page
                          :config-auth          config-auth})]})))
