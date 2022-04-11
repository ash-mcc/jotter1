;; # Stirling's bin collection quantities per DataZone

;; ## Introduction
 
;; Stirling council has published Open Data about its _bin collections_.
;; Its data for 2021 includes town/area names.
;; Our aim is to _approximately_ map this data onto DataZones.
;;
;; DataZones are well defined geographic areas that are associated with (statistical) data,
;; such as population data. This makes them useful when comparing between 
;; geographically anchored, per-population quantities - like Stirling's bin collection quantities.
;;
;; We have used the term _approximately_ because mapping the bin collections data to DataZones 
;; is not simple and unamibiguous. 
;; For example, the data may say that a certain weight of binned material was collected in 
;; "`Aberfoyle, Drymen, Balmaha, Croftamie, Balfron & Fintry narrow access areas`",
;; and this needs to be aportioned across several DataZones.
;; In cases like this, we will aportion the weight across the DataZones, 
;; based on relative populations of those DataZones.
;; Will the resulting approximation be accurate enough to be useful?

;; ## Software libraries

;; Load the helper libraries.
^{:nextjournal.clerk/toc true
  ::clerk/visibility :fold}
(ns stirling-bin-collection-quantities-per-datazone
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as tds]
            [clj-http.client :as http]
            [geo.io :as gio]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import java.net.URLEncoder
           java.io.ByteArrayInputStream
           [com.univocity.parsers.csv CsvParser CsvParserSettings]))

;; Specify how to display datasets.
(clerk/set-viewers! [{:pred tc/dataset?
                      :transform-fn #(clerk/table {:head (tds/column-names %)
                                                   :rows (tds/rowvecs %)})}])

;; ## DataZones

;; ### Read the DataZones data

;; Each DataZone will have a name, a geographic boundary and a population. 

