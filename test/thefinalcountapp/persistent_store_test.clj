(ns thefinalcountapp.persistent-store-test
  (:require [clojure.test :refer :all]
            [thefinalcountapp.data.store :as store]
            [thefinalcountapp.data.persistent :as perstore]
            [com.stuartsierra.component :as component]))

(def dbspec {:subprotocol "postgresql"
             :subname "//localhost:5432/thefinalcountapp_test"})

(defn make-store []
  (perstore/persistent-store dbspec))

(deftest groups
  (testing "A group can be created and retrieved"
    (let [store (make-store)
          name "Kaleidos Team"
          group {:name name, :counters []}]
       (is (not (store/group-exists? store name)))
       (store/create-group store name)
       (is (= group (select-keys (store/get-group store name) (keys group))))
       (is (store/group-exists? store name))))

  (testing "A counter can go from clojure to json and viceversa"
    (let [c  {:type :streak, :text "Daily commit",:value 42}]
      (is (= c (perstore/json->counter (perstore/counter->json c))))
      ))

)
;  (testing "A counter can be added to a group, retrieved, updated and deleted"
;    (let [store (make-store)
;          gr "Kaleidos Team"
;          _ (store/create-group store name)
;          c (store/create-counter store gr {:type :streak
;                                            :text "Daily commit"
;                                            :value 42})
;          cid (:id c)]
;
;       (is (store/counter-exists? store gr cid))
;       (is (= c (store/get-counter store gr cid)))
;       (store/update-counter store gr cid {:value 0})
;       (is (zero? (:value (store/get-counter store gr cid))))
;       (store/delete-counter store gr cid)
;       (is (not (store/counter-exists? store gr cid)))))

;  (testing "'count-up', 'times' and 'counter' counters can be reset"
;    (let [store (make-store)
;          gr "Kaleidos Team"
;          _ (store/create-group store name)
;          u (store/create-counter store gr {:type :count-up
;                                            :text "Drinking coffee"})
;          uid (:id u)
;
;          s (store/create-counter store gr {:type :streak
;                                            :text "Daily commit"})
;          sid (:id s)
;
;          c (store/create-counter store gr {:type :counter
;                                            :text "Breaking the build"
;                                            :value 3})
;          cid (:id c)]
;      ; count-up
;       (store/reset-counter store gr uid)
;       (is (not (= (:last-updated u)
;                   (:last-updated (store/get-counter store gr uid)))))
;      ; streak
;       (store/reset-counter store gr sid)
;       (is (not (= (:last-updated s)
;                   (:last-updated (store/get-counter store gr sid)))))
;      ; counter
;       (store/reset-counter store gr cid)
;       (is (= 0 (:value (store/get-counter store gr cid))))))
;
;  (testing "'counter' counters can be incremented"
;    (let [store (make-store)
;          gr "Kaleidos Team"
;          _ (store/create-group store name)
;
;          c (store/create-counter store gr {:type :counter
;                                            :text "Breaking the build"
;                                            :value 3})
;          cid (:id c)]
;      ; counter
;       (store/increment-counter store gr cid)
;       (is (= (inc (:value c))
;              (:value (store/get-counter store gr cid))))))
;
;)
