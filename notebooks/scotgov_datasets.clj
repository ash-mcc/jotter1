;; # Datasets from statistics.gov.scot which might be useful to _od-bods_


;; ## Software set-up
;; Load some helper libraries.
(ns scotgov-datasets
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clj-http.client :as http]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as dapi]
            [nextjournal.clerk :as clerk])
  (:import java.net.URLEncoder
           java.io.ByteArrayInputStream
           java.time.LocalDate
           [java.time.format DateTimeFormatter DateTimeParseException]))


;; ## SPARQL query
;; Define the SPARQL query whcih will retrive metadata about the datasets.
(def sparql "         
PREFIX dcterms: <http://purl.org/dc/terms/>
PREFIX dcat: <http://www.w3.org/ns/dcat#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX folder: <http://publishmydata.com/def/ontology/folder/>

SELECT ?uri ?name ?creator ?publisher ?issued ?modified ?licence ?comment ?theme 
WHERE {
  ?uri rdf:type <http://publishmydata.com/def/dataset#Dataset>.    
  OPTIONAL { ?uri rdfs:label ?name. }
  OPTIONAL { ?uri dcterms:publisher/rdfs:label ?publisher.}
  OPTIONAL { ?uri dcterms:creator/rdfs:label ?creator.}
  OPTIONAL { ?uri dcterms:issued ?issued.}
  OPTIONAL { ?uri dcterms:modified ?modified.}
  OPTIONAL { ?uri dcterms:license ?licence.}
  OPTIONAL { ?uri rdfs:comment ?comment.}
  OPTIONAL { 
    ?uri dcat:theme ?themeUri.
    ?themeUri folder:inTree <http://statistics.gov.scot/def/concept/folders/themes>;
              rdfs:label ?theme.
  }
}
")


;; ## Scot gov's SPARQL engine endpoint
;; Define how-to run a SPARQL query against Scot gov's SPARQL engine.
(defn exec-against-scotgov [sparql]
  (:body (http/post "http://statistics.gov.scot/sparql"
                    {:body    (str "query=" (URLEncoder/encode sparql))
                     :headers {"Accept"       "text/csv"
                               "Content-Type" "application/x-www-form-urlencoded"}
                     :debug   false})))


;; ## Run the SPARQL query
;; Run the SPARQL query, read the result, and store it in a column-oriented datastructure.
(def scotgov-datasets-v1
  (-> sparql
      exec-against-scotgov
      (.getBytes "UTF-8")
      (ByteArrayInputStream.)
      (dapi/->dataset {:file-type :csv
                       :key-fn    keyword})))


;; ## Examine the result

;; Count the rows and columns.
(tc/shape scotgov-datasets-v1)    

;; Get a further description of the data.
;;
;; _Browsing hint: Click on the underlined backets within following the outputs, to expand what is shown._
(tc/info scotgov-datasets-v1)


;; ## Remove multiple 'issued' dates 

;; Count the distinct datasets.
(-> scotgov-datasets-v1 :uri distinct count)

;; Some datasets are represented multiple times because they have multiple `issued` values.
;; Let's take the earliest `issued `value for each dataset.
(def scotgov-datasets-v2 
  (-> scotgov-datasets-v1
      (tc/group-by [:uri :name :creator :publisher :modified :licence :theme])
      (tc/order-by :issued)
      (tc/first)
      (tc/ungroup)))

;; Count the rows and columns of version `v2`.
(tc/shape scotgov-datasets-v2)


;; ## Remove blank 'creator' datasets

;; 3 of the datasets have a `nil` as their `creator`.
(->> (-> scotgov-datasets-v2
         (tc/group-by :creator)
         (tc/aggregate #(-> % :uri count))
         (tc/rename-columns {:$group-name :creator
                             :summary     :dataset-count})
         (tc/rows :as-maps))
     (sort-by :creator)
     clerk/table)

;; Remove those `nil` `creator` datasets
(def scotgov-datasets-v3 (-> scotgov-datasets-v2
                             (tc/drop-missing :creator)))


;; ## Examine our final `v3` version

;; Number of datasets.
(-> scotgov-datasets-v3 :uri count)

;; Number of creators
(-> scotgov-datasets-v3 :creator distinct count)


;; ## Convert into an _od-bods_ oriented format

;; Define a few helper functions.

(defn ->stringify-issued-vals [column]
  (map #(try (-> % (LocalDate/parse DateTimeFormatter/ISO_OFFSET_DATE_TIME) .toString)
             (catch DateTimeParseException _ (str %)))
       column))

(defn ->stringify-modified-vals [column]
  (map #(-> % .toLocalDate .toString)
       column))

(defn ->stringify-licence-vals [column]
  (map #(if (= % "http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/")
          "OGL v3.0"
          %)
       column))

;; Convert into an _od-bods_ oriented format.
(def scotgov-datasets-v4 (-> scotgov-datasets-v3
         (tc/update-columns {:issued   ->stringify-issued-vals
                             :modified ->stringify-modified-vals
                             :licence  ->stringify-licence-vals})
         (tc/rename-columns {:uri      :url
                             :name     :title
                             :creator  :organization
                             :comment  :notes
                             :theme    :category
                             :issued   :date_created
                             :modified :date_updated})
         (tc/drop-columns :publisher)))

;; Display as a table.
(->> (tc/rows scotgov-datasets-v4 :as-maps)
     (sort-by :title)
     clerk/table)

;; Write as CSV into a file.
(let [headers [:title :category :organization :notes :date_created :date_updated :url]
      header-row (map name headers)
      data-rows (->> (tc/rows scotgov-datasets-v4 :as-maps)
                     (map #(map % headers)))
      file (io/file "scotgov-datasets.csv")]
  (io/make-parents file)
  (with-open [wtr (io/writer file)]
    (csv/write-csv wtr (cons header-row data-rows))))