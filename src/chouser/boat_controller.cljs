(ns chouser.boat-controller
  (:require [clojure.browser.repl :as repl]
            [goog.dom :as dom]
            [goog.color :as color]
            [goog.events :as events]))
(enable-console-print!)

(def ctrls (atom {:ids {} :left {:pos 0} :right {:pos 0}}))

(defn xy [touch]
  [(.-clientX touch) (.-clientY touch)])

(defn touchstart [event]
  (let [container (dom/getElement "main")
        width (.-clientWidth container)
        height (.-clientHeight container)]
    (doseq [touch (prim-seq (.-changedTouches (.-event_ event)))]
      (let [[x y] (xy touch)
            ctrl-key (cond
                      (or (and (nil? (:id (@ctrls 1)))
                               (< x (/ width 2)))
                          (and (:id (@ctrls 1))
                               (< x (:x (@ctrls 1)))))
                      :left

                      (or (and (nil? (:id (@ctrls 0)))
                               (> x (/ width 2)))
                          (and (:id (@ctrls 0))
                               (> x (:x (@ctrls 0)))))
                      :right)]

        #_(.log js/console touch)

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

(defn dirty! [ctrl-key]
  (let [dom-id ({:left "sails" :right "rudder"} ctrl-key)]
    (set! (-> (dom/getElement dom-id) .-style .-top) (str (-> @ctrls ctrl-key :pos) "px")))
  #_(prn ctrl-key @ctrls))

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

  (let [body (-> js/window .-document .-body)
        main-elem (dom/createDom "div" (js-obj "id" "main"))]
    (dom/append body main-elem)

    (dom/append main-elem (dom/createDom "div" (js-obj "id" "sails" "class" "ctrl")))
    (dom/append main-elem (dom/createDom "div" (js-obj "id" "rudder" "class" "ctrl")))

    (events/listen main-elem "touchstart" touchstart)
    (events/listen main-elem "touchmove" touchmove)
    (events/listen main-elem "touchend" touchend)))

(load)

(prn :rockin4)

(defonce conn (repl/connect "http://localhost:9000/repl"))
