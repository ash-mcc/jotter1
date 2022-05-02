^{:nextjournal.clerk/toc true
  ::clerk/visibility :hide
  ::clerk/viewer :hide-result}
(ns opendatascot-search
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [nextjournal.clerk :as clerk])
  (:import java.net.URL
           javax.imageio.ImageIO
           [java.awt Color Font]))

^{::clerk/visibility :hide}
;; Use a photograph by Michael Holley, of a card based catalogue, 2009.
;; Licence: Public domain
(let [url         (URL. "https://upload.wikimedia.org/wikipedia/commons/3/3a/Copyright_Card_Catalog_Drawer.jpg")
      img         (with-open [in (io/input-stream url)] (ImageIO/read in))
      img-cropped  (.getSubimage img 0 210 1700 840)   ;(.getSubimage img 0 149 1799 1149)
      graphics    (.createGraphics img-cropped)]
  (.setColor graphics Color/WHITE)
  (.setFont graphics (Font. "SansSerif" Font/BOLD 16))
  (.drawString graphics "Photograph by Michael Holley, 2009", 1380, 25)
  img-cropped)

;; # Building a useful alternative search over Open Data Scotland's indexes

;; ## 👋 Introduction

;; The Open Data Scotland [website](https://opendata.scot/about/) 
;; provides an up-to-date list of the Open Data resources about Scotland.
;; It is being developed by the 
;; volunteer-run [OD_BODS project](https://github.com/OpenDataScotland/the_od_bods) team, and 
;; the idea for it originated from Ian Watt's 
;; Scottish Open Data [audit](https://github.com/watty62/SOD).
;;
;; The website has been built using the [JKAN](https://jkan.io) framework
;; which provides to end users, a ready-made _search-the-datasets_ feature
;; (try the search box near to the top of [this page](https://opendata.scot/datasets/)).
;; However, its search can sometimes excessively exclude because 
;; it returns only those datasets whose metadata contain all of the search words, consecutively.
;; 
;; For instance, say that we wanted to find all datasets related to waste management.
;; We might think of entering the search words: `waste` `management` `recycl` `bin` `landfill` `dump` `tip`.
;; With JKAN, we would fairly much have to search for each of those words individually then collate the results.
;;
;; Search tuning is its own field of research/area of business but, 
;; we will try to _easily_ build a useful alternative to the JKAN search, 
;; to better support exploratory searches...

;; ## 🗂️ Create searchable, in-memory index cards from the index files 

;; We start by getting the handles for the local copies of Open Data Scotland's index files.
^{::clerk/visibility :fold}
(def index-files
  (->> "/Users/amc/workspace/ash-mcc/jkan/_datasets"
       io/file
       file-seq
       (filter #(.isFile %))))

;; Each index file consists of a number of named fields - let's specify them.
^{::clerk/visibility :fold}
(def fields
  ["title" "organization" "notes" "license" "category" "maintainer" "maintainer_email"
   "date_created" "date_updated" "records" "original_dataset_link"])

;; Code how to establish the whereabouts of the fields within the text of a file.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn find-field-indexes
  [file-text fields]
  (for [field fields]
    [field (str/index-of file-text (str "\n" field ":"))]))

;; Code how to extract the fields (name-value pairs) from the text of a file.
^{::clerk/visibility :fold
  ::clerk/viewer     :hide-result}
(defn extract-field-texts
  [file-text fields]
  (let [field-indexes               (->> (find-field-indexes file-text fields)
                                         (remove #(nil? (second %)))
                                         (sort-by second))
        field-indexes-offset        (concat (rest field-indexes)
                                            [[:artifical-next (count file-text)]])
        field-indexes-with-endstops (map (fn [[field field-ix] [_next-field next-field-ix]]
                                           [field field-ix next-field-ix])
                                         field-indexes field-indexes-offset)]
    (for [[field ix1 ix2] field-indexes-with-endstops]
      [field (subs file-text (+ ix1 (count field) 2) ix2)])))

;; Code how to extract the value of a particular field.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn extract-field-text
  [field field-texts]
  (->> field-texts
       (filter (fn [[name _text]] (= name field)))
       first ;; expect only 1
       second ;; the field's text
       ))

;; For most fields, their value is held with the first line of text.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn first-line
  [field-text]
  (->> field-text
       (re-find #"[^\n]*") ;; up to any first newline
       str/trim))

;; For some fields, their value is held across multiple lines of text that contains (HTML-ish) markup.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn remove-markup-and-truncate
  [field-text]
  (let [s (-> field-text
              (str/replace #"<[^>]*>" " ") ;; remove HTML-ish markup
              (str/replace #"\"" "") ;; remove other unwanted syntax...
              (str/replace #"'" "")
              (str/replace #"\\n" " ")
              (str/replace #"\\" " ")
              str/trim)]
    (if (> (count s) 240) ;; truncate to 240 characters
      (subs s 0 240)
      s)))

;; Parse the contents of an Open Data Scotland index file, into an in-memory index-card.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn parse-index-file
  [f]
  (let [filename    (.getName f)
        field-texts (-> f
                        slurp
                        (extract-field-texts fields))]
    {:filename filename
     :title    (->> field-texts (extract-field-text "title") first-line)
     :org      (->> field-texts (extract-field-text "organization") first-line)
     :notes    (->> field-texts (extract-field-text "notes") remove-markup-and-truncate)
     :modified (->> field-texts (extract-field-text "date_updated") (re-find #"\d{4}-\d{2}-\d{2}"))
     :url      (->> field-texts (extract-field-text "original_dataset_link") first-line)
     :words    (->> field-texts
                    (map (fn [[_name text]] (re-seq #"\w+" text)))
                    flatten
                    (remove #(< (count %) 3)) ;; only 3+ character words
                    (map str/lower-case)
                    (sort-by count) ;; shorter words first 
                    )}))

;; Parse the contents of all of the Open Data Scotland index files, into a set of in-memory index-cards.
^{::clerk/visibility :fold}
(def index-cards
  (->> index-files
       (map parse-index-file)))

;; ## 🔎 Code how to search through the index cards

;; Try to match the search words against the contents of one index card.
^{::clerk/visibility :fold
  ::clerk/viewer     :hide-result}
(defn match-against-index-card
  [index-card search-words]
  (let [search-words->matches                    
        (->> (for [search-word search-words]
               [search-word (->> (:words index-card)
                                 (filter #(str/includes? % search-word))
                                 distinct)])
             (into {}))

        ;; how many search-words are exact equals of/contained within a word in the index card?
        count-of-search-words-with-subs-matches
        (->> (vals search-words->matches)
             (remove empty?)
             count)

        ;; how many search-words are exact equals of a word in the index card?
        count-of-search-words-with-exact-matches 
        (->> search-words->matches
             (map (fn [[search-word matches]] (filter #(= % search-word) matches)))
             (remove empty?)
             count)]
    
    (-> index-card
        (dissoc :words)
        (assoc :metric-1 count-of-search-words-with-subs-matches
               :metric-2 count-of-search-words-with-exact-matches
               :search-words->matches search-words->matches))))

;; Try to match the search words against the contents of all of the index cards.
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(defn match-against-index-cards
  [index-cards search-words]
  (for [index-card index-cards]
    (match-against-index-card index-card search-words)))

;; ## 🖥️ Create the UI

;; Code a Clerk viewer which can render a text input field
;; and can handle changes to its state.
;; (Thanks to Martin Kavalar and Jack Rusher for the pior art.)
^{::clerk/visibility :fold
  ::clerk/viewer :hide-result}
(def text-input
  {:pred ::clerk/var-from-def
   :fetch-fn (fn [_ x] x)
   :transform-fn (fn [{::clerk/keys [var-from-def]}]
                   {:var-name (symbol var-from-def) :value @@var-from-def})
   :render-fn '(fn [{:keys [var-name value]}]
                 (v/html [:input {:type          :text
                                  :placeholder   "Search... (type words of three or more letters)"
                                  :initial-value value
                                  :class         "px-3 py-3 placeholder-blueGray-300 text-blueGray-500 relative bg-white bg-white rounded text-sm border border-blueGray-300 outline-none focus:outline-none focus:ring w-full"
                                  :on-input      #(v/clerk-eval `(reset! ~var-name ~(.. % -target -value)))}]))})

(clerk/html
 [:div.mt-8
  [:span.text-lg.text-indigo-700.font-bold "Type words (of three or more letters)"]])
^{:nextjournal.clerk/viewer text-input}
(defonce text-state (atom ""))

^::clerk/no-cache
(clerk/html
 (let [search-results (->> (str/split @text-state #"\s")
                           (filter #(>= (count %) 3))
                           (map str/lower-case)
                           (match-against-index-cards index-cards)
                           (filter #(> (:metric-1 %) 0))
                           (sort-by (juxt :metric-1 :metric-2 :modified :title))
                           reverse)
       max-search-results-shown 50]
   [:div
    (let [n (count search-results)]
      [:span.text-sm n " (partial) match" (when (> n 1) "es") "."
       (if (> n max-search-results-shown) (str " Displaying the first " max-search-results-shown ".") "")])
    [:ol
     (for [index-card (take max-search-results-shown search-results)]
       [:li
        [:a {:href (:url index-card)} (:title index-card)] ", " (:modified index-card) ", " (:org index-card) [:br]
        (let [s (str/trim (:notes index-card))]
          (when (not (str/blank? s))
            [:<> [:span.text-gray-600 s] [:br]]))
        ":metric " (:metric index-card) " :metric2 " (:metric2 index-card) [:br]
        [:spam.text-gray-400.text-sm "Matching: "]
        (-> (for [[search-word matches] (:search-words->matches index-card)]
              (when-let [match (first matches)]
                (let [ix1 (str/index-of match search-word)
                      ix2 (+ ix1 (count search-word))]
                  [:span.text-gray-500.text-sm
                   (subs match 0 ix1)
                   [:span.font-extrabold.text-indigo-700 search-word]
                   (subs match ix2)])))
            (interleave (repeat " ")))])]]))
