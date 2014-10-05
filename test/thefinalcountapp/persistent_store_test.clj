(ns thefinalcountapp.persistent-store-test
  (:require [clojure.test :refer :all]
            [jdbc.core :as j]
            [thefinalcountapp.data.store :as store]
            [thefinalcountapp.data.persistent :as perstore]))

(def dbspec {:subprotocol "postgresql"
             :subname "//localhost:5432/thefinalcountapp_test"})

(defn make-store []
  (perstore/persistent-store dbspec))

(deftest groups
  (testing "A group can be created and retrieved"
    (let [store (make-store)
          name (str (gensym))
          group {:name name, :counters []}]
       (is (not (store/group-exists? store name)))
       (store/create-group store name)
       (is (= group (select-keys (store/get-group store name) (keys group))))
       (is (store/group-exists? store name)))))

(deftest counters
  (testing "A counter can be added to a group and retrieved"
    (let [store (make-store)
          name (str (gensym))
          _ (store/create-group store name)
          c (store/create-counter store name {:type :streak
                                              :color :blue
                                              :text "Daily commit"
                                              :value 42})
          cid (:id c)]
      (is (store/counter-exists? store name cid))
      (is (= c (select-keys (store/get-counter store name cid) (keys c))))))

  (testing "A counter can be deleted"
    (let [store (make-store)
          name (str (gensym))
          _ (store/create-group store name)
          c (store/create-counter store name {:type :streak
                                              :color :blue
                                              :text "Daily commit"
                                              :value 42})
          cid (:id c)]
      (is (store/counter-exists? store name cid))
      (store/delete-counter store name cid)
      (is (not (store/counter-exists? store name cid)))))

  (testing "A counter can be updated"
    (let [store (make-store)
          name (str (gensym))
          _ (store/create-group store name)
          c (store/create-counter store name {:type :streak
                                              :color :blue
                                              :text "Daily commit"
                                              :value 42})
          cid (:id c)]
       (store/update-counter store name cid {:value 0})
       (is (zero? (:value (store/get-counter store name cid))))))

  (testing "'count-up', 'times' and 'counter' counters can be reset"
    (let [store (make-store)
          name (str (gensym))
          _ (store/create-group store name)
          ; count-up
          u (store/create-counter store name {:type :count-up
                                              :color :blue
                                              :text "Drinking coffee"})
          uid (:id u)
          ; streak
          s (store/create-counter store name {:type :streak
                                              :color :blue
                                              :text "Daily commit"})
          sid (:id s)
          ; counter
          c (store/create-counter store name {:type :counter
                                              :color :blue
                                              :text "Breaking the build"
                                              :value 3})
          cid (:id c)]
      ; count-up
      (store/reset-counter store name uid)
      (is (not (= (:last-updated u)
                  (:last-updated (store/get-counter store name uid)))))
      ; streak
       (store/reset-counter store name sid)
       (is (not (= (:last-updated s)
                   (:last-updated (store/get-counter store name sid)))))
      ; counter
       (store/reset-counter store name cid)
       (is (zero? (:value (store/get-counter store name cid))))))

  (testing "'counter' counters can be incremented"
    (let [store (make-store)
          name (str (gensym))
          _ (store/create-group store name)

          c (store/create-counter store name {:type :counter
                                              :color :blue
                                              :text "Breaking the build"
                                              :value 3})
          cid (:id c)]
      ; counter
       (store/increment-counter store name cid)
       (is (= (inc (:value c))
              (:value (store/get-counter store name cid))))))

)
