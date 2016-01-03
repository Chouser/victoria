(ns chouser.victoria
  (:require [clojure.browser.repl :as repl]
            [clojure.browser.net :as net]
            [clojure.browser.event :as event]
            [goog.json :as json]
            [goog.dom :as dom]
            [goog.color :as color]
            [goog.events :as events]))
(enable-console-print!)

(def xhr
  (doto (net/xhr-connection)
    (event/listen :complete
                  (fn [e]
                    (js/console.log "Response" (.getResponseText (.-target e)))))))

(def maxpos 1000)
(def ctrl-scale 2.0)
(def ctrls (atom {:ids {} :left {:pos 1000} :right {:pos 500}}))

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

(defn dirty! [ctrl-key]
  (schedule-draw #(js/requestAnimationFrame %)
                 (fn []
                   (set! (-> (dom/getElement "sails") .-style .-transform)
                         (str "rotate("
                              (-> @ctrls :left :pos - (+ 1000) (/ 1000) (* 80) (+ 10))
                              "deg)"))

                   (set! (-> (dom/getElement "rudder") .-style .-transform)
                         (str "rotate("
                              (-> @ctrls :right :pos - (+ 1000) (/ 1000) (* 70) (- 35))
                              "deg)"))))

  (when-not (= ctrl-key :init)
    (schedule-send #(js/setTimeout % 1000)
                   #(net/transmit xhr
                                  (str js/location "ctrl") "PUT"
                                  (-> {:sails (-> @ctrls :left :pos)
                                       :rudder (-> @ctrls :right :pos)}
                                      clj->js json/serialize)))))

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
                         (assoc ctrl-key {:pos old-pos
                                          :drag-offset (- (* height (/ old-pos maxpos ctrl-scale)) y)
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
               (let [pos (Math/round (* ctrl-scale (/ maxpos height) (+ y (:drag-offset ctrl))))]
                 (-> (if (= type :end)
                       (dissoc ctrl :x)
                       (assoc ctrl :x x))
                     (merge (cond
                             (< pos 0) {:pos 0, :drag-offset (- y)}
                             (> pos maxpos) {:pos maxpos, :drag-offset (- (/ height ctrl-scale) y)}
                             :else {:pos pos}))))))

      (when (= type :end)
        (swap! ctrls update :ids dissoc ctrl-key))
      (dirty! ctrl-key))))

(defn load []
  (when-let [main-elem (dom/getElement "main")]
    (dom/removeNode main-elem))

  (let [main-elem (dom/createDom "div" (js-obj "id" "main")
                    (dom/createDom "h1" nil (dom/createTextNode "Victoria"))
                    (dom/createDom "div" (js-obj "id" "sails" "class" "ctrl"))
                    (dom/createDom "div" (js-obj "id" "rudder" "class" "ctrl")))]
    (dom/append (.-body js/document) main-elem)

    (doto main-elem
      (events/listen "touchstart" touchstart)
      (events/listen "touchmove" (partial touchupdate :move))
      (events/listen "touchend"  (partial touchupdate :end))))

  (dirty! :init)
  (js/console.log "Load complete"))

(load)

(defonce conn (repl/connect "http://localhost:9000/repl"))