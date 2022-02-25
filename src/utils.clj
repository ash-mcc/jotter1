(ns utils
  (:require [nextjournal.clerk :as clerk]))

;; Martin's custom viewer for Mermaid 
;; (a markdown-like syntax for creating diagrams from text, see https://mermaid-js.github.io/mermaid ). 
;; Note that the Mermaid library isn't bundled with Clerk. 
;; Instead, this code uses d3-require to load it at runtime.
(def mermaid {:pred string?
              :fetch-fn (fn [_ x] x)
              :render-fn '(fn [value]
                            (v/html
                             (when value
                               [d3-require/with {:package ["mermaid@8.14/dist/mermaid.js"]}
                                (fn [mermaid]
                                  [:div {:ref (fn [el] (when el
                                                         (.render mermaid (str (gensym)) value #(set! (.-innerHTML el) %))))}])])))})