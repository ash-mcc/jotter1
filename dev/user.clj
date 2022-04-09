(ns user
  (:require [clojure.java.browse :as browse]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))

(comment
  
  ;; start without file watcher, open browser when started
  (clerk/serve! {:browse? true})

  ;; start with file watcher for these sub-directory paths
  (clerk/serve! {:watch-paths ["notebooks" "src" "index.md"]})

  ;; start with file watcher and a `show-filter-fn` function to watch
  ;; a subset of notebooks
  (clerk/serve! {:watch-paths    ["notebooks" "src"]
                 :show-filter-fn #(clojure.string/starts-with? % "notebooks")})

  ;; open clerk
  (browse/browse-url "http://localhost:7777")

  ;; or call `clerk/show!` explicitly
  (clerk/show! "notebooks/scotgov_datasets.clj")
  (clerk/show! "notebooks/wikidata_label_first_querying_for_scotgov_agencies.clj")
  (clerk/show! "notebooks/pasi_poc_flowchart.clj")
  (clerk/show! "notebooks/stirling_bin_collection_data_on_a_map.clj")
  (clerk/show! "notebooks/leaflet_example.clj")
  (clerk/show! "notebooks/investigate_onscreen_controls.clj")
  (clerk/show! "notebooks/church.clj")

  (clerk/show! "index.md")

  (clerk/clear-cache!)

  ;; Clerk elides lists after the 20th element; show and tweak the eliding parameter :n
  (-> @v/!viewers :root (get 10) :fetch-opts :n)
  (swap! v/!viewers update-in [:root 10 :fetch-opts] #(assoc % :n 35))

  ;; [As is, THIS DOESN'T WORK. Probably need to pre-process that viewer.css file or supply a downstream derivative.]
  ;; Tweak where Clerk gets its stylesheet from
  ;; NOTE: Because of the way in which this is configured, 
  ;;       the viewer.css file needs to be served from a webserver, e.g.
  ;;          python3 -m http.server 7778 --directory public
  (swap! nextjournal.clerk.config/!resource->url assoc "/css/viewer.css" "http://localhost:7778/css/viewer.css")

  ;; generate a 'static app'
  (clerk/build-static-app! {:paths (mapv #(str "notebooks/" % ".clj")
                                         '[scotgov_datasets])})
  
  (clerk/build-static-app! {:paths ["index.md"
                                    "notebooks/scotgov_datasets.clj"
                                    "notebooks/wikidata_label_first_querying_for_scotgov_agencies.clj"]
                            :bundle? false})

  )
