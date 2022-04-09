;; see https://github.com/kommen/ogd/blob/main/notebooks/graph.clj

^{:nextjournal.clerk/visibility :hide}
(ns leaflet-example
  (:require [nextjournal.clerk :as clerk])
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

          ;; try to re-init the map which isn't good so commented out for now
          #_[:h3.cursor-pointer
              ;; towards being able to have browser-side controls in cljs that change 
              ;; the browser-side leaflet map
           {:on-click ;#(js/alert "BOO!") 
            #(let [ix (swap! !cljs-side-counter inc)]
                                              ;(reset! data (get choices ix)) doesn't work - can't reach back to the clj
               ix)}
           "Say boo " @!cljs-side-counter]
          
            ;(js/console.log ">>>> 2")
             ; for Wicket see https://github.com/arthur-e/Wicket/blob/master/doc/leaflet.html
             ;[:script "wkt = new Wkt.Wkt(); alert(wkt);"]
          [:p "info:"
           #_(doto (wicket/Wkt.)
               (.read "POLYGON ((30 10, 10 20, 20 40, 40 40, 30 10))")
               (.toJson))]
          [v/with-d3-require {:package ["wicket@1.3.8/wicket.js"]} 
            (fn [wicket]
              [:div 
               [:h3.cursor-pointer 
                {:on-click #(do
                              (js/console.log "TODO wicket stuff")
                              (js/console.log wicket)
                              (let [v2 (.isArray wicket (clj->js [2]))
                                    _ (js/console.log (str "isArray ans = " v2))
                                    v3 (.trim wicket "xxxHelloxxx" "x")
                                    _ (js/console.log (str "trim ans = " v3))
                                    
                                    v4 (.Wkt.read (wicket) "POLYGON ((30 10, 10 20, 20 40, 40 40, 30 10))")
                                    _ (js/console.log v4)
                                    ;v5 (.Wkt wicket wicket)
                                    ;_ (js/console.log v5)
                                    ;v6 (js/wicket.Wkt.)
                                    ;_ (js/console.log v6)
                                    ;(.read v4 "POLYGON ((30 10, 10 20, 20 40, 40 40, 30 10))")
                                    ;(js/console.log (.toJson v4))
                                    ]
                                ;(js/console.log jsonObj)
                                ))} 
                "do wicket stuff"]])]
          ]
         (when-let [{:keys [lat lng]} value]
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
                                                                    }))]
                                  (.addTo basemap-layer m)
                                  (.addTo location-marker m)
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

(reset! data {:title {:text "hacked 0"}
              :dtv/location {:lat 56.11578973082037
                             :lng -3.9373153205613627}})

(clerk/with-viewer '(fn [_] (reagent/with-let [counter (reagent/atom 0)]
                              (v/html 
                               [:h3.cursor-pointer 
                                {:on-click #(let [ix (swap! counter inc)]
                                              ;(reset! data (get choices ix)) doesn't work - can't reach back to the clj
                                              ix)} 
                                "counter: " @counter])))
  nil)