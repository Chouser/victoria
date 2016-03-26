(ns chouser.victoria
  (:require [clojure.browser.repl :as repl]
            [clojure.browser.net :as net]
            [clojure.browser.event :as event]
            [goog.json :as json]
            [goog.dom :as dom]
            [goog.color :as color]
            [goog.events :as events]))
(enable-console-print!)

(def maxpos 1000)
(def ctrl-scale 2.0)
(def ctrls (atom {:ids {} :left {:pos 1000} :right {:pos 500}}))
(def config (atom nil))

(declare dirty!)

(def xhr
  (doto (net/xhr-connection)
    (event/listen :complete
                  (fn [e]
                    ;; (js/console.log "Response" (.getResponseText (.-target e)))
                    (let [resp (json/parse (.getResponseText (.-target e)))]
                      (when-let [config-val (.-config resp)]
                        (reset! config config-val)
                        (dirty! :init)
                        (js/console.log "Applied config")))))))

(def url
  (let [loc-str (str js/location)]
    (subs loc-str 0 (- (count loc-str) (count (.-hash js/location))))))

(defn xy [touch]
  [(.-clientX touch) (.-clientY touch)])

(defn collater []
  (let [id (atom :none)]
    (with-meta (fn [schedule execute]
                 (when (= :none @id)
                   (reset! id (schedule (fn []
                                          (reset! id :none)
                                          (execute))))))
      {:id id})))

(def schedule-draw (collater))
(def schedule-send (collater))

(defn scale [pos low high]
  (+ low (* (- high low) (/ pos 1000))))

(defn dirty! [ctrl-key]
  (when @config
    (schedule-draw
     #(js/requestAnimationFrame %)
     (fn []
       (set! (-> (dom/getElement "sails") .-style .-transform)
             (str "rotate("
                  (apply scale (-> @ctrls :left :pos)
                         (-> @config .-sails .-viz-range))
                  "deg)"))

       (set! (-> (dom/getElement "rudder") .-style .-transform)
             (str "rotate("
                  (apply scale (-> @ctrls :right :pos)
                         (-> @config .-rudder .-viz-range))
                  "deg)"))))

    (when-not (= ctrl-key :init)
      (schedule-send #(js/setTimeout % (-> @config .-servo-delay))
                     #(net/transmit xhr
                                    (str url "/ctrl") "POST"
                                    (-> {:sails (-> @ctrls :left :pos)
                                         :rudder (-> @ctrls :right :pos)}
                                        clj->js json/serialize))))))

(defn touchstart [event]
  (.preventDefault event)
  (.webkitRequestFullScreen js/document.documentElement)
  (let [container (dom/getElement "main")
        width (.-clientWidth container)
        height (.-clientHeight container)]
    (doseq [touch (prim-seq (.-changedTouches (.-event_ event)))]
      (let [[x y] (xy touch)
            [x0 x1] (map :x @ctrls)
            ctrl-key (cond
                      (or (and (nil? x1) (< x (/ width 2)))
                          (and x1 (< x x1)))
                      :left

                      (or (and (nil? x0) (> x (/ width 2)))
                          (and x0 (> x x0)))
                      :right)]

        #_(js/console.log touch)

        (when ctrl-key
          (swap! ctrls
                 (fn [ctrls]
                   (let [old-pos (-> ctrls ctrl-key :pos)]
                     (-> ctrls
                         (assoc ctrl-key
                           {:pos old-pos
                            :drag-offset (- (* (/ old-pos maxpos ctrl-scale)
                                               height) y)
                            :x x})
                         (assoc-in [:ids (.-identifier touch)] ctrl-key)))))
          #_(prn ctrl-key @ctrls))))))

(defn touchupdate [type event]
  (doseq [touch (prim-seq (.-changedTouches (.-event_ event)))]
    (let [ctrl-key (get-in @ctrls [:ids (.-identifier touch)])
          height (.-clientHeight (dom/getElement "main"))
          [x y] (xy touch)]
      (swap! ctrls update ctrl-key
             (fn [ctrl]
               (let [pos (Math/round (* ctrl-scale (/ maxpos height)
                                        (+ y (:drag-offset ctrl))))]
                 (-> (if (= type :end)
                       (dissoc ctrl :x)
                       (assoc ctrl :x x))
                     (merge (cond
                             (< pos 0) {:pos 0, :drag-offset (- y)}
                             (> pos maxpos) {:pos maxpos,
                                             :drag-offset (- (/ height
                                                                ctrl-scale) y)}
                             :else {:pos pos}))))))

      (when (= type :end)
        (swap! ctrls update :ids dissoc ctrl-key))
      (dirty! ctrl-key))))

(defn load []
  (when-let [main-elem (dom/getElement "main")]
    (dom/removeNode main-elem))

  (let [main-elem (dom/createDom "div" (js-obj "id" "main")
                    "Victoria"
                    (dom/createDom "div" (js-obj "id" "sails" "class" "ctrl"))
                    (dom/createDom "div" (js-obj "id" "rudder" "class" "ctrl")))]
    (dom/append (.-body js/document) main-elem)

    (doto main-elem
      (events/listen "touchstart" touchstart)
      (events/listen "touchmove" (partial touchupdate :move))
      (events/listen "touchend"  (partial touchupdate :end))))

  ;; request configs from server
  (net/transmit xhr (str url "/config") "GET")

  (dirty! :init)
  (js/console.log "Load complete"))

(load)

(when (re-find #"repl" (str js/location))
  (defonce conn (repl/connect "http://localhost:9000/repl")))
