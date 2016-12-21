(ns clj-pepxml.core
  (:require [clojure.java.io :as io]
            [clojure.data.xml :refer [parse]]
            [clojure.zip :refer [node xml-zip]]
            [clojure.data.zip.xml :refer [xml-> attr xml1-> text attr]]
            [biodb.core :as bdb]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- filter-summary
  [z analysis]
  (->> (filter #(and (#{:analysis_summary} (:tag %))
                     (#{analysis} (:tag (first (:content %)))))
               (:content z))
       (map #(assoc (first (:content %)) :attrs
                    (assoc (:attrs (first (:content %))) :analysis_summary
                           (:attrs %))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; searches
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn msms-run-seq
  [reader]
  (let [get-zip (fn [x tag]
                  (doall (->> (drop-while #(not (#{tag} (:tag %))) (:content x))
                              (take-while #(#{tag} (:tag %))))))]
    (->> (filter #(#{:msms_run_summary} (:tag %)) (:content (parse reader)))
         (map #(lazy-seq
                (cons
                 (assoc % :content
                        (doall
                         (take-while (fn [x] (not (#{:spectrum_query} (:tag x))))
                                     (:content %))))
                 (drop-while (fn [x] (not (#{:spectrum_query} (:tag x))))
                             (:content %))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spectrum accessors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn search-results
  [s]
  (xml-> (xml-zip s) :search_result node))

(defn search-hits
  [sr]
  (xml-> (xml-zip sr) :search_hit node))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; analysis etc
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pepxml-parameters
  ([file] (pepxml-parameters file nil))
  ([file ks]
   (with-open [r (io/reader file)]
     (let [xml (parse r)
           pkeys [:interprophet_summary :peptideprophet_summary
                  :ptmprophet_summary :asapratio_summary
                  :xpressratio_summary :dataset_derivation]
           get-zip (fn [tag]
                     [tag (doall (filter-summary xml tag))])]
       (->> (map get-zip (or (seq (remove (complement (set ks)) pkeys))
                             pkeys))
            (into {}))))))