;; Define the SPARQL query to be used against the Scottish government SPARQL endpoint.
^{::clerk/viewer :hide-result}
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
SELECT ?name ?population ?geometry 
WHERE {
  ?dzUri stat-geo:status \"Live\";
         stat-geo:parentcode/rdfs:label \"Stirling\";
         stat-ent:code/rdfs:label \"Intermediate Zones\"; # 'Intermediate' seems the most appropriate given the resolution of the bin collections data
         rdfs:label ?name;
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
^{::clerk/viewer :hide-result}
(defn exec-against-scotgov [sparql]
  (:body (http/post "http://statistics.gov.scot/sparql"
                    {:body    (str "query=" (URLEncoder/encode sparql))
                     :headers {"Accept"       "text/csv"
                               "Content-Type" "application/x-www-form-urlencoded"}
                     :debug   false})))

;; Run the SPARQL query and read the result.
(def datazones
  (-> sparql
      exec-against-scotgov
      (.getBytes "UTF-8")
      (ByteArrayInputStream.)
      (tds/->dataset {:file-type :csv
                      :csv-parser (CsvParser.
                                   (doto (CsvParserSettings.)
                                       ;; up the max field length to allows for the large WKT geometry strings
                                     (.setMaxCharsPerColumn (* 65536 8))))
                      :key-fn    keyword})
      (tc/order-by :name)))

;; ### Plot the DataZones on a map

;; Import a stylesheet for the (Leaflet) map.
(clerk/html
 [:link {:rel         "stylesheet"
         :href        "https://unpkg.com/leaflet@1.7.1/dist/leaflet.css"
         :crossorigin "anonymous"}])

;; Define a (Clerk) component for displaying a (Leaflet) map.
^{::clerk/viewer :hide-result}
(def leaflet
  {:fetch-fn  (fn [_ x] x)
   :render-fn '(fn [value]
                 (v/html
                  (when-let [{:keys [geojson lat lng zoom]} value]
                    [v/with-d3-require {:package ["leaflet@1.7.1/dist/leaflet.js"]}
                     (fn [leaflet]
                       (letfn [(render-fn
                                 []
                                 [:div#leaflet-hook {:style {:height 400}}])
                               (did-mount-fn
                                 []
                                 (let [leaflet-map   (.map leaflet "leaflet-hook")
                                       basemap-layer (.tileLayer
                                                      leaflet
                                                      "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                                      (clj->js {:attribution "<a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>"}))
                                       geojson-layer (.geoJson
                                                      leaflet
                                                      (clj->js geojson)
                                                      (clj->js
                                                       {:style         (fn [^js feature]
                                                                         {:weight      1
                                                                          :opacity     0.5
                                                                          :color       "grey"
                                                                          :fillOpacity 0.1})
                                                        :onEachFeature (fn [^js feature layer]
                                                                         (let [props-map (js->clj (.. feature -properties) :keywordize-keys true)]
                                                                           (.bindTooltip layer (:name props-map))))}))]
                                   (.addTo basemap-layer leaflet-map)
                                   (.addTo geojson-layer leaflet-map)
                                   (.setView leaflet-map (.latLng leaflet lat lng) zoom)))
                               (did-update-fn
                                 [_this _prev-props])
                               (leaflet-component-fn
                                 []
                                 (reagent/create-class
                                  {:reagent-render       render-fn
                                   :component-did-mount  did-mount-fn
                                   :component-did-update did-update-fn}))]
                         [leaflet-component-fn]))])))})

; Display a map of the DataZones.
(clerk/with-viewer leaflet
  {:lat     56.257  
   :lng     -4.326 ;; approx. the centre of the Stirling council area (Ben Ledi)
   :zoom    9
   :geojson {:type     "FeatureCollection"
             :features (for [datazone (tc/rows datazones :as-maps)]
                         {:type       "Feature"
                          :geometry   (-> datazone :geometry .toString gio/read-wkt gio/to-geojson json/read-str)
                          :properties {:name (-> datazone :name)}})}})

;; ## Bin collections

;; ### Read the bin collections data

;; Read the CSV file from Stirling council's Open Data website.
(def bin-collections 
  (-> (tc/dataset "https://data.stirling.gov.uk/dataset/70614cbb-ff9e-4ef7-8e18-486017a368d6/resource/8807c713-46cb-4100-80c5-de8a457b0f8e/download/20220207-domestic-waste-collections-jan-2021-to-dec-2021.csv")
      (tc/reorder-columns ["Date" "Route" "Quantity"])
      (tc/order-by ["Date" "Route"])))

;; Count the rows and columns.
(tc/shape bin-collections)

;; Get a further description of the columns.
(tc/info bin-collections)

;; ## Map bin collection routes to DataZones

;; ### Define the mapping

;; Define a date parser.
^{::clerk/viewer :hide-result}
(defn parse-yyyyMMdd
  [^String date]
  (let [[_ dd MM yyyy] (re-find #"(\d{2})/(\d{2})/(\d{4})" date)]
    (str yyyy "-" MM "-" dd)))

;; Define how to clean the syntax of and correct the spellings within a named (bin collection) route.
^{::clerk/viewer :hide-result}
(def text-substitutions 
  ;; NB no specific ordering.
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
   "Broombridge"            "Broomridge"})

;; Define a function that will apply all of the syntax/spelling corrections to a named route.
^{::clerk/viewer :hide-result}
(defn apply-text-substitutions
  [^String route]
  (loop [todo        text-substitutions 
         accumulator route]
    (if (empty? todo)
      accumulator
      (let [[match substitution] (first todo)]
        (recur (rest todo)
               (str/replace accumulator match substitution))))))

;; Define how to split a named route into components.
^{::clerk/viewer :hide-result}
(defn parse-components 
  [^String route]
  (->> (str/split route #",")
       (map str/trim)))

;; Define a function that will return all DataZone names that (textually) wholely contain the route-component name.
^{::clerk/viewer :hide-result}
(defn datazone-names-wholely-containing
  [^String route-component]
  (filter #(str/includes? % route-component) (:name datazones)))

;; Define a look-up table: route-component -> datazone.
^{::clerk/viewer :hide-result}
(def lookup-table
  ;; route-component -> datazone
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
   "Blairhoyle"                         ["Carse of Stirling"]})

;; Map one route-component to many datazones.
^{::clerk/viewer :hide-result}
(defn ->datazone-names
  [^String route-component]
  (if-let [datazone-names (not-empty (datazone-names-wholely-containing route-component))]
    datazone-names
    (if-let [datazone-names (not-empty (get lookup-table route-component))]
      datazone-names
      [])))

;; Return the population of the given datazone.
^{::clerk/viewer :hide-result}
(defn ->population
  [datazone-name]
  (-> datazones
      (tc/select-rows (fn [row] (= datazone-name (:name row))))
      :population
      first))

;; ### Apply the mapping

;; Apply a pipleline data transformers to achieve the mapping.
(def datazoned-bin-collections
  (-> bin-collections
      ;; Ignore internal transfers
      (tc/drop-rows (fn [row] 
                      (= "Internal Stirling Council Transfer" (get row "Route"))))
      ;; Parse the date
      (tc/map-columns :yyyyMMdd ["Date"] 
                      (fn [date] (parse-yyyyMMdd date)))
      ;; Map: route -> datazones
      (tc/map-columns :datazones ["Route"] 
                      (fn [route] (->> route 
                                       apply-text-substitutions 
                                       parse-components
                                       (map ->datazone-names)
                                       flatten
                                       distinct
                                       vec)))
      ;; Map: datazones -> populations
      (tc/map-columns :populations [:datazones] 
                      (fn [datazones] (->> datazones
                                           (map ->population)
                                           vec)))
      ;; Map: populations -> fractions
      (tc/map-columns :fractions [:populations] 
                      (fn [populations] (let [total (apply + populations)]
                                          (->> populations
                                               (map #(/ % total))
                                               vec))))
      ;; Map: quantity, fractions -> fractional-quantities 
      (tc/map-columns :fractional-quantities ["Quantity" :fractions] 
                      (fn [quantity fractions] 
                        (->> fractions
                             (map #(* quantity %))
                             vec)))
      ;; Unroll the collection holding columns, rename each to its singular form, and sort
      (tc/unroll [:datazones :populations :fractions :fractional-quantities])
      (tc/rename-columns {:datazones             :datazone
                          :populations           :population
                          :fractions             :fraction
                          :fractional-quantities :fractional-quantity})
      (tc/reorder-columns [:yyyyMMdd :datazone :fractional-quantity :fraction :population])
      (tc/order-by [:yyyyMMdd :datazone])))

;; ### Plot 

;; Define a helper function that builds a plotline, from the data.
(defn ->plotline [name _colour point-data #_extra]
  {:name          name
   :x             (-> point-data :yyyyMMdd vec)
   :y             (-> point-data :month-quantity vec)
   :line          {;:color colour 
                   :width 2}
   ;:customdata    (->> extra
   ;                    :percentage 
   ;                    (map #(if (nil? %) "n/a" (format "%.1f%%" (double %)))) 
   ;                    vec)
   :hovertemplate (str "<b>" name "</b> (%{x})<br>"
                       "%{yaxis.title.text}: %{y}<br>"
                       ;"portion of total: %{customdata}<extra></extra>"
                       )})

(defn point-data
 [datazone] 
  (-> bin-collections-v2
      (tc/select-rows (fn [row] (= datazone (:datazone row))))
      (tc/map-columns :yyyyMMdd [:yyyyMMdd] (fn [yyyyMMdd] (str (subs yyyyMMdd 0 8) "01"))) ;; -> yyyy-MM-01
      (tc/group-by [:yyyyMMdd :population])
      (tc/aggregate {:monthly-quantity #(reduce + (% :fractional-quantity))})  
      (tc/map-columns :month-quantity [:monthly-quantity :population] (fn [monthly-quantity population] (/ monthly-quantity population)))
      ))

(def plot-lines
  (vec (for [datazone (map-areas :datazone)]
         (->plotline datazone :placeholder-colour (point-data datazone)))))


;; Display the graph.
(v/plotly 
 {:data   plot-lines
  :layout {:title  "Bin collection quantities across Stirling"
           :height 800 ;:margin {:l 110 :b 40}
           :xaxis  {:title    "month"
                    :type     "date"
                    ;:showgrid false ;:dtick 1
                    }
           :yaxis  {:title "tonnes per person" ;:tickformat "," :rangemode "tozero"
                    }
           ;:legend {:traceorder "reversed"}
                    ;; :plot_bgcolor "#fff1e5" :paper_bgcolor "floralwhite"
           }})


^{::clerk/viewer :hide-result}
(def leaflet
  )


(comment

  (-> (tc/dataset [{:a "y" :b 3 :c 1} {:b 5 :a "x" :c 2} {:a "x" :b 1 :c 2}])
      (tc/group-by [:a :c])
      (tc/aggregate #(reduce + (% :b))))


  (def body
    (-> sparql
        exec-against-scotgov))

  body

  (def x (-> body
             (.getBytes "UTF-8")
             (ByteArrayInputStream.)))

  x



  (def csv-parser
    (CsvParser. 
     (doto (CsvParserSettings.)
                  (.setMaxCharsPerColumn (* 65536 8)))))

  (def y  (tds/->dataset x {:file-type :csv
                            :csv-parser parser
                            :key-fn    keyword}))

  (tc/shape y)
  (-> y :geometry first)

  ;; Super useful for seeing a WDT geometry on a map  
  ;; http://arthur-e.github.io/Wicket/

  map-areas-test

  )