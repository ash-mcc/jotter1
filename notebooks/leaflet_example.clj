;; see https://github.com/kommen/ogd/blob/main/notebooks/graph.clj

^{:nextjournal.clerk/visibility :hide}
(ns leaflet-example
  (:require [clojure.data.json :as json]
            [nextjournal.clerk :as clerk]
            [geo.io :as gio])
  (:import [java.time LocalDate]))

^{::clerk/viewer
  {:fetch-fn     (fn [_ x] x)
   :transform-fn (fn [{:as _x ::clerk/keys [var-from-def]}]
                   {:value @@var-from-def})
   :render-fn '(fn [{:as x :keys [var-name value options]}]
                 (v/html [:h1 (:text (:title value))]))}}
(defonce data (atom nil))


(clerk/html
 [:link {:rel "stylesheet"
         :href "https://unpkg.com/leaflet@1.7.1/dist/leaflet.css"
         :crossorigin ""}])

^{::clerk/viewer :hide-result}
(def leaflet
  {:fetch-fn (fn [_ x] x)
   :render-fn
   '(fn [value]
      (v/html
       (reagent/with-let [!cljs-side-counter (reagent/atom 0)]
                         [:<>
         [:div

          ;; tries to re-init the map which isn't good so commented out for now
          #_[:h3.cursor-pointer
              ;; towards being able to have browser-side controls in cljs that change 
              ;; the browser-side leaflet map
           {:on-click ;#(js/alert "BOO!") 
            #(let [ix (swap! !cljs-side-counter inc)]
                                              ;(reset! data (get choices ix)) doesn't work - can't reach back to the clj
               ix)}
           "Say boo " @!cljs-side-counter]
          
          ]
         (when-let [{:keys [lat lng geojson]} value]
           [v/with-d3-require {:package ["leaflet@1.7.1/dist/leaflet.js"]} 
            (fn [leaflet]
              [:div {:style {:height 400}
                     :ref   (fn [el]
                              (when el
                                (let [m               (.map leaflet el (clj->js {:zoomControl true
                                                                                 :zoomDelta   0.5
                                                                                 :zoomSnap    0.0}))
                                      location-latlng (.latLng leaflet lat lng)
                                      location-marker (.marker leaflet location-latlng)
                                      basemap-layer   (.tileLayer leaflet
                                                                  "http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                                                  (clj->js
                                                                   {;:subdomains    ["maps" "maps1" "maps2" "maps3" "maps4"]
                                                               ;:maxZoom       25
                                                               ;:maxNativeZoom 19
                                                                    :attribution "openstreetmap.org"
                                                               ;:errorTileUrl  "/transparent.gif"
                                                                    }))
                                      style-fn  (fn [^js feature]
                                                  {:weight     1
                                                   :opacity    0.5
                                                   :color      "gray"
                                                   "fillOpacity" 0.1})
                                      geojson (clj->js geojson)
                                      ;_ (js/console.log geojson)
                                      geojson-layer   (.geoJson leaflet geojson
                                                                (clj->js 
                                                                 {:style style-fn
                                                                  :onEachFeature (fn [feature layer]
                                                                                   (let [properties-map (js->clj (.. feature -properties))
                                                                                         datazone         (get properties-map "datazone")]
                                                                                     (.bindTooltip layer datazone)))}))]
                                  (.addTo basemap-layer m)
                                  (.addTo location-marker m)
                                  (.addTo geojson-layer m)
                                  (.setView m location-latlng 8.7))))}]
              

              )])])))})


;^::clerk/no-cache
(clerk/with-viewer leaflet
  (when-let [d @data]
    (:dtv/location d)))


(def choices 
   [{:title        {:text "hacked 1"}
     :dtv/location {:lat 55.0
                    :lng -4.1}}
    {:title        {:text "hacked 2"}
     :dtv/location {:lat 57.1
                    :lng -3.7}}])

(def map-areas
  [{:datazone "King's Park"
    :geometry "POLYGON ((-3.9373153205613627 56.11578973082037, -3.938319723490875 56.11401262251436, -3.937142904573308 56.11392661519023, -3.9356632300481365 56.11322611579657, -3.934635482408255 56.11211902588475, -3.932752249590143 56.11204200445095, -3.9330050489858834 56.11131014465308, -3.9329696461588957 56.10996273178065, -3.931934212637632 56.10823556783211, -3.931712006035565 56.10733141348903, -3.932003118344932 56.106401253735505, -3.9325910625077145 56.1056192163901, -3.9336352029230013 56.104955841776935, -3.935152164117855 56.10378180540084, -3.9358238006851956 56.102746818524494, -3.936320999612703 56.103249245318935, -3.9380684457642756 56.10284470688215, -3.939124740792421 56.10283879509215, -3.940997350609335 56.103266022641414, -3.9406241502888704 56.103807142179086, -3.9428510384114768 56.103553995870506, -3.9456507280039688 56.10380339209474, -3.949214916467403 56.1039559677801, -3.9494712621708907 56.10361941982574, -3.950730981316457 56.10436337217152, -3.950601652324575 56.10498548358809, -3.95173336863569 56.104787866854856, -3.9517790020616643 56.10441869916914, -3.9533604369667317 56.10452848372259, -3.9533716814270674 56.105067495577906, -3.954186429626361 56.10527926361602, -3.953835072061997 56.105644284780205, -3.9549633236003996 56.10601284270148, -3.9544840236303123 56.10671977891168, -3.9535951318809595 56.10630409612368, -3.9532166952379892 56.107988895773794, -3.952683916985258 56.10786500662388, -3.951888624614545 56.108552639034755, -3.9520798983549867 56.108928853629045, -3.9515608142136958 56.10999179502219, -3.95007815582015 56.110910249370285, -3.951172548422438 56.1112274625631, -3.953311116120774 56.111277688352004, -3.9557878513553346 56.11165968486966, -3.956322021369293 56.11289317316148, -3.9544315228759466 56.11236380370319, -3.953542913581778 56.11227075712985, -3.949641360307066 56.11143497570783, -3.949121913009779 56.11155475346086, -3.9478939124078867 56.11235053837277, -3.9451507927266403 56.11359965306764, -3.9455933715362423 56.114401461902965, -3.947379896246766 56.11626887644877, -3.947893199922685 56.116445316809425, -3.948521234836155 56.11729757836274, -3.9485983816043877 56.117838694367, -3.949709408128657 56.11790458627346, -3.9500619152565877 56.118476121824294, -3.949383201757314 56.11859113589739, -3.9483752925829236 56.1183497795632, -3.9479191405435734 56.119046741815446, -3.9464335775245316 56.12032191086853, -3.9448094668827154 56.11926650223211, -3.9435559378485205 56.11897171331754, -3.9410684350126126 56.118130169639414, -3.9410940075980903 56.117680444902774, -3.938371421170509 56.116959367226606, -3.9385621671441213 56.11636275462428, -3.9373153205613627 56.11578973082037))"}
   {:datazone "Cowie"
    :geometry "POLYGON ((-3.867464490360657 56.086802376482396, -3.8669569668739237 56.08704160582965, -3.8641266615664853 56.08756519109036, -3.862745905637044 56.085503057456286, -3.8621595552791748 56.084343681139636, -3.8632117068659704 56.08383355264753, -3.862736928330067 56.08298701654782, -3.860523448142189 56.08207683904093, -3.8625166131077417 56.08042024629621, -3.8605085367517296 56.079767582321004, -3.858757828835901 56.079452473096985, -3.8593993313726322 56.07909234193392, -3.8582425455336504 56.07776181418297, -3.8595473683299484 56.07585702142414, -3.862994978067242 56.07577820662675, -3.8634106884108395 56.07396353336005, -3.8685582191237162 56.07277138563115, -3.8697289723462425 56.073059188587635, -3.870729662680293 56.073484363432634, -3.8716878504072567 56.07137598978863, -3.8733398018470484 56.07164748116582, -3.8728682757102977 56.07413992961834, -3.8731616964440683 56.07493021319219, -3.8749191028518073 56.07637729049731, -3.8752638789246614 56.077189812144375, -3.874467300210529 56.077992715381264, -3.8728521238055666 56.078484529521745, -3.874529894847714 56.079049550743875, -3.873564950635016 56.07987997787208, -3.8724711910499376 56.0796816060925, -3.8720473728617275 56.07996362978459, -3.8729842377045647 56.08153789014152, -3.872009072546642 56.0819750450559, -3.8737223605343134 56.082838695563716, -3.8742650420978917 56.08342355555504, -3.8748489299597617 56.0834110115486, -3.874864915748056 56.08487155671276, -3.872403112029636 56.08583521663561, -3.871255689516953 56.08657147341159, -3.867464490360657 56.086802376482396))"}])

;; King's Park -ish and Cowie areas
(def feature-collection
  {:type     "FeatureCollection"
   :features (for [area map-areas]
               {:type       "Feature"
                :geometry   (-> area :geometry gio/read-wkt gio/to-geojson json/read-str)
                :properties {:datazone (-> area :datazone)}})})


(reset! data {:title        {:text "hacked 0"}
              :dtv/location {:lat     56.11578973082037
                             :lng     -3.9373153205613627
                             :geojson feature-collection}
              })

(clerk/with-viewer '(fn [_] (reagent/with-let [counter (reagent/atom 0)]
                              (v/html 
                               [:h3.cursor-pointer 
                                {:on-click #(let [ix (swap! counter inc)]
                                              ;(reset! data (get choices ix)) doesn't work - can't reach back to the clj
                                              ix)} 
                                "counter: " @counter])))
  nil)