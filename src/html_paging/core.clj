(ns html-paging.core
  (:require [clojure.string :as string]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]
            [html-paging.paging :as pg])
  (:gen-class))

(def cli-options
  [["-p" "--path PATH" "Path"
    :default "."]
   ["-c" "--count COUNT" "Maximal count of thumbnail elements on page"
    :default 3
    :parse-fn #(Integer/parseInt %)
    :validate [#(> % 0) "Must be number greater then 0"]]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["This is program to perform simple paging actions on html files."
        ""
        "Usage: html-paging [options] parameters"
        ""
        "Options:"
        options-summary
        ""
        "Parameters:"
        "  menu page prefix"
        "  thumbnail section selector"
        "  thumbnail div selector"
        "  menu section selector"
        "  menu current selector"
        "  menu anchor selector"
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{[menu-page-prefix thumbnail-section-selector
          thumbnail-div-selector menu-section-selector
          menu-current-selector menu-anchor-selector
          :as arguments] :arguments
          errors :errors summary :summary options :options}
        (parse-opts args cli-options)]
    (cond
     (:help options) (exit 0 (usage summary))
     (not= (count arguments) 6) (exit 1 (usage summary))
     errors (exit 1 (error-msg errors)))
    (do (pg/generate-menu-pages (:path options)
                                menu-page-prefix
                                (:count options)
                                (edn/read-string thumbnail-div-selector)
                                (edn/read-string thumbnail-section-selector)
                                (edn/read-string menu-section-selector)
                                (edn/read-string menu-current-selector)
                                (edn/read-string menu-anchor-selector))
        (exit 0 "Program finished."))))
