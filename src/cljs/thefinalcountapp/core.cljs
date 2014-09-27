(ns thefinalcountapp.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent :refer [atom]]
            [thefinalcountapp.http :as http]
            [thefinalcountapp.components.counter :refer [counter]]
            [cljs.core.async :refer [<!]]))

(def state (atom {:group "kaleidos-team"
                  :counters []
                  :displaying 0}))

;; (defn main-component []
;;   [:div.content
;;    [counter]
;;    [:ul (for [item (:counters @state)] ^{:key item} [:li "Counter: " (:text item)])]
;;    [:p (str ">>" (count (:counters @state)))]])

;(defn main-component []
;  [:div.content
;   [:svg {:width "100%" :height "100%"}
;    [:g {:transform "translate(680, 300)"}
;     [:g {:transform "scale(1.0, 1.0)"} [counter]]]]])

(defn counter-list []
  (.log js/console (count (:counters @state)))
  (if (>= (count (:counters @state)) 3)
    (let [idx (:displaying @state)
          center-counter (nth (:counters @state) idx)
          left-idx (if (< (dec idx) 0) (dec (count (:counters @state))) (dec idx))
          left-counter (nth (:counters @state) left-idx)
          right-idx (if (> (inc idx) (dec (count (:counters @state)))) 0 (inc idx))
          right-counter (nth (:counters @state) right-idx)]
      [:g
       [:g {:transform "translate(350, 120) scale(0.8, 0.8)" :style #js {"opacity" "0.4"}}
        [counter left-counter]]
       [:g {:transform "translate(1080, 120) scale(0.8, 0.8)" :style #js {"opacity" "0.4"}}
        [counter right-counter]]
       [:g {:transform "translate(640, 80)" :style #js {"opacity" "1.0"}}
        [counter center-counter]]])))

(defn main-component []
  [:div
   [:div
    [:img {:class "logo" :src "/images/logo.png" :alt "The Final Count App"}]
    [:div {:class "content-buttons"}
     [:a {:class "group" :href "#"} "Kaleidos Team"]
     [:a {:class "add-btn" :href "#"} "+"]]]
   [:div#content
    [:svg {:width "100%" :height "100%"}
     [counter-list]
     ]]])


(defn ^:export run []
  (go (let [response (<! (http/get "/api/counters/kaleidos-team"))]
        (.log js/console (str response))
        (swap! state #(assoc % :counters (-> response :data :counters)))))
  (reagent/render-component [main-component]
                            (. js/document (getElementById "main"))))
