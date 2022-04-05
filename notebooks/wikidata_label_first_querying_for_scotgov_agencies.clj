;; # The label-first 🏷️ querying of WikiData for Scottish government agencies

;; ## Intro 

;; One of the projects at the [CTC24 hackathon](https://codethecity.org/what-we-do/hack-weekends/ctc24-open-in-practice/)
;; worked on building a Wikidata representation of government in Scotland,
;; culminating in [this SPARQL query](https://w.wiki/4TpN) to show the results.

;; That query contains the [Q identifiers](https://www.wikidata.org/wiki/Q43649390) 
;; of the classes of the items that are to be found.
;; It's a well constructed and typical _QID-first_ Wikidata SPARQL query.
;; QIDs are used because they are unambiguous (and succinct) but, they are not human-oriented.
;; Many SPARQL tools remedy this by displaying human-oriented labels for QIDs in _tooltips_.
;; An alternative approach is, from the outset, to construct an equivalent but more human-oriented, 
;; _label-first_ query...

;; To help us, we use [Mundaneum](https://github.com/jackrusher/mundaneum) - Jack Rusher's query abstraction over Wikidata.

;; ## Querying

;; Load the helper libraries.
^{:nextjournal.clerk/toc true}
(ns wikidata-label-first-querying-for-scotgov-agencies
  (:require [clojure.string :as str]
            [mundaneum.query :as wd]
            [backtick :as b]
            [tablecloth.api :as tc]
            [nextjournal.clerk :as clerk]))

;; Define the queries in a _label-first_ way.
(def queries 
  
  {:exec-agency         
   '[[?item (wdt :instance-of) (entity "executive agency in the Scottish government")]]
   
   :non-dept-publ-body 
   '[[?item (wdt :instance-of) (entity "non-departmental public body")
      _ (wdt :applies-to-jurisdiction) (entity "Scotland")]
     :minus [[?item (wdt :instance-of) (entity "general lighthouse authority")]]]
   
   :commission          
   '[[?item (wdt :instance-of) (entity "commission" :office-held-by-head-of-the-organization (entity "commissioner"))
      _ (wdt :applies-to-jurisdiction) (entity "Scotland")]]
   
   :commissioner        
   '[[?item (wdt :instance-of) (entity "commissioner")
      _ (wdt :applies-to-jurisdiction) (entity "Scotland")]]
   
   :publ-corp           
   '[[?item (wdt :instance-of) (entity "state-owned enterprise")
      _ (wdt :owned-by) (entity "Scottish Government")]]
   
   :parole-board        
   '[[?item (wdt :instance-of) (entity "parole board")
      _ (wdt :applies-to-jurisdiction) (entity "Scotland")]]
   
   :tribunal           
   '[[?item (wdt :instance-of) (entity "tribunal")
      _ (wdt :applies-to-jurisdiction) (entity "Scotland")]
     :minus [[?item (wdt :applies-to-jurisdiction) (entity "England and Wales")]]]
   
   :non-minstr-gov-dept 
   '[[?item (wdt :instance-of) (entity "non-ministerial government department")]
     :union [[?item (wdt :applies-to-jurisdiction) (entity "Scotland")]
             [?item (wdt :located-in-the-administrative-territorial-entity) (entity "Scotland")]]
     :filter (?item != (entity "Forestry Commission" :instance-of (entity "non-ministerial government department")))]
   
   :gov-agency          
   '[[?item (wdt :instance-of) (entity "government agency")]
     :union [[?item (wdt :applies-to-jurisdiction) (entity "Scotland")]
             [?item (wdt :located-in-the-administrative-territorial-entity) (entity "Scotland")]]]
   
   :queen-printer       
   '[[?item (wdt :instance-of) (entity "Queen's Printer")]]
   
   :council             
   '[[?item (wdt :instance-of) (entity "Scottish unitary authority council")]]
   
   :nhs-board           
   '[[?item (wdt :instance-of) (entity "NHS board")]]
   
   :health-partnership  
   '[[?item (wdt :instance-of) (entity "Health and Social Care Partnership")]]

   :court               
   '[[?item (wdt :part-of) (entity "Courts of Scotland")]]})

;; Execute those queries and collect their results into a column-oriented dataset.
^{::clerk/visibility :fold}
(def DS0 (-> (for [[id where-clause] queries]
              (->
               (b/template
                [:select distinct ?item ?itemLabel
                 :where ~(conj where-clause
                               '[:minus [[?item (wdt :dissolved-abolished-or-demolished) ?_]]])
                 :limit 300])
               wd/query
               tc/dataset
               (tc/add-column :from-query id)))
            (->> (apply tc/concat))))


;; ## Inspecting, de-duping & displaying the resulting dataset

;; Inspect the dataset - expect 244 rows.
(tc/shape DS0)

;; De-dup the dataset.
(def DS (-> DS0
             (tc/fold-by [:item :itemLabel])))

;; Define a `display-table` helper fn.
^{::clerk/visibility :fold}
(defn display-table [ds]
  (clerk/table {"name" (-> ds :itemLabel vec)
                "QID" (-> ds :item vec)
                "from query" (->> ds
                                  :from-query
                                  (map (fn [coll] (str/join " , " coll)))
                                  vec)}))

;; Display items arising from > 1 query.
(-> DS 
    (tc/select-rows #(-> % :from-query count (> 1)))
    (tc/order-by :itemLabel)
    display-table)

;; Inspect the de-duped dataset - expect 238 rows.
(tc/shape DS)

;; Display the de-duped, final dataset.
(-> DS
    (tc/order-by :itemLabel)
    display-table)

