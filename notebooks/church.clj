(ns church
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as xls]
            [clojure.data.csv :as csv]
            [tablecloth.api :as tc]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]))


(defn select-cell [sheet row-number col-id]
  (xls/select-cell (str col-id row-number) sheet))

;; The parameterised reading of a sheet.
(defn read-sheet [workbook sheet-name year first-row last-row surname-col title-forename-col total-col]
  (let [sheet (xls/select-sheet sheet-name workbook)]
    (for [row-number (range first-row (inc last-row))]
      (let [select-cell' (partial select-cell sheet row-number)
            m            {:surname        (-> surname-col select-cell' xls/read-cell)
                          :title-forename (-> title-forename-col select-cell' xls/read-cell)
                          :GBP            (-> total-col select-cell' xls/read-cell)
                          :year           year}]
        (if (and (str/blank? (:surname m)) (str/blank? (:title-forename m)))
          (assoc m :surname (str year "-" row-number))
          m)))))

;; Specify the Excel file.
(def filename "resources/church/F W O  2021.xlsx")

;; Specify where the data is on each sheet.
(def sheet-specs
  [["Sheet2017"  2017 3 143 "B" "C" "BI"]
   ["Sheet2018"  2018 3 143 "A" "B" "BH"]
   ["Sheet2019"  2019 3 137 "A" "B" "BF"]
   ["Sheet2020"  2020 3 138 "B" "C" "BM"]
   ["Sheet 2021" 2021 3 134 "B" "C" "BI"]])

;; Read the data from the Excel file.
(def DS0
  (let [workbook (xls/load-workbook filename)
        read-sheet' (partial read-sheet workbook)]
    (->> sheet-specs
         (map #(apply read-sheet' %))
         flatten)))

;; Display the data that has been read from the Excel file.
(pp/print-table DS0)

;; Put it into a table oriented data structure.
(def DS1 (tc/dataset DS0))

;; Display summary statistics.
(tc/info DS1)

;; Replace missing GBP values with 0s.
(def DS2 (tc/replace-missing DS1 :GBP :value 0))

;; Display summary statistics.
(tc/info DS2)

;; Specify the expected per-year totals.
(def expected [50032.90
               52484.70
               52846.80
               56554.15
               50882.80])

;; Calculate the actual per-year totals.
(def actual (-> DS2
                (tc/group-by :year)
                (tc/aggregate {:actual #(reduce + (% :GBP))})
                (tc/rename-columns {:$group-name :year})
                (tc/order-by :year)))

;; Display the expected against the actual.
(-> actual
    (tc/add-column :expected expected)
    (tc/reorder-columns [:year :expected :actual]))



(def coll2 (group-by (juxt :surname :title-forname) coll))

(def coll3
  (sort-by (juxt :surname :title-forname)
           (for [[[surname title-forname] coll] coll2]
             {:surname       surname
              :title-forname title-forname
              :2017          (->> coll (filter #(= 2017 (:year %))) first :GBP)
              :2018          (->> coll (filter #(= 2018 (:year %))) first :GBP)
              :2019          (->> coll (filter #(= 2019 (:year %))) first :GBP)
              :2020          (->> coll (filter #(= 2020 (:year %))) first :GBP)
              :2021          (->> coll (filter #(= 2021 (:year %))) first :GBP)})))

(pp/print-table coll3)


(let [headers [:surname :title-forname :2017 :2018 :2019 :2020 :2021]
      header-row (map name headers)
      data-rows (->> coll3
                     (map #(map % headers)))
      file (io/file "church-year-totals.csv")]
  (io/make-parents file)
  (with-open [wtr (io/writer file)]
    (csv/write-csv wtr (cons header-row data-rows))))





