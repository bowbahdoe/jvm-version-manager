(ns dev.mccue.gui
  (:require [seesaw.core :as seesaw]
            [clojure.java.io :as io])
  (:import (java.awt Frame Taskbar Taskbar$Feature Toolkit)
           (java.lang Module ModuleLayer)
           (java.util.stream Stream)
           (javax.swing JFrame JOptionPane)
           (java.util Set)
           (javax.swing.event ListSelectionEvent)))

(def boot-layer-modules
  (->> (ModuleLayer/boot)
       (ModuleLayer/.modules)

       (sort-by Module/.getName)
       (vec)))

(defn set-taskbar-image!
  [image]
  (let [taskbar (Taskbar/getTaskbar)]
    (when (Taskbar/.isSupported taskbar Taskbar$Feature/ICON_IMAGE)
      (Taskbar/.setIconImage taskbar image))))

(defn start!
  []
  (let [icon (-> (Toolkit/getDefaultToolkit)
                 (Toolkit/.getImage (io/resource "duke.png")))]
    (set-taskbar-image! icon)
    (let [root-frame (seesaw/frame :width 500
                                   :height 500)
          display!   (fn disp [content]
                       (seesaw/config! root-frame :content content)
                       content)
          names      (mapv Module/.getName boot-layer-modules)
          dropdown   (seesaw/combobox :model ["JDK"])
          listbox    (seesaw/listbox :model names)

          left-pane  (seesaw/border-panel
                       :north dropdown
                       :center (seesaw/scrollable listbox))
          left-sized (seesaw/config! left-pane :preferred-size [260 :by 1])

          module-info-panel-name (seesaw/label "test")
          module-info-panel-requires (seesaw/label "Requires:")
          module-info-panel-exports (seesaw/label "Exports:")
          module-info-panel (seesaw/vertical-panel
                              :items [module-info-panel-name
                                      module-info-panel-requires
                                      module-info-panel-exports]
                              :visible? false)


          right-pane (seesaw/border-panel
                       :north module-info-panel
                       :preferred-size [260 :by 1])

          _          (seesaw/listen listbox :selection
                                    (fn [e]
                                      (seesaw/config! module-info-panel-name
                                                      :text (nth names (ListSelectionEvent/.getFirstIndex e)))
                                      (seesaw/config! module-info-panel :visible? true)))
          content    (seesaw/border-panel
                       :west left-sized
                       :center right-pane)]
      (display! content)
      (seesaw/show! root-frame))))

#_#_button     (seesaw/button
                     :text "Click Me!"
                     :listen [:action (fn [& _]
                                        (display! (seesaw/label "Test")))])


#_(-> (JOptionPane/getRootFrame)
      (Frame/.setIconImage
        (-> (Toolkit/getDefaultToolkit)
            (Toolkit/.getImage (io/resource "duke.png")))))

(comment
  (start!)

  (->> (ModuleLayer/boot)
       (ModuleLayer/.modules)
       (mapv Module/.getName)))

;;      ModuleLayer.boot().modules().stream()
;            .map(Module::getName)
;            .sorted()
;            .forEach(System.out::println);