(ns thefinalcountapp.data.schemas
  #+clj
  (:require [schema.core :as s])
  #+cljs
  (:require [schema.core :as s :include-macros true]))


(def NewGroup
  {:name s/Str})

(def DBCounter
  {:type (s/enum :count-up
                 :streak
                 :counter)
  :color (s/enum :red
                 :pink
                 :blue
                 :purple
                 :yellow)
  :text s/Str
  (s/optional-key :value) s/Int
  (s/optional-key :last-updated) s/Inst
  (s/optional-key :public-reset) s/Bool
  (s/optional-key :public-plus) s/Bool})
(def Counter DBCounter)


(def NewCounter
  {:type (s/enum :count-up
                 :streak
                 :counter)
   :text s/Str
   :color (s/enum :red
                  :pink
                  :blue
                  :purple
                  :yellow)
  (s/optional-key :value) s/Int
  (s/optional-key :public-reset) s/Bool
  (s/optional-key :public-plus) s/Bool})


(def CounterUpdate
  {(s/optional-key :text) s/Str
   (s/optional-key :value) s/Int
   (s/optional-key :public-reset) s/Bool
   (s/optional-key :public-plus) s/Bool})
