(ns thefinalcountapp.events
  (:require-macros
   [cljs.core.async.macros :as async :refer (go go-loop)])
  (:require
   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente  :as sente :refer (cb-success?)]
  ))


(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
)

;; Events
(defmulti event-handler :id)

(defmethod event-handler :chsk/recv
  [{:as ev :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [[ev payload] ?data]
    (case (first ?data)
      :group/subscribed (do (.log js/console "Subscribed to " payload) ; FIXME: just for testing
                            (unsubscribe "kaleidos-team"))
      :group/unsubscribed (.log js/console "Unsubscribed from " payload)
      :counter/updated (.log js/console "Counter updated " payload)
      :counter/created (.log js/console "Counter created " payload))))


(defmethod event-handler :default
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (.log js/console "ev ")
  (.log js/console id))

;;; Client events
;; TODO: wait for open conn and uid available
(defn subscribe [group]
  (chsk-send! [:group/subscribe {:group group :uid (:uid @chsk-state)}]))

(defn unsubscribe [group]
  (chsk-send! [:group/unsubscribe {:group group :uid (:uid @chsk-state)}]))

(go (<! (async/timeout 10000))
    (subscribe "kaleidos-team"))


(sente/start-chsk-router! ch-chsk event-handler)
