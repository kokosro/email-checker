(ns emailchecker.core
  (:require [clj-sockets.core :as cs]
            [clj-dns.core :as dns]
            [clojure.string :as s]
            [clojure.data.csv :as csv]
         	[clojure.java.io :as io]
          [manifold.deferred :as d]
          [manifold.stream :as ms]
          [byte-streams :as bs]
          [aleph.tcp :as tcp]
          [clojure.tools.logging :as log]
          [gloss.core :as gloss]
    	[gloss.io :as gio])
  (:import (org.xbill.DNS Type))
  (:gen-class))


(def protocol
  (gloss/compile-frame
    (gloss/string :utf-8)
    #(do (log/info "RAW SNT: #" (s/trim %) "#")
       (str (bs/to-string %) "\r\n"))
    #(do (log/info "RAW RCV: #" (s/trim %) "#")
       (s/trim (bs/to-string %)))))

(defn wrap-duplex-stream
  [protocol s]
  (let [out (ms/stream)]
    (ms/connect
      (ms/map #(gio/encode protocol %) out)
      s)
    (ms/splice
      out
      (gio/decode-stream s protocol))))


(defn client
  [host port & {:keys [ssl? insecure?]
                :or {ssl? false
                     insecure? true}}]
  (d/chain (tcp/client {:host host, :port port :ssl? ssl? :insecure? insecure?})
    #(wrap-duplex-stream protocol %)))

;(import 'org.xbill.DNS.Type)
(defn get-mx [domain]
  (let [mxs (:answers (dns/dns-lookup domain Type/MX))
        _ (log/info "all MXs for " domain (map #(-> % .getTarget .toString)
                                               mxs))]
    (when-not (nil? (last mxs))
      (apply str (butlast (.toString (.getTarget (rand-nth mxs))))))))

(defn parse-int [s]
  (Integer. (re-find  #"\d+" s )))

(defn get-status
  [response]
  (if (nil? response)
    10000
    (let [parts (s/split response #"[^a-zA-Z0-9]")
        code (parse-int (first parts))]
    (if (= 0 code)
      1000
      code))))


(defn get-error-message
  [status]
  (if (= 10000 status)
    "noresponsefromserver"
    (if (= 1000 status)
    "timeout"
    (if (> status 500)
      "notfoundonserver"
      "allgood"))))

(defn error?
  [response]
  (let [is-error? (or (nil? response)
      				(> (get-status response) 500))]
    {:error? is-error?
     :message (get-error-message (get-status response))}))

(defn valid-format?
  "simply checks if there are 2 parts for an email account @ domain"
  [email]
  (= 2 (count (s/split email #"@"))))

(defn get-next-response
  [socket timeout]
  (loop
    [rsp (s/trim (or @(ms/try-take! socket timeout)
             "1000"))]
    (if-not (= "" rsp)
      rsp
      (recur (s/trim (or @(ms/try-take! socket timeout)
             "1000"))))))

(defn get-domain
  [email]
  (when (valid-format? email)
    (last (s/split email #"@"))))

(defn get-acc-part
  [email]
  (when (valid-format? email)
    (first (s/split email #"@"))))

(defn check
  [email & helo]
  (if (valid-format? email)
    (let [host (get-mx (get-domain email))
          port 25
         ; port 587
          timeout 60000 ;;15 seconds for any connection is enough to say .. no more waiting please, just continue
          socket (if-not (nil? host)
                   (try @(client host port :ssl? false)
                   (catch Exception e (do
                                        (log/info email host ":" port " ERROR " e)
                                        nil)))
                   (do (log/info email "ERROR: NO MX for " (get-domain email))
                     nil))]
      (if-not (nil? socket)
        (let [f1  (get-next-response socket timeout)
              validity (reduce (fn [ok? write-this]
                          (let [_ 1]
                            (if-not (:error? ok?)
                            (let [message (write-this email)
                                  _ (ms/put! socket message)
                                  response (get-next-response socket timeout)
                                  error-check (error? response)]
                              (assoc (assoc ok?
                                       :error? (and (:error? ok?)
                                                    (:error? error-check)))
                                :message (:message error-check)))
                            ok?)))
                        (error? f1)
                        [#(do (log/info (format "checking %s" %))
                            (format "EHLO %s" (or (first helo) "localhost")))
                         #(format "MAIL FROM:<%s>" %)
                         ;#(format "RCPT TO:<%s>" %)
                         ])
              ;_ (ms/put! socket "QUIT")
              _ (get-next-response socket timeout)
              _ (.close socket)]
        validity)
        {:error? true
         :message "MXmissing"}))
    {:error? true
     :message "invalidformat"}))


(defn exists? 
  [email & helo]
  (not (:error? (check email (first helo)))))

(defn check-csv
  [file-name]
  (future 
    (with-open [in-file (io/reader file-name)]
    (with-open [out-file (io/writer (str file-name ".out5.csv"))]
      (csv/write-csv out-file (reduce
      (fn [checked-emails [email]]
        (let [check-rsp (check email)
              line (if (:error? check-rsp)
                                [email "NU" (:message check-rsp)]
                                [email "DA" "allgood"])
              _ (log/info (format "VALID? %s - %s - %s" email (second line) (last line)))]
          (conj checked-emails line)))
      []
      (csv/read-csv in-file)))))
    (log/info (format "checked emails results saved in %s" (str file-name ".out5.csv")))))







(defn -main
  [csv-filename]
  (log/info (format "starting email check on %s" csv-filename))
  (future (check-csv csv-filename)))
