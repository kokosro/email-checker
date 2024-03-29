(defproject emailchecker "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojars.kokos/clj-sockets "0.1.1"]
                 [com.brweber2/clj-dns "0.0.2"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/tools.logging "0.2.4"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 ;; trying aleph .. again
                 [aleph "0.4.2-alpha8"]
                 [gloss "0.2.5"]
                 
                 [com.draines/postal "2.0.2"]]
  :uberjar-name "emailchecker.jar"
  :main ^:skip-aot emailchecker.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
