(ns chouser.boat-controller
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

(def ctrls (atom {:ids {} :left {:pos 0} :right {:pos 0}}))

(defn xy [touch]
  [(.-clientX touch) (.-clientY touch)])

(defn touchstart [event]
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
                   (let [pos (-> ctrls ctrl-key :pos)]
                     (prn (- y pos))
                     (-> ctrls
                         (assoc ctrl-key {:pos pos
                                          :drag-offset (- pos y)
                                          :x x})
                         (assoc-in [:ids (.-identifier touch)] ctrl-key)))))
          #_(prn ctrl-key @ctrls))))))

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
                 #(doseq [[ctrl-key dom-id] {:left "sails", :right "rudder"}]
                    (set! (-> (dom/getElement dom-id) .-style .-top)
                          (str (-> @ctrls ctrl-key :pos) "px"))))

  (schedule-send #(js/setTimeout % 1000)
                 #(net/transmit xhr
                                (str js/location "ctrl") "PUT"
                                (-> {:sails (-> @ctrls :left :pos)
                                     :rudder (-> @ctrls :right :pos)}
                                    clj->js json/serialize))))

(defn touchmove [event]
  (doseq [touch (prim-seq (.-changedTouches (.-event_ event)))]
    (let [ctrl-key (get-in @ctrls [:ids (.-identifier touch)])
          [x y] (xy touch)]
      (swap! ctrls update ctrl-key
             (fn [ctrl]
               (assoc ctrl
                 :pos (+ y (:drag-offset ctrl))
                 :x x)))
      (dirty! ctrl-key))))

(defn touchend [event]
  (doseq [touch (prim-seq (.-changedTouches (.-event_ event)))]
    (let [ctrl-key (get-in @ctrls [:ids (.-identifier touch)])
          [x y] (xy touch)]
      (swap! ctrls update ctrl-key
             (fn [ctrl]
               {:pos (+ y (:drag-offset ctrl))}))
      (swap! ctrls update :ids dissoc ctrl-key)
      (dirty! ctrl-key))))

(defn load []
  (when-let [main-elem (dom/getElement "main")]
    (dom/removeNode main-elem))

  (let [body (.-body js/document)
        main-elem (dom/createDom "div" (js-obj "id" "main"))]
    (dom/append body main-elem)

    (dom/append main-elem (dom/createDom "div" (js-obj "id" "sails" "class" "ctrl")))
    (dom/append main-elem (dom/createDom "div" (js-obj "id" "rudder" "class" "ctrl")))

    (events/listen main-elem "touchstart" touchstart)
    (events/listen main-elem "touchmove" touchmove)
    (events/listen main-elem "touchend" touchend))

  (js/console.log "Load complete"))

(load)

(defonce conn (repl/connect "http://localhost:9000/repl"))
