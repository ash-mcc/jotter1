^{:nextjournal.clerk/toc true
  ::clerk/visibility :hide
  ::clerk/viewer :hide-result}
(ns stirling-bin-collection-quantities-per-datazone
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as tds]
            [clj-http.client :as http]
            [geo.io :as gio]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v])
  (:import [java.net URL URLEncoder]
           java.io.ByteArrayInputStream
           java.time.LocalDate
           javax.imageio.ImageIO
           [com.univocity.parsers.csv CsvParser CsvParserSettings]))

^{::clerk/visibility :hide}
;; Use a photograph by Lojze Jerala, of bins being emptied into a lorry in Ljubljana, Slovenia, 1959.
;; Licence: Public domain
(let [url (URL. "https://upload.wikimedia.org/wikipedia/commons/d/d2/Ljubljanski_smetarji_1959.jpg")
      img (with-open [in (io/input-stream url)] (ImageIO/read in))]
  (.getSubimage img 0 633 4985 2492))

^{::clerk/visibility :hide
  ::clerk/viewer :hide-result}
;; Specify how to display datasets
(clerk/set-viewers! [{:pred         tc/dataset?
                      :transform-fn #(clerk/table {:head (tds/column-names %)
                                                   :rows (tds/rowvecs %)})}])

;; # Stirling's bin collection quantities per DataZone

;; ## 👋 Introduction
 
