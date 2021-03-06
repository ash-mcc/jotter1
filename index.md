```clojure
^{:nextjournal.clerk/visibility #{:hide :hide-ns}}
(ns index
  (:require [nextjournal.clerk :as clerk]))
```

# Ash's jotter #&#8203;1 🗒️ - Extracting information from Open Data

Walk-throughs, notes and other jottings about extracting information from Open Data.

```clojure
(clerk/html
  (into
    [:div.md:grid.md:gap-8.md:grid-cols-2.pb-8]
    (map
      (fn [{:keys [path preview title description]}]
        [:a.rounded-lg.shadow-lg.border.border-gray-300.relative.flex.flex-col.hover:border-indigo-600.group.mb-8.md:mb-0
         {:href (clerk/doc-url path)
          :style {:height 300}}
         [:div.flex-auto.overflow-hidden.rounded-t-md.flex.items-center.px-3.py-4
          [:img {:src preview :width "100%" :style {:object-fit "contain"}}]]
         [:div.sans-serif.border-t.border-gray-300.px-4.py-2.group-hover:border-indigo-600
          [:div.font-bold.block.group-hover:text-indigo-600 title]
          [:div.text-xs.text-gray-500.group-hover:text-indigo-600.leading-normal description]]])
      [{:title "🏴󠁧󠁢󠁳󠁣󠁴󠁿 Datasets from statistics.gov.scot which might be useful to the_od_bods project"
        :preview "img/preview-scotgov-datasets.png"
        :path "notebooks/scotgov_datasets.clj"
        :description "We generate a CSV file that describes the 311 datasets (15 organisations) that are available via Scot gov’s SPARQL endpoint."}
       {:title "The label-first 🏷️ querying of WikiData for Scottish government agencies"
        :preview "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Mundaneum_Tiräng_Karteikaarten.jpg/440px-Mundaneum_Tiräng_Karteikaarten.jpg"
        :path "notebooks/wikidata_label_first_querying_for_scotgov_agencies.clj"
        :description "We construct a label-first query that is equivalent to CTC24's more typical QID-first Wikidata SPARQL query, for Scottish government agencies."}
       {:title "A flowchart of PASI's proof-of-concept implementation"
        :preview "img/pasi-poc-flowchart.png"
        :path "notebooks/pasi_poc_flowchart.clj"
        :description "A somewhat ideal view of the flow of waste reduction related data 
        through the PASI (Participatory Accounting for Social Impact) system."}
       {:title "Stirling's bin collection quantities per DataZone"
        :preview "https://upload.wikimedia.org/wikipedia/commons/d/d2/Ljubljanski_smetarji_1959.jpg"
        :path "notebooks/stirling_bin_collection_quantities_per_datazone.clj"
        :description "We tranfer Stirling's bin collection data onto DataZones (map areas with known statistics)
        to extract insights."}
       {:title "Building a useful alternative search over Open Data Scotland's indexes"
        :preview "https://upload.wikimedia.org/wikipedia/commons/3/3a/Copyright_Card_Catalog_Drawer.jpg"
        :path "notebooks/opendatascot_search.clj"
        :description "We try to build a simply but useful alternative to the JKAN search, 
        to better support exploratory searching."}])))