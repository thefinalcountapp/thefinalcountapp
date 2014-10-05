(ns thefinalcountapp.data.persistent
  (:require [com.stuartsierra.component :as component]
            [thefinalcountapp.data.store :as st]
            [thefinalcountapp.data.schemas :as sch]
            [schema.coerce :as sc]
            [jdbc.core :as j]
            [jdbc.types :as jt]
            [jdbc.transaction :as tx]
            [jdbc.pool.dbcp :as p]
            [cheshire.core :as json])
  (:import [org.postgresql.util PGobject]))


(defn counter->json [c]
  (json/generate-string c))


(def json-counter-coercer (sc/coercer sch/Counter sc/json-coercion-matcher))
(defn json->counter [j]
   (->
     (json/parse-string j true)
     json-counter-coercer))

(extend-protocol jt/ISQLType
  clojure.lang.IPersistentMap

  (as-sql-type [self conn]
    (doto (PGobject.)
      (.setType "json")
      (.setValue (counter->json self))))

  (set-stmt-parameter! [self conn stmt index]
    (.setObject stmt index (jt/as-sql-type self conn))))

(extend-protocol jt/ISQLResultSetReadColumn
  PGobject
  (from-sql-type [pgobj conn metadata i]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json->counter value)
        :else value))))


(defn map->counter [{:keys [id last_updated data]}]
  (merge {:id id :last-updated last_updated} data))


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
        (let [sql ["INSERT INTO groups (name) VALUES (?);" name]
              res (j/execute-prepared! conn sql {:returning [:id :name]})]
          (merge (first res)
                 {:counters []})))))

  (get-group [_ name]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn {:read-only true}
        (let [group-sql ["SELECT * FROM groups WHERE name = ?" name]
              counters-sql ["SELECT c.id, data FROM counters AS c JOIN groups AS g ON g.id = c.groupid WHERE g.name = ?" name]]
          (when-let [g (j/query-first conn group-sql)]
            (merge g {:counters (vec (map map->counter (j/query conn counters-sql)))}))))))

  (group-exists? [_ name]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn {:read-only true}
        (let [sql ["SELECT EXISTS(SELECT 1 FROM groups WHERE name = ?)" name]
              res (j/query-first conn sql)]
          (when res
            (:exists res))))))

  (create-counter [this group counter]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn
        (let [g (st/get-group this group)
              sql ["INSERT INTO counters (data, groupid) VALUES (?, ?);" counter (:id g)]
              res (j/execute-prepared! conn sql {:returning [:id :data :last_updated]})
              c (first res)]
          (map->counter c)))))

  (counter-exists? [_ group id]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn {:read-only true}
        (let [sql ["SELECT EXISTS(SELECT 1 FROM counters AS c JOIN groups AS g ON g.id = c.groupid WHERE c.id = ? AND g.name = ?)" id group]
              res (j/query-first conn sql)]
          (when res
            (:exists res))))))

  (get-counter [_ group id]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn {:read-only true}
        (let [sql ["SELECT * FROM counters AS c JOIN groups AS g ON g.id = c.groupid WHERE c.id = ? AND g.name = ?" id group]
              res (j/query-first conn sql)]
          (when res
            (merge {:id id :last-updated (:last_updated res)}
                   (:data res)))))))

  (update-counter [this group counter-id new-counter]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn
        (let [c (st/get-counter this group counter-id)
              g (st/get-group this group)
              data (dissoc (merge c new-counter) :id :last-updated)
              sql ["UPDATE counters SET data = ? WHERE id = ? AND groupid = ?;" data counter-id (:id g)]
              res (j/execute-prepared! conn sql)]
          (when (first res)
            data)))))

  (reset-counter [this group counter-id]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn
        (when-let [c (st/get-counter this group counter-id)]
          (case (:type c)
            :count-up  (let [g (st/get-group this group)
                             sql ["UPDATE counters SET last_updated = current_timestamp WHERE id = ? AND groupid = ?" counter-id (:id g)]]
                         (j/execute-prepared! conn sql))
            :streak  (let [g (st/get-group this group)
                           sql ["UPDATE counters SET last_updated = current_timestamp WHERE id = ? AND groupid = ?" counter-id (:id g)]]
                       (j/execute-prepared! conn sql))
            :counter   (st/update-counter this group counter-id {:value 0}))))))

  (increment-counter [this group counter-id]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn
        (when-let [c (st/get-counter this group counter-id)]
          (when (= (:type c) :counter
            (st/update-counter this group counter-id {:value (inc (:value c))})))))))

  (delete-counter [this group counter-id]
    (j/with-connection [conn dbspec]
      (tx/with-transaction conn
        (let [g (st/get-group this group)
              sql ["DELETE FROM counters WHERE id = ? AND groupid = ?;" counter-id (:id g)]]
         (j/execute-prepared! conn sql)))))

)

(defn persistent-store [dbspec]
  (map->PersistentStore {:dbspec (p/make-datasource-spec dbspec)}))
