(ns dev.mccue.repository.test
  (:require [clojure.test :as t]
            [dev.mccue.repository.module-info :as mi]))

(t/deftest module-info-parsing
  (t/testing "Module Info Parsing Round-Trips"
    (t/is (= {:name "example"
              :exports []
              :provides []
              :uses []
              :requires [{:module "java.base"
                          :mandated true}]}
             (mi/from-bytes
               (mi/to-bytes
                 {:name "example"
                  :exports []
                  :provides []
                  :uses []
                  :requires [{:module "java.base"
                              :mandated true}]})))))
  (t/testing "java.base require always added"
    (t/is (= {:name "example"
              :exports []
              :provides []
              :uses []
              :requires [{:module "java.base"
                          :mandated true}]}
             (mi/from-bytes
               (mi/to-bytes
                 {:name "example"
                  :exports []
                  :provides []
                  :uses []}))))
    (t/is (= {:name "example"
              :exports []
              :provides []
              :uses []
              :requires [{:module "example2"
                          :static true
                          :transitive true}
                         {:module "java.base"
                          :mandated true}]}
             (mi/from-bytes
              (mi/to-bytes
                {:name "example"
                 :exports []
                 :provides []
                 :uses []
                 :requires [{:module "example2"
                             :static true
                             :transitive true}]}))))))