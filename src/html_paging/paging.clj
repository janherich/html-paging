(ns html-paging.paging
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]))

(defn- get-filename [^java.io.File f] (.getName f))

(defn- get-filepath [^java.io.File f] (.getPath f))

(defn- is-menu-page-file?
  "Determines if given filename conforms to menu-page file"
  [menu-page-prefix filename]
  (if (-> (str "\\A" menu-page-prefix "[_-]??[0-9]*.html\\z")
          re-pattern
          (re-find filename))
    true
    false))

(defn- extract-page-number
  "Extracts page number from menu-page file"
  [menu-page-prefix filename]
  (let [[_ num-str] (-> (str "\\A" menu-page-prefix "[_-]??([0-9]*).html\\z")
                        re-pattern
                        (re-find filename))]
    (if (empty? num-str) 0 (Integer. num-str))))

(defn- get-menu-page-files
  "Returns menu page files"
  [path menu-page-prefix]
  (->> (file-seq (io/file path))
       (filter (comp (partial is-menu-page-file? menu-page-prefix)
                     get-filename))))

(defn- select-nodes
  "Extracts nodes matched by given selector from sequence of enlive nodes"
  [selector nodes]
  (html/select nodes selector))

(defn- insert-nodes
  "Returns nodes for with child nodes inserted at child selector"
  [selector nodes child-nodes]
  (html/at nodes selector (html/content child-nodes)))

(defn- file-from-nodes!
  "Creates file with given filename from sequence of enlive nodes"
  [filename nodes]
  (spit filename (apply str (html/emit* nodes))))

(defn- partition-elements
  "Partitions elements into sections according to maximal number of elements
   on one section"
  [max-elements-on-page elements]
  (let [elements-count (count elements)
        whole-elements-count (- elements-count (rem elements-count max-elements-on-page))
        whole-elements-pages (into []
                                   (partition max-elements-on-page
                                              (take whole-elements-count elements)))]
    (if (= whole-elements-count elements-count)
      whole-elements-pages
      (conj whole-elements-pages (drop whole-elements-count elements)))))

(defn- for-files [files fn] (doseq [^java.io.File f files] (fn f)))

(defn- prepare-to-delete!
  "Prepare old files to be delete by renaming them with special suffix"
  [files]
  (for-files files #(.renameTo % (-> (get-filepath %) (str ".old") io/file))))

(defn- restore-old-files!
  "Restore old renamed files be deleting the special suffix from filename"
  [files]
  (for-files (map #(-> (get-filepath %) (str ".old") io/file) files)
             #(.renameTo % (-> (get-filepath %) (string/replace ".old" "") io/file))))

(defn- delete-old-files!
  "Delete old renamed files"
  [files]
  (for-files (map #(-> (get-filepath %) (str ".old") io/file) files) #(.delete %)))

(defn- delete-files!
  "Delete given files"
  [files]
  (for-files files #(.delete %)))

(defn- prepare-nodes
  "Prepare nodes to be written into page file"
  ([node-transform-function page-prefix nodes]
     [(str page-prefix ".html") (node-transform-function nodes)])
  ([node-transform-function page-prefix idx nodes]
     (if (<= idx 1)
       (prepare-nodes node-transform-function page-prefix nodes)
       [(str page-prefix "-" idx ".html") (node-transform-function nodes)])))

(defn- extract-child-nodes
  "Extract child html-nodes from files, and returns them as one sequence"
  [selector files]
  (->> files
       (map (comp (partial select-nodes selector) html/html-resource))
       flatten))

(defn- create-pages
  "Creates pages with indexing"
  [section-selector page-prefix page-nodes nodes]
  (->> nodes
       (map (partial prepare-nodes
                     (partial insert-nodes section-selector page-nodes)
                     page-prefix)
            (iterate inc 1))))

(defn- insert-paging-anchors
  "Inserts paging anchors into page nodes"
  [menu-section-selector menu-current-selector menu-anchor-selector nodes page-anchors]
  (let [menu-current-element (first (html/select nodes menu-current-selector))
        menu-anchor-element (first (html/select nodes menu-anchor-selector))
        filled-menu-elements (map (fn [{:keys [content href]}]
                                    (if href
                                      (-> menu-anchor-element
                                          (assoc :content (list (str content)))
                                          (assoc-in [:attrs :href] href))
                                      (-> menu-current-element
                                          (assoc :content (list (str content))))))
                                  page-anchors)]
    (html/at nodes menu-section-selector (html/do-> (html/remove-attr :style)
                                                    (html/content filled-menu-elements)))))

(defn- generate-paging-anchors
  "Generate page anchors for each page, if there is only one page, sets the menu
   section to be invisible"
  [menu-section-selector insert-paging-fn [[first-page-name first-page-nodes] :as pages]]
  (let [number-of-pages (count pages)]
    (if (> number-of-pages 1)
      (let [numbered-names (map (fn [page-number [page-name]]
                                  [page-number page-name]) (iterate inc 1) pages)
            names->anchors (reduce (fn [acc [number name]]
                                     (assoc acc name (map (fn [[nb nm]]
                                                            (if (= number nb)
                                                              {:content nb}
                                                              {:content nb :href nm}))
                                                          numbered-names)))
                                   {}
                                   numbered-names)]
        (map (fn [[page-name page-nodes]]
               [page-name (insert-paging-fn page-nodes (get names->anchors page-name))])
             pages))
      (list [first-page-name (html/at first-page-nodes
                                      menu-section-selector
                                      (html/set-attr :style "display: none;"))]))))

(defn generate-menu-pages
  "Generate menu pages"
  [path menu-page-prefix max-thumbnails thumbnail-div-selector thumbnail-section-selector
   menu-section-selector menu-current-selector menu-anchor-selector]
  {:pre [(string? path) (string? menu-page-prefix) (integer? max-thumbnails)
         (vector? thumbnail-div-selector) (vector? thumbnail-section-selector)
         (vector? menu-section-selector) (vector? menu-current-selector)
         (vector? menu-anchor-selector)]}
  (let [menu-pages-files (get-menu-page-files path menu-page-prefix)
        menu-page-nodes (-> menu-pages-files first html/html-resource)
        menu-pages (->> menu-pages-files
                        (sort-by (comp (partial extract-page-number menu-page-prefix) get-filename))
                        (extract-child-nodes thumbnail-div-selector)
                        (partition-elements max-thumbnails)
                        (create-pages thumbnail-section-selector menu-page-prefix menu-page-nodes)
                        (generate-paging-anchors menu-section-selector
                                                 (partial insert-paging-anchors
                                                          menu-section-selector
                                                          menu-current-selector
                                                          menu-anchor-selector)))]
    (try
      (prepare-to-delete! menu-pages-files)
      (doseq [[menu-page-name menu-page-nodes] menu-pages]
        (println (str "Creating page: " menu-page-name " at location: " path))
        (file-from-nodes! (str path "/" menu-page-name) menu-page-nodes))
      (delete-old-files! menu-pages-files)
      (catch Exception e (do (delete-files! (get-menu-page-files path menu-page-prefix))
                             (restore-old-files! menu-pages-files)
                             (throw e))))))
