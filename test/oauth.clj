 (ns oauth
   (:use clojure.test
         [statuses.routing :only [extract-access-token]]))

 (deftest extract-access-token-from-body
   (is
     (=
       (extract-access-token {:body "{\"access_token\":\"test.A-JEKLslDlCv5uO0SmH_TWB9SHxLuk9IqITcWk1ZvA\"}"})
       "test.A-JEKLslDlCv5uO0SmH_TWB9SHxLuk9IqITcWk1ZvA")))
