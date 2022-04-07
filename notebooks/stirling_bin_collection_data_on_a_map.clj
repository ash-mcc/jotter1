;; # Stirling's bin collection data on a map

;; ## Introduction
 
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

;; ## Software libraries

;; Load the helper libraries.
^{:nextjournal.clerk/toc true
  ::clerk/visibility :fold}
(ns stirling_bin_collection_data_on_a_map
  (:require [clojure.string :as str]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as tds]
            [clj-http.client :as http]
            [nextjournal.clerk :as clerk])
  (:import java.net.URLEncoder
           java.io.ByteArrayInputStream))

;; ## Read the bin collections data

;; Read the CSV file from Stirling council's Open Data website.
^{::clerk/visibility :fold}
(def bin-collections 
  (tc/dataset "https://data.stirling.gov.uk/dataset/70614cbb-ff9e-4ef7-8e18-486017a368d6/resource/8807c713-46cb-4100-80c5-de8a457b0f8e/download/20220207-domestic-waste-collections-jan-2021-to-dec-2021.csv"))

;; Count the rows and columns.
^{::clerk/visibility :fold}
(tc/shape bin-collections)

;; Get a further description of the columns.
^{::clerk/visibility :fold}
(tc/info bin-collections)

;; ## Read the map areas data

;; Each map area will have a name, a geographic boundary and a population. 

;; Define the SPARQL query to be used against the Scottish government SPARQL endpoint.
^{::clerk/visibility :fold}
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
SELECT ?datazone ?population # ?geometry <- commented out for now, problem with its encoding
WHERE {
  ?dzUri stat-geo:status \"Live\";
         stat-geo:parentcode/rdfs:label \"Stirling\";
         stat-ent:code/rdfs:label \"Intermediate Zones\"; # These provide the best fit for the bin collection data
         rdfs:label ?datazone;
         geosparql:hasGeometry/geosparql:asWKT ?geometry.
  ?popUri qb:dataSet <http://statistics.gov.scot/data/population-estimates-current-geographic-boundaries>;
          pdmx:refArea ?dzUri;
          pdmx:refPeriod/rdfs:label \"2018\"; # The latest year in which population exists at this resolution 
          dim:age <http://statistics.gov.scot/def/concept/age/all>;
          dim:sex <http://statistics.gov.scot/def/concept/sex/all>;
          mp:count ?population.
}
")

;; Define how-to run a SPARQL query against the Scottish government SPARQL endpoint.
^{::clerk/visibility :fold}
(defn exec-against-scotgov [sparql]
  (:body (http/post "http://statistics.gov.scot/sparql"
                    {:body    (str "query=" (URLEncoder/encode sparql))
                     :headers {"Accept"       "text/csv"
                               "Content-Type" "application/x-www-form-urlencoded"}
                     :debug   false})))

;; Run the SPARQL query and read the result.
^{::clerk/visibility :fold}
(def map-areas
  (-> sparql
      exec-against-scotgov
      (.getBytes "UTF-8")
      (ByteArrayInputStream.)
      (tds/->dataset {:file-type :csv
                      :key-fn    keyword})))


;; ## Link bin collections to map areas

;; Narrow and parse the data:
;; * Drop the `Internal Transfer` rows.
;; * Parse `:places` from the `Route` column.
;; * Parse `:yyyyMMdd` from the `Date` column.

(def text-substitutions 
  ;; NB no specific ordering
  {"and "                   ","
   "&"                      ","
   "\n"                      ","
   ":"                      ""
   "."                      ""
   "narrow access areas"    ""
   "boxes"                  ""
   "Boxes"                  ""
   "bins"                   ""
   "Glass"                  ""
   "Glasss"                 ""
   "Green"                  ""
   "Blue"                   ""
   "Brown"                  ""
   "Grey"                   ""
   "Aberfolye"              "Aberfoyle"
   "Dumblane"               "Dunblane"
   "Falllin"                "Fallin"
   "Kings Park"             "King's Park"
   "Kippena nd Gargunnock"  "Kippen , Gargunnock"
   "Callander Causewayhead" "Callander , Causewayhead"
   "Crainlarich"            "Crianlarich"
   "Balqhidder"             "Balquhidder"
   "Port of Mentieth"       "Port of Menteith"
   "St Nininas"             "St Ninians"
   "Torbex"                 "Torbrex"
   "Balamaha"               "Balmaha"
   "Broombridge"            "Broomridge"
   })

(defn apply-text-substitutions
  [^String route]
  (loop [todo        text-substitutions 
         accumulator route]
    (if (empty? todo)
      accumulator
      (let [[match substitution] (first todo)]
        (recur (rest todo)
               (str/replace accumulator match substitution))))))

(defn parse-components 
  [^String route]
  (->> (str/split route #",")
       (map str/trim)))

(defn datazones-whose-names-wholely-contain
  [^String route-component]
  (filter #(str/includes? % route-component) (:datazone map-areas)))

(def lookup-table
  {"Cultenhove"                         ["Borestone"]
   "Loch Katrine"                       ["Highland"]
   "Thornhill"                          ["Carse of Stirling"]
   "Blanefield"                         ["Blane Valley"]
   "Town centre"                        ["City Centre"]
   "Mugdock"                            ["Blane Valley"]
   "Balquhidder"                        ["Highland"]
   "Aberfoyle"                          ["Carse of Stirling"]
   "Gartmore"                           ["Blane Valley"]
   "Dumgoyne"                           ["Blane Valley"]
   "Buchlyvie"                          ["Kippen and Fintry"]
   "Milton of Buchanan"                 ["Blane Valley"]
   "Dalmary"                            ["Balfron and Drymen"]
   "Lochearnhead"                       ["Highland"]
   "Wallace Park"                       ["Bannockburn"]
   "Strathblane"                        ["Blane Valley"]
   "Crianlarich"                        ["Highland"]
   "Croftamie"                          ["Blane Valley"]
   "Deanston"                           ["Carse of Stirling"]
   "Inverarnan"                         ["Highland"]
   "Blairlogie"                         ["Forth"]
   "Carron Valley"                      ["Plean and Rural SE"]
   "Carronbridge"                       ["Plean and Rural SE"]
   "Ruskie"                             ["Carse of Stirling"]
   "Blairdrummond"                      ["Carse of Stirling"]
   "Doune"                              ["Carse of Stirling"]
   "Boquhan"                            ["Balfron and Drymen"]
   "Kilmahog"                           ["Callander and Trossachs"]
   "Kinbuck"                            ["Dunblane East"]
   "Rowardennan"                        ["Highland"]
   "Port of Menteith"                   ["Carse of Stirling"]
   "Blanefield s"                       ["Blane Valley"]
   "Milton"                             ["Carse of Stirling"]
   "Inversnaid"                         ["Highland"]
   "Kinlochard"                         ["Highland"]
   "Balmaha"                            ["Blane Valley"]
   "Gartness"                           ["Blane Valley"]
   "Tyndrum"                            ["Highland"]
   "Balfron Station"                    ["Balfron and Drymen"]
   "Killearn"                           ["Blane Valley"]
   "Brig O'Turk"                        ["Callander and Trossachs"]
   "Gargunnock"                         ["Kippen and Fintry"]
   "Stockiemuir"                        ["Blane Valley"]
   "City Centre recycling  bring sites" ["City Centre"]
   "Killin"                             ["Highland"]
   "Bridge of Allan East"               ["Bridge of Allan and University"]
   "Strathyre"                          ["Highland"]
   "St Ninians"                         ["Borestone"]
   "Throsk"                             ["Plean and Rural SE"]
   "Springkerse"                        ["Braehead"]
   "Ashfield"                           ["Dunblane East"]
   "Chartershall"                       ["Borestone"]
   "Arnprior"                           ["Kippen and Fintry"]
   "Whins of Milton"                    ["Borestone"]
   "Riverside"                          ["Forth"]
   "Cambuskenneth"                      ["Forth"]
   "Sherrifmuir"                        ["Dunblane East"]
   "Dunblane North"                     ["Dunblane East"]
   "Blairhoyle"                         ["Carse of Stirling"]}
  )

(defn ->datazones
  [^String route-component]
  (if-let [datazones (not-empty (datazones-whose-names-wholely-contain route-component))]
    datazones
    (if-let [datazones (not-empty (get lookup-table route-component))]
      datazones
      nil)))


(defn date->yyyyMMdd 
  [^String date]
  (let [[_ dd MM yyyy] (re-find #"(\d{2})/(\d{2})/(\d{4})" date)]
    (str yyyy "-" MM "-" dd)))

(def tmp 
  (-> bin-collections
      (tc/drop-rows (fn [row] (= "Internal Stirling Council Transfer" (get row "Route"))))
      (tc/map-columns :route-components ["Route"] (fn [route] (-> route 
                                                                  apply-text-substitutions 
                                                                  parse-components)))
      (tc/map-columns :yyyyMMdd ["Date"] (fn [date] (parse-yyyyMMdd date)))))

(def tmp2
  (->> tmp
       :route-components
       flatten
       distinct
       (remove (fn [route-component]
                 (not-empty (datazones-whose-names-wholely-contain route-component))))
       sort))

(prn tmp2)

(count tmp2)

(prn (into {} (map #(vector % []) tmp2)))

(:datazone map-areas)

(if-let [v []]
  :a
  :b)