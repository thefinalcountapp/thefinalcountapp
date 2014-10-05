(ns thefinalcountapp.http.api
  (:require [compojure.core :refer [defroutes GET POST PUT ANY DELETE]]
            [com.stuartsierra.component :as component]
            [ring.middleware.defaults :as ring-defaults]
            [ring.middleware.transit :refer [wrap-transit-body]]
            [io.clojure.liberator-transit :as lt]
            [thefinalcountapp.time :as time]
            [thefinalcountapp.data.store :as store]
            [thefinalcountapp.data.schemas :as schemas]
            [thefinalcountapp.http.pubsub :as pubsub]
            [thefinalcountapp.utils :as utils]
            [schema.core :refer [check]]
            [liberator.core :refer [defresource]])
  (:import [org.joda.time DateTime]))

;; Resources
(def transit-handlers {DateTime time/joda-time-writer})

(def resource-defaults
  {:available-media-types ["application/transit+json"]
   :as-response (lt/as-response {:handlers transit-handlers})})


(defresource group-creation []
  resource-defaults
  :allowed-methods [:post]
  :malformed? (fn [ctx]
                (let [body (get-in ctx [:request :body])]
                  (when (nil? (check schemas/NewGroup body))
                    {::parsed-entity body})))
  :authorized? (fn [ctx]
                 (let [group (::parsed-entity ctx)
                       {{db ::db} :request} ctx]
                   (not (store/group-exists? db (:name group)))))
  :post! (fn [ctx]
           (let [group (::parsed-entity ctx)
                 {{db ::db} :request} ctx]
             {::entity (store/create-group db (:name group))}))
  :post-redirect? false
  :new? false
  :respond-with-entity? true
  :multiple-representations? false
  :handle-ok ::entity)


(defresource group-detail [group]
  resource-defaults
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (let [{{db ::db} :request} ctx]
                 (store/group-exists? db group)))
  :handle-ok (fn [ctx]
               (let [{{db ::db} :request} ctx]
                 (store/get-group db group))))


(defresource counter-detail [group counter-id]
  resource-defaults
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (let [{{db ::db} :request} ctx]
                 (store/counter-exists? db group counter-id)))
  :handle-ok (fn [ctx]
               (let [{{db ::db} :request} ctx]
                 (store/get-counter db group counter-id))))


(defresource counter-create [group]
  resource-defaults
  :allowed-methods [:post]
  :authorized? (fn [ctx]
                 (let [{{db ::db} :request} ctx]
                   (store/group-exists? db group)))
  :post! (fn [ctx]
           (let [req (:request ctx)
                 counter (:body req)
                 db (::db req)
                 created-counter (store/create-counter db group counter)]
             (pubsub/notify :counter/created group {:group group :id (:id created-counter)})
             {::entity created-counter}))
  :post-redirect? false
  :new? false
  :respond-with-entity? true
  :multiple-representations? false
  :handle-ok ::entity)


(defresource counter-update [group counter-id]
  resource-defaults
  :allowed-methods [:put]
  :exists? (fn [ctx]
             (let [{{db ::db} :request} ctx]
                 (store/counter-exists? db group counter-id)))
  :put! (fn [ctx]
          (let [req (:request ctx)
                counter (:body req)
                db (::db req)
                updated-counter (store/update-counter db group counter-id counter)]
            (pubsub/notify :counter/updated group {:group group :id counter-id})
            {::entity updated-counter}))
  :conflict? false
  :post-redirect? false
  :new? false
  :respond-with-entity? true
  :multiple-representations? false
  :handle-ok ::entity)


(defresource counter-delete [group counter-id]
  resource-defaults
  :allowed-methods [:delete]
  :exists? (fn [ctx]
             (let [{{db ::db} :request} ctx]
               (store/counter-exists? db group counter-id)))
  :delete! (fn [ctx]
             (let [{{db ::db} :request} ctx]
               (store/delete-counter db group counter-id)
               (pubsub/notify :counter/deleted group {:group group :id counter-id}))))

(defresource counter-increment [group counter-id]
  resource-defaults
  :allowed-methods [:post]
  :exists? (fn [ctx]
             (let [db (::db (:request ctx))]
               (store/counter-exists? db group counter-id)))
  :authorized? (fn [ctx]
                 (let [db (::db (:request ctx))]
                   (= :counter (:type (store/get-counter db group counter-id)))))
  :post! (fn [ctx]
           (let [db (::db (:request ctx))]
             (store/increment-counter db group counter-id)
             (pubsub/notify :counter/updated group {:group group :id counter-id})))
  :post-redirect? false
  :new? false
  :respond-with-entity? false)


(defresource counter-reset [group counter-id]
  resource-defaults
  :allowed-methods [:post]
  :exists? (fn [ctx]
             (let [db (::db (:request ctx))]
               (store/counter-exists? db group counter-id)))
  :post! (fn [ctx]
           (let [db (::db (:request ctx))]
             (store/reset-counter db group counter-id)
             (pubsub/notify :counter/updated
                            group
                            {:group group, :id counter-id})))
  :post-redirect? false
  :new? false
  :respond-with-entity? false)


;; Routes
(defroutes api-routes
  (POST "/api/counters" [] (group-creation))
  (GET "/api/counters/:group" [group] (group-detail group))
  (POST "/api/counters/:group" [group] (counter-create group))
  (GET "/api/counters/:group/:id" [group id] (counter-detail group (utils/uuid id)))
  (PUT "/api/counters/:group/:id" [group id] (counter-update group (utils/uuid id)))
  (POST "/api/counters/:group/:id/increment" [group id] (counter-increment group (utils/uuid id)))
  (POST "/api/counters/:group/:id/reset" [group id] (counter-reset group (utils/uuid id)))
  (DELETE "/api/counters/:group/:id" [group id] (counter-delete group (utils/uuid id))))


;; Component
(defn api-middleware [handler db]
  (fn [req]
    (handler (assoc req ::db db))))


(defrecord API [db]
  component/Lifecycle
  (start [this]
    (let [wrapped-api-routes (-> #'api-routes
                                 (wrap-transit-body {:encoding :json, :opts {:handlers transit-handlers}})
                                 (ring-defaults/wrap-defaults ring-defaults/api-defaults)
                                 (api-middleware db))]
      (assoc this :routes wrapped-api-routes)))

  (stop [this]
    (dissoc this :routes)))
