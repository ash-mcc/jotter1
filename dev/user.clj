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

  (clerk/show! "index.md")

  (clerk/clear-cache!)

  ;; Clerk elides lists after the 20th element; show and tweak the eliding parameter :n
  (-> @v/!viewers :root (get 10) :fetch-opts :n)
  (swap! v/!viewers update-in [:root 10 :fetch-opts] #(assoc % :n 35))

  ;; generate a 'static app'
  (clerk/build-static-app! {:paths (mapv #(str "notebooks/" % ".clj")
                                         '[scotgov_datasets])})

  )
