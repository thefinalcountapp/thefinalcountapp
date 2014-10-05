(ns thefinalcountapp.data.persistent
  (:require [com.stuartsierra.component :as component]
            [thefinalcountapp.data.store :as st]
            [thefinalcountapp.data.schemas :as sch]
            [schema.coerce :as sc]
            [jdbc.core :as j]
            [jdbc.transaction :as tx]
            [jdbc.pool.dbcp :as p]
            [cheshire.core :as json]))


(defn counter->json [c]
  (json/generate-string c))


(def json-counter-coercer (sc/coercer sch/Counter sc/json-coercion-matcher))
(defn json->counter [j]
   (->
     (json/parse-string j true)
     json-counter-coercer))


(defrecord PersistentStore [dbspec]
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    this)

  st/Store
  (create-group [_ name]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn
        (let [sql "INSERT INTO groups (name) VALUES (?);"
              res (j/execute-prepared! conn sql [name] {:returning [:id :name]})]
          (merge (first res)
                 {:counters []})))))

  (get-group [_ name]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn {:read-only true}
        (let [group-sql ["SELECT * FROM groups WHERE name = ?" name]
              counters-sql ["SELECT * FROM counters JOIN groups AS g ON g.id = groupid WHERE g.name = ?" name]]
          (when-let [g (j/query-first conn group-sql)]
            (merge g {:counters (vec (map json->counter (j/query conn counters-sql)))}))))))

  (group-exists? [_ name]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn {:read-only true}
        (let [sql ["SELECT EXISTS(SELECT 1 FROM groups WHERE name = ?)" name]
              res (j/query-first conn sql)]
          (when res
            (:exists res))))))

  (create-counter [_ group counter])

  (counter-exists? [_ group id])

  (get-counter [_ group id])

  (update-counter [_ group counter-id new-counter])

  (increment-counter [_ group counter-id])

  (reset-counter [_ group counter-id])

  (delete-counter [_ group counter-id])
)

(defn persistent-store [dbspec]
  (map->PersistentStore {:dbspec (p/make-datasource-spec dbspec)}))
