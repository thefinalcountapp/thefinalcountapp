(ns thefinalcountapp.http.pubsub
  (:require [taoensso.sente :as sente]
            [compojure.core :refer [defroutes GET POST]]
            [thefinalcountapp.utils :as utils]
            [clojure.core.async :as async :refer [go <! >! go-loop]]
            [ring.middleware.defaults]
            [com.stuartsierra.component :as component]))

;; Pubsub channel
(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {:user-id-fn (fn [_] (utils/uuid))})]
  ;; Ring handlers
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  ;; Channels
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  ;; Connected clients
  (def connected-uids                connected-uids) ; Watchable, read-only atom
)


;; Subscriptions
(def subscriptions (atom {}))

(add-watch connected-uids :unsubscribe-on-disconnect (fn [key reference old-state new-state]
                                                       (doseq [uid (clojure.set/difference (set (:any old-state)) (set (:any new-state)))]
                                                         (swap! subscriptions dissoc uid))))


(defn subscribe [uid group]
  (swap! subscriptions (fn [subs]
                         (update subs uid (fnil #(conj % group) #{})))))

(defn subscribed? [uid group]
  (if-let [subs (@subscriptions uid)]
    (contains? subs group)
    false))

(defn unsubscribe [uid group]
  (when (contains? @subscriptions uid)
    (if (= 1 (count  (@subscriptions uid)))
      (swap! subscriptions (fn [subs]
                             (dissoc subs uid)))
      (swap! subscriptions (fn [subs]
                             (update subs uid #(disj % group)))))))


;; Notifications
(defn notify [ev group data]
  (doseq [uid (filter #(subscribed? % group) (:any @connected-uids))]
    (chsk-send! uid [ev data])))


;; Routes
(defroutes pubsub-routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req)))


;; Events
(defmulti event-handler :id)

(defmethod event-handler :group/subscribe
  [{:keys [?data]}]
  (let [{:keys [group uid]} ?data]
    (subscribe uid group)
    (chsk-send! uid [:group/subscribed group])))


(defmethod event-handler :group/unsubscribe
  [{:keys [?data]}]
  (let [{:keys [group uid]} ?data]
    (unsubscribe uid group)
    (chsk-send! uid [:group/unsubscribed group])))

(defmethod event-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  nil)


;; Component
(defrecord PubSub [shutdown]
  component/Lifecycle
  (start [this]
    (assoc this :routes (ring.middleware.defaults/wrap-defaults pubsub-routes ring.middleware.defaults/site-defaults)
                :shutdown (sente/start-chsk-router! ch-chsk event-handler)))

  (stop [this]
    (shutdown)
    (dissoc this :routes :shutdown)))


(defn pubsub []
  (map->PubSub {}))