;; Stirling council has published Open Data about its _bin collections_.
;; Its [data for 2021](https://data.stirling.gov.uk/dataset/waste-services-2021-onwards) includes town/area names.
;; Our aim is to _approximately_ map this data onto DataZones.
;;
;; [DataZones](https://www.gov.scot/binaries/content/documents/govscot/publications/statistics/2020/01/small-area-statistics-reference-materials/documents/data-zone-matching---quick-guide-2001-2011/data-zone-matching---quick-guide-2001-2011/govscot%3Adocument/Data%2BZone%2BMatching%2B-%2BQuick%2BGuide%2B%25282001-2011%2529.pdf)
;; are well defined geographic areas that are associated with (statistical) data,
;; such as population data. This makes them useful when comparing between 
;; geographically anchored, per-person quantities - like Stirling's bin collection quantities.
;;
;; We have used the term _approximately_ because mapping the bin collections data to DataZones 
;; is not simple and unamibiguous. 
;; For example, the data may say that a certain weight of binned material was collected in 
;; "`Aberfoyle, Drymen, Balmaha, Croftamie, Balfron & Fintry narrow access areas`",
;; and this needs to be aportioned across several DataZones.
;; In cases like this, we will aportion the weight across the DataZones, 
;; based on relative populations of those DataZones.
;; Will the resulting approximation be accurate enough to be useful?

;; ## 📍 DataZones

;; ### Read the DataZones data

;; Each DataZone will have a name, a geographic boundary and a population. 

;; Construct the SPARQL query to be used against the Scottish government SPARQL endpoint,
;; to fetch the DataZone data.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
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

;; Code how to run a SPARQL query against the Scottish government SPARQL endpoint.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn exec-against-scotgov [sparql]
  (:body (http/post "http://statistics.gov.scot/sparql"
                    {:body    (str "query=" (URLEncoder/encode sparql))
                     :headers {"Accept"       "text/csv"
                               "Content-Type" "application/x-www-form-urlencoded"}
                     :debug   false})))

;; Run the SPARQL query and read the result.
^{::clerk/visibility :fold}
(def datazones
  (-> sparql
      exec-against-scotgov
      (.getBytes "UTF-8")
      (ByteArrayInputStream.)
      (tds/->dataset {:file-type  :csv
                      :csv-parser (CsvParser.
                                   (doto (CsvParserSettings.)
                                     ;; up the max field length to allows for the large WKT geometry strings
                                     (.setMaxCharsPerColumn (* 65536 8))))
                      :key-fn     keyword})
      (tc/order-by :name)))

;; ### Plot the DataZones on a map

;; Import a stylesheet for the (Leaflet) map.
^{::clerk/visibility :fold}
(clerk/html
 [:link {:rel         "stylesheet"
         :href        "https://unpkg.com/leaflet@1.7.1/dist/leaflet.css"
         :crossorigin "anonymous"}])

;; Code a (Clerk) component for displaying a (Leaflet) map.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(def leaflet
  {:fetch-fn  (fn [_ x] x)
   :render-fn '(fn [value]
                 (v/html
                  (when-let [{:keys [geojson lat lng zoom]} value]
                    [v/with-d3-require {:package ["leaflet@1.7.1/dist/leaflet.js"]}
                     (fn [leaflet]
                       (letfn [(render-fn
                                 []
                                 [:div#leaflet-hook {:style {:height 500}}])
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
                                                                           (.bindTooltip layer (str "<div style='text-align: center'>" 
                                                                                                    "<h6>" (:name props-map) "</h6>"
                                                                                                    "pop. " (:population props-map)
                                                                                                    "</div>"))))}))]
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
^{::clerk/visibility :fold}
(clerk/with-viewer leaflet
  {:lat     56.257  
   :lng     -4.326 ;; approx. the centre of the Stirling council area (Ben Ledi)
   :zoom    9
   :geojson {:type     "FeatureCollection"
             :features (for [datazone (tc/rows datazones :as-maps)]
                         {:type       "Feature"
                          :geometry   (-> datazone :geometry .toString gio/read-wkt gio/to-geojson json/read-str)
                          :properties {:name       (-> datazone :name)
                                       :population (-> datazone :population)}})}})

;; ## 🚮 Bin collections

;; ### Read the bin collections data

;; Read the CSV file from Stirling council's Open Data website.
^{::clerk/visibility :fold}
(def bin-collections 
  (-> (tc/dataset "https://data.stirling.gov.uk/dataset/70614cbb-ff9e-4ef7-8e18-486017a368d6/resource/8807c713-46cb-4100-80c5-de8a457b0f8e/download/20220207-domestic-waste-collections-jan-2021-to-dec-2021.csv")
      (tc/reorder-columns ["Date" "Route" "Quantity"])
      (tc/order-by ["Date" "Route"])))

;; Count the rows and columns.
^{::clerk/visibility :fold}
(tc/shape bin-collections)

^{::clerk/visibility :hide
  ::clerk/viewer :hide-result}
(comment
  ;; Get a further description of the columns.
  (tc/info bin-collections)
  )

;; ## 🗂️ Map bin collection routes to DataZones

;; ### Define the routes to DataZones mappings

;; Code how to parse a date from text, then convert it to its month-end date.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn ->month-end
  [^String date-text]
  (let [[_ dd MM yyyy] (re-find #"(\d{2})/(\d{2})/(\d{4})" date-text)
        date           (LocalDate/parse (str yyyy "-" MM "-" dd))
        month-end-date (.withDayOfMonth date (.length (.getMonth date) (.isLeapYear date)))]
    (.toString month-end-date)))

;; Specify textual substitutions for cleaning the syntax 
;; and correcting the spelling of the name of a bin collection route.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
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

;; Code how to apply all of the syntax/spell correcting substitutions to a route name.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn apply-text-substitutions
  [^String route]
  (loop [todo        text-substitutions 
         accumulator route]
    (if (empty? todo)
      accumulator
      (let [[match substitution] (first todo)]
        (recur (rest todo)
               (str/replace accumulator match substitution))))))

;; Code how to split a route name into component-route names.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn parse-components 
  [^String route]
  (->> (str/split route #",")
       (map str/trim)))

;; Code how to find all DataZone names that (textually) wholely contain the route-component name.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn dz-names-wholely-containing
  [^String route-component]
  (filter #(str/includes? % route-component) (:name datazones)))

;; Define a look-up table of route-component name -> datazone name.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
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
   "Whins of Milton"                    ["Hillpark"]
   "Riverside"                          ["Forth"]
   "Cambuskenneth"                      ["Forth"]
   "Sherrifmuir"                        ["Dunblane East"]
   "Dunblane North"                     ["Dunblane East"]
   "Blairhoyle"                         ["Carse of Stirling"]})

;; Keep a note of the route-components that don't get matched with datazones.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(def route-components-not-matched-with-datazones (atom #{}))

;; Code how to map one route-component to many datazones.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn ->dz-names
  [^String route-component]
  (if-let [dz-names (not-empty (dz-names-wholely-containing route-component))]
    dz-names
    (if-let [dz-names (not-empty (get lookup-table route-component))]
      dz-names
      (do
        (swap! route-components-not-matched-with-datazones conj route-component)
        []))))

;; Code how to find the population of a named datazone.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn ->population
  [dz-name]
  (-> datazones
      (tc/select-rows (fn [row] (= dz-name (:name row))))
      :population
      first))

;; ### Calculate the bin collection quantities per DataZone

;; Apply our pipeline of data transformers/mappings to compute the quantities per DataZone.
^{::clerk/visibility :fold}
(def bin-collections-per-datazone
  (-> bin-collections
      
      ;; Parse the date and convert it to the date representing its month-end
      (tc/map-columns :month-ending ["Date"]
                      (fn [date] (->month-end date)))
      
      ;; Map: one route -> many datazones
      (tc/map-columns :dz-names ["Route"]
                      (fn [route] (->> route
                                       apply-text-substitutions
                                       parse-components
                                       (map ->dz-names)
                                       flatten
                                       distinct
                                       vec)))
      
      ;; Map: datazone -> datazone population
      (tc/map-columns :dz-populations [:dz-names]
                      (fn [dz-names] (->> dz-names
                                          (map ->population)
                                          vec)))
      
      ;; Map: datazone populations of a route -> fractions per datazones of a route 
      (tc/map-columns :fractions [:dz-populations]
                      (fn [populations] (let [total (apply + populations)]
                                          (->> populations
                                               (map #(/ % total))
                                               vec))))
      
      ;; Map: quantity, fractions per datazones of a route -> quantities per datazones of a route 
      (tc/map-columns :dz-quantities ["Quantity" :fractions]
                      (fn [quantity fractions]
                        (->> fractions
                             (map #(* quantity %))
                             vec)))
      
      ;; Unroll the collection holding columns, and rename each to its singular form
      (tc/unroll [:dz-names :dz-populations :dz-quantities])
      (tc/rename-columns {:dz-names       :dz-name
                          :dz-populations :dz-population
                          :dz-quantities  :dz-quantity})
      
      ;; Calculate per-person quantities
      (tc/map-columns :dz-person-quantity [:dz-quantity :dz-population]
                      (fn [dz-quantity dz-population]
                        (/ dz-quantity dz-population)))
      
      ;; Rollup to monthly quantities
      (tc/group-by [:month-ending :dz-name :dz-population])
      (tc/aggregate {:dz-person-month-quantity   (fn [sub-ds] 
                                                   (reduce + (sub-ds :dz-person-quantity)))
                     :dz-month-recycling-percent (fn [sub-ds] 
                                                   (/ (reduce + (-> sub-ds 
                                                                    (tc/select-rows (fn [row] 
                                                                                      (str/includes? (row "Category") "Recycling"))) 
                                                                    :dz-person-quantity))
                                                      (reduce + (sub-ds :dz-person-quantity))))})
      
      ;; Rank datazone based on their per-person month average quantity, also on their month average percentage recycled
      (tc/fold-by [:dz-name])
      (tc/map-columns :dz-person-month-quantity-avg [:dz-person-month-quantity] 
                      (fn [dz-person-month-quantity]
                        (/ (apply + dz-person-month-quantity) 
                           (count dz-person-month-quantity))))
      (tc/order-by [:dz-person-month-quantity-avg])
      (tc/add-column :dz-person-month-quantity-avg-rank (rest (range)))
      (tc/map-columns :dz-month-recycling-percent-avg [:dz-month-recycling-percent]
                      (fn [dz-month-recycling-percent]
                        (/ (apply + dz-month-recycling-percent)
                           (count dz-month-recycling-percent))))
      (tc/order-by [:dz-month-recycling-percent-avg] :desc)
      (tc/add-column :dz-month-recycling-percent-avg-rank (rest (range)))
      (tc/unroll [:month-ending :dz-population :dz-person-month-quantity :dz-month-recycling-percent])
      
      ;; Order columns and rows
      (tc/select-columns [:month-ending :dz-name :dz-population 
                          :dz-person-month-quantity :dz-person-month-quantity-avg-rank 
                          :dz-month-recycling-percent :dz-month-recycling-percent-avg-rank])
      (tc/reorder-columns [:month-ending :dz-name :dz-population 
                           :dz-person-month-quantity :dz-person-month-quantity-avg-rank 
                           :dz-month-recycling-percent :dz-month-recycling-percent-avg-rank])
      (tc/order-by [:month-ending :dz-name])))

;; Look at the route-components which weren't mapped to DataZones.
;;
;; At the time of writing, these were:
;; * `Internal Stirling Council Transfer` - ignorable since it isn't applicable for our purpose.
;; * `Not applicable` - which suggests that it is ignorable for our purpose!
;; * `All routes` and `Rural glass bring sites` - ignored since there is little value in _spreading_ these across a lot of DataZones.
;; * `Kilm` - ignored because I can't find this town/area; perhaps it's a spelling mistake.
^{::clerk/visibility :fold}
@route-components-not-matched-with-datazones

;; ## 📉 Plot the bin collection quantities per DataZone 

;; Code how to construct the specification for a plotline, 
;; from a dataset about one DataZone.
;; This will only show the plotlines of the best and worst two DataZones by rank.
;; To see the other plotlines, click on their legend listings.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn plotline-spec
  [{:keys [rank-kw y-kw template-fragment] :as _config} last-rank sub-ds]
  (let [second-last-rank (- last-rank 1)
        name             (-> sub-ds tc/dataset-name (subs 7))
        rank             (-> sub-ds rank-kw first)]
    {:name          name
     :x             (-> sub-ds :month-ending vec)
     :y             (-> sub-ds y-kw vec)
     :line          {:width 2}
     :visible       (cond
                      (<= rank 2) true
                      (>= rank second-last-rank) true
                      :else "legendonly")
     :hovertemplate (str "<b>" name "</b> (avg. rank " rank ")<br>"
                         template-fragment " for %{x|%b'%y}<br>"
                         "<extra></extra>")}))

;; Code how to construct the specification for a list of plotlines, 
;; from a dataset about a list of DataZones.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn plotlines-spec
  [config ds]
  (let [sub-ds-coll    (-> ds
                           (tc/group-by :dz-name)
                           (tc/groups->seq))
        last-rank      (count sub-ds-coll)
        plotline-spec' (partial plotline-spec config last-rank)]
    (->> sub-ds-coll
         (map plotline-spec')
         vec)))

;; Code how to construct the specification for a chart, 
;; from a dataset about a list of DataZones.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn chart-spec
  [{:keys [chart-title y-axis-title] :as config} ds]
  {:data   (plotlines-spec config ds)
   :layout {:title  chart-title
            :height 700
            :margin {:l 75
                     :b 80}
            :xaxis  {:title      "Month"
                     :type       "date"
                     :showgrid   false
                     :tickformat "%b'%y"
                     :tickangle  45
                     :tick0      (-> ds :month-ending sort first)
                     :dtick      "M1"}
            :yaxis  {:title      y-axis-title
                     :tickformat ".2f"
                     :rangemode  "tozero"}}})

;; ### Plot the monthly per-person quantities
^{::clerk/visibility :fold}
(v/plotly 
 (chart-spec {:chart-tile        "Bin collection quantities across Stirling"
              :y-axis-title      "Tonnes per person"
              :rank-kw           :dz-person-month-quantity-avg-rank
              :y-kw              :dz-person-month-quantity
              :template-fragment "%{y:.3f} tonnes per-person"} 
             bin-collections-per-datazone))

;; ### Plot the monthly recycling percentages
^{::clerk/visibility :fold}
(v/plotly
 (chart-spec {:chart-tile        "Bin collection recycling percentages across Stirling"
              :y-axis-title      "Recycling percentage"
              :rank-kw           :dz-month-recycling-percent-avg-rank
              :y-kw              :dz-month-recycling-percent
              :template-fragment "%{y:.3f}% recycling"} 
             bin-collections-per-datazone))


;; ## 🤔 Conclusions
;;

;; The charts suggest that there are substantial differences between some DataZones, for example:
;; * the `per-person quantities` chart indicates that there is roughly a ×3 difference between the
;; best (`Broomridge`) and worst (`Kippen and Fintry`) DataZones, 
;; * and the `recycling percentages` chart indicates that there is roughly a ×2 difference between the 
;; best (`City Centre`) and worst (`Bridge of Allan and University`) DataZones.
;;
;; Are these differences _real_? Well, they are too significant to have arisen due to a few bad data points or mappings.
;; Ok then, could the differences be due to systematic differences in the method used to categorise 
;; and measure bin collection quantities, between DataZones? That's unlikely since many of the DataZones 
;; at both ends of the ranking share the same processing/measurement facility.  
;;
;; Most of the DataZones exhibit a _step change_ in both charts around `Aug'21`-`Nov'21`.
;; This coincides with Stirling council's 
;; [change to a four-weekly bin collection](https://stirling.gov.uk/news/2021/july-2021/council-announces-recycle-4-stirling-campaign/) 
;; for grey (general waste) and blue (plastics, cartons and cans) bins.
;; 
;;
;; * have I got all the route -> DataZone mappings correct ?
;; * be better if Stir council used DZs directly
;; * what's a apparrent...
;; * more data please


^{::clerk/visibility :hide
  ::clerk/viewer :hide-result}
(comment
  ;; Check quantity total from the downstream dataset (much processed) 
  ;; against the quantity total from the upstream dataset (closer to the source, more raw).
  (println "upstream total =   " 
           (-> bin-collections
               ;; the next drop-rows is good enough - although imperfect since it assumes that the route-component is actually the whole of the route 
               (tc/drop-rows (fn [row] (contains? @route-components-not-matched-with-datazones (row "Route")))) 
               (tc/aggregate {:total (fn [row] (reduce + (row "Quantity")))})
               :total
               first))
  (println "downstream total = " 
           (-> bin-collections-per-datazone
               (tc/map-columns :dz-month-quantity [:dz-person-month-quantity :dz-population] 
                               (fn [dz-person-month-quantity dz-population] 
                                 (* dz-person-month-quantity dz-population)))
               (tc/aggregate {:total (fn [row] (reduce + (row :dz-month-quantity)))})
               :total
               first))

  ;; Super useful for seeing a WDT geometry on a map  
  ;; http://arthur-e.github.io/Wicket/       
  )