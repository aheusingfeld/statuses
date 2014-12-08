{:title              "innoQ Status Updates"
 :database-path      "data/db.json"
 :save-interval      2
 :host               "localhost"
 :http-port          8080
 :external-url       "http://localhost:8080"
 :external-url-path  "/statuses"
 :run-mode           :dev
 ; {username} is replaced with the username
 :avatar-url                 "https://testldap.innoq.com/liqid/users/{username}/avatar/32x32"
 ;:avatar-url    "http://assets.github.com/images/gravatars/gravatar-user-420.png"
 :profile-url-prefix         "https://testldap.innoq.com/liqid/users/"
 :entry {
         :min-length 1
         :max-length 140}
 ; set the following parameters to enable openID connect authentication
 :oauth-server-authorize-uri "https://testldap.innoq.com/openid/authorize"
 :oauth-server-token-uri     "https://testldap.innoq.com/openid/token"
 :oauth-server-userinfo-uri  "https://testldap.innoq.com/openid/userinfo"
 :oauth-client-id    "08f74afd-aa5a-4fda-b506-56955ed0089a"
 :oauth-client-secret    "ANPgFiTvF9-1FoNrOMwCls36CEIYC1to6J4vjQJuFwKwCGtuRnvbx1zFHmqCuKG0fFZPfOdd9GdGF3Qd67p87wc"
 ; registration-access-token ""
 }
