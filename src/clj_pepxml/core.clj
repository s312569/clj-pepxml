(ns clj-pepxml.core
  (:require [clojure.java.io :as io]
            [clojure.data.xml :refer [parse]]
            [clojure.zip :refer [node xml-zip]]
            [clojure.data.zip.xml :refer [xml-> attr xml1-> text attr
                                          attr=]]
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

(defn- parse-numbers
  [m f v]
  (reduce #(update-in %1 [%2] f) m v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; searches
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- helper
  [[f & r]]
  (when f
    (cons f (lazy-seq (helper r)))))

(defn msms-run-seq
  [reader]
  (->> (filter #(#{:msms_run_summary} (:tag %)) (:content (parse reader)))
       (map #(lazy-cat
              (list
               (assoc % :content
                      (doall
                       (take-while (fn [x] (not (#{:spectrum_query} (:tag x))))
                                   (:content %)))))
              (helper (drop-while (fn [x] (not (#{:spectrum_query} (:tag x))))
                                  (:content %)))))))
                
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; msms run summary accessors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spectrum-path
  [rs]
  "Returns the spectrum file path from a msms run summary
  element. Currently only works for X! Tandem."
  (xml1-> (xml-zip rs) :search_summary :parameter (attr= :name "spectrum, path")
          (attr :value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; spectrum accessors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- sr-zip
  [s]
  (xml-> (xml-zip s) :search_result))

(defn- sh-zip
  [s]
  (xml-> (xml-zip s) :search_result :search_hit))

(defn- an-zip
  [s]
  (xml-> (xml-zip s) :search_result :search_hit :analysis_result))

(defn- ip-zip
  [s]
  (xml-> (xml-zip s) :search_result :search_hit :analysis_result
         (attr= :analysis "interprophet") :interprophet_result))

(defn search-results
  [s]
  (->> (sr-zip s) (map node)))

(defn search-hits
  [s]
  (->> (sh-zip s) (map node)))

(defn spectrum-accession
  [s]
  "Returns the spectrum accession from a spectrum query."
  (-> s :attrs :spectrum))

(defn scan-start-end
  [s]
  "Returns a vector containing the start and end scan of a spectrum in
  a spectrum query."
  (->> [(-> s :attrs :start_scan) (-> s :attrs :end_scan)]
       (map #(Integer/parseInt %))
       vec))

(defn peptide-hit-info
  [s]
  "Returns a list of hashes containing peptide hit information."
  (let [a (->> (sh-zip s) (map node) (map :attrs))
        i #(if-not (nil? %)
             (Integer/parseInt %))
        f #(if-not (nil? %)
             (Float/parseFloat %))]
    (map #(-> (parse-numbers % i [:hit_rank :num_tot_proteins :num_matched_ions
                                  :total_num_ions :num_tol_term
                                  :num_missed_cleavages :num_matched_peptides
                                  :is_rejected])
              (parse-numbers f [:calc_neutral_pep_mass :massdiff :calc_pI]))
         a)))

(defn hit-protein-accession
  [s]
  "Returns a list of protein accessions in hit."
  (map :protein (peptide-hit-info s)))

(defn interprophet-prob
  [s]
  "Returns the probablity scores from any interprophet results."
  (->> (ip-zip s)
       (map #(xml1-> % (attr :probability)))
       (map #(Float/parseFloat %))))

(defn hyperscore
  [s]
  "Returns the hyperscores when X! Tandem analysis was performed."
  (->> (sh-zip s)
       (map #(xml1-> % :search_score (attr= :name "hyperscore") (attr :value)))
       (map #(Float/parseFloat %))))

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
