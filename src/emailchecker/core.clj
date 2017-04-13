(ns emailchecker.core
  (:require [emailchecker.email :as ec])
  (:gen-class))


(defn -main
  [csv-filename]
  (future (ec/check-csv csv-filename)))
