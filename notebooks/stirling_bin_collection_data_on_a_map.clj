;; # Stirling's bin collection data on a map

;; ## Intro
 
;; Stirling council has published Open Data about it _bin collection_.
;; Its data for 2021 includes town/area names.
;; Our aim is to _approximately_ plot this data on a map.
;;
;; We say _approximately_ because transfering the data to a map is not simple and unamibiguous. 
;; For example, the data may say that a certain weight of binned material was collected in 
;; "`Aberfoyle, Drymen, Balmaha, Croftamie, Balfron & Fintry narrow access areas`".
;; In a case like this, we will aportion the weight collected,
;; between the individual areas, based on relative populations of those areas.
;; But this is approximate.

;; Load the helper libraries.
^{:nextjournal.clerk/toc true}
(ns stirling_bin_collection_data_on_a_map
  (:require [clojure.string :as str]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as tds]
            [clj-http.client :as http]
            [nextjournal.clerk :as clerk])
  (:import java.net.URLEncoder
           java.io.ByteArrayInputStream))

;; ## Read the bin collection data

;; Read the CSV file from Stirling council's Open Data website.
(def collection-v0 
  (tc/dataset "https://data.stirling.gov.uk/dataset/70614cbb-ff9e-4ef7-8e18-486017a368d6/resource/8807c713-46cb-4100-80c5-de8a457b0f8e/download/20220207-domestic-waste-collections-jan-2021-to-dec-2021.csv"))

;; Count the rows and columns.
(tc/shape collection-v0)

;; Get a further description of the columns.
(tc/info collection-v0)

;; ## Read the map data

;; Define the SPARQL query to be used against the Scottish government SPARQL endpoint.
(def sparql "
PREFIX qb: <http://purl.org/linked-data/cube#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX sdmx: <http://purl.org/linked-data/sdmx/2009/concept#>
PREFIX pdmx: <http://purl.org/linked-data/sdmx/2009/dimension#>
PREFIX dim: <http://statistics.gov.scot/def/dimension/>
PREFIX mp: <http://statistics.gov.scot/def/measure-properties/>
PREFIX stat-ent: <http://statistics.data.gov.uk/def/statistical-entity#>
PREFIX ent-id: <http://statistics.gov.scot/id/statistical-entity/>
PREFIX stat-geo: <http://statistics.data.gov.uk/def/statistical-geography#>
PREFIX geo-id: <http://statistics.gov.scot/id/statistical-geography/>
PREFIX geosparql: <http://www.opengis.net/ont/geosparql#>
SELECT ?datazone ?population ?geometry
WHERE {
  ?dzUri stat-geo:status \"Live\";
         stat-geo:parentcode/rdfs:label \"Stirling\";
         stat-ent:code/rdfs:label \"Intermediate Zones\"; # These provide the best fit for the bin collection data
         rdfs:label ?datazone;
         geosparql:hasGeometry ?geometry.
  ?popUri qb:dataSet <http://statistics.gov.scot/data/population-estimates-current-geographic-boundaries>;
          pdmx:refArea ?dzUri;
          pdmx:refPeriod/rdfs:label \"2018\"; # The latest year in which population exists at this resolution 
          dim:age <http://statistics.gov.scot/def/concept/age/all>;
          dim:sex <http://statistics.gov.scot/def/concept/sex/all>;
          mp:count ?population.
}
")

;; Define how-to run a SPARQL query against the Scottish government SPARQL endpoint.
(defn exec-against-scotgov [sparql]
  (:body (http/post "http://statistics.gov.scot/sparql"
                    {:body    (str "query=" (URLEncoder/encode sparql))
                     :headers {"Accept"       "text/csv"
                               "Content-Type" "application/x-www-form-urlencoded"}
                     :debug   false})))

;; Run the SPARQL query, read the result, and store it in a column-oriented datastructure.
(def datazone-v0
  (-> sparql
      exec-against-scotgov
      (.getBytes "UTF-8")
      (ByteArrayInputStream.)
      (tds/->dataset {:file-type :csv
                      :key-fn    keyword})))


;; ## TODO narrow, parse, map the bin collecton data

;; Narrow and parse the data:
;; * Drop the `Internal Transfer` rows.
;; * Parse `:places` from the `Route` column.
;; * Parse `:yyyyMMdd` from the `Date` column.

(def route-text-substitutions
  {"and "                ","
   "&"                   ","
   "narrow access areas" ""
   ": Glass boxes."      ""
   ": Green bins."       ""
   ": Blue bins."        ""
   ": Grey bins."        ""})

(defn apply-route-text-substitutions
  [route-text]
  (loop [todo route-text-substitutions
         accumulator route-text]
    (if (empty? todo)
      accumulator
      (let [[match substitution] (first todo)]
        (recur (rest todo)
               (str/replace accumulator match substitution))))))

;;TODO route-parts -> data-zones

(defn parse-named-places 
  [route-text]
  (->> (str/split route-text #",")
       (map str/trim)))

(defn parse-yyyyMMdd 
  [date-text]
  (let [[_ dd MM yyyy] (re-find #"(\d{2})/(\d{2})/(\d{4})" date-text)]
    (str yyyy "-" MM "-" dd)))

(def collection-v1 
  (-> collection-v0
      (tc/drop-rows (fn [row] (= "Internal Stirling Council Transfer" (get row "Route"))))
      (tc/map-columns :places ["Route"] (fn [route-text] (-> route-text 
                                                             apply-route-text-substitutions 
                                                             parse-named-places)))
      (tc/map-columns :yyyyMMdd ["Date"] (fn [date-text] (parse-yyyyMMdd date-text)))))

(-> collection-v1
    :named-places
    flatten
    distinct
    count)


