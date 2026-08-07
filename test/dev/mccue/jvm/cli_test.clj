(ns dev.mccue.jvm.cli-test
  (:require [clojure.string :as string]
            [clojure.test :as t]
            [clojure.xml :as xml]
            [dev.mccue.jvm.cli :as cli])
  (:import (java.io ByteArrayInputStream)
           (java.nio.charset StandardCharsets)))

(defn x
  [s]
  (xml/parse (ByteArrayInputStream. (String/.getBytes s StandardCharsets/UTF_8))))

(t/deftest xml-parsing-test
  (binding [cli/*crash!* (fn [& args]
                           (throw (Exception. (string/join "" (map str args)))))]
    (t/testing "Parse blank xml"
      (t/is (= {:modules [] :providers [] :index nil}
               (cli/interpret-xml
                 (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
</jigsaw>")))))
    (t/testing "One provider"
      (t/is (= {:modules [] :providers [{:handle "test" :did "abc"}] :index nil}
               (cli/interpret-xml
                 (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
  <provider> <handle> test </handle> <did> abc </did> </provider>
</jigsaw>")))))
    (t/testing "One module"
      (t/is (= {:modules [{:provider "ethan-test.bsky.social"
                           :name     "dev.mccue.json"
                           :version  "2024.11.20"}]
                :providers []
                :index nil}
               (cli/interpret-xml
                 (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
    <module>
        <provider>  ethan-test.bsky.social</provider>
        <name>dev.mccue.json  </name>
        <version>2024.11.20</version>
    </module>
</jigsaw>")))))

    (t/testing "Multiple providers and multiple modules"
      (t/is (= {:modules   [{:provider "ethan-test.bsky.social"
                             :name     "dev.mccue.json"
                             :version  "2024.11.20"}
                            {:provider "mccue.dev"
                             :name     "vegeta"
                             :version  "12.13.0"}
                            {:provider "mccue.dev"
                             :name     "com.fasterxml.jackson.databind"
                             :version  "2.22.0"}
                            {:provider "mccue.dev"
                             :name     "com.fasterxml.jackson.core"
                             :version  "2.22.0"}
                            {:provider "mccue.dev"
                             :name     "com.fasterxml.jackson.annotation"
                             :version  "2.22"}
                            {:provider "ethan-test.bsky.social"
                             :name     "org.jspecify"
                             :version  nil}]


                :providers [{:handle "ethan-test.bsky.social"
                             :did    "did:plc:2oip3ubsbe2pc7tmbnwsm3i7"}
                            {:handle "mccue.dev"
                             :did    "did:plc:dt7fth2hmap6wya7uyyl2g3v"}]
                :index nil}
               (cli/interpret-xml
                 (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
    <provider>
        <handle>ethan-test.bsky.social</handle>
        <did>did:plc:2oip3ubsbe2pc7tmbnwsm3i7</did>
    </provider>

    <module>
        <provider>ethan-test.bsky.social</provider>
        <name>dev.mccue.json</name>
        <version>2024.11.20</version>
    </module>

    <module>
        <provider>mccue.dev</provider>
        <name>vegeta</name>
        <version>12.13.0</version>
    </module>

    <module>
        <provider>mccue.dev</provider>
        <name>com.fasterxml.jackson.databind</name>
        <version>2.22.0</version>
    </module>


    <provider>
        <handle>mccue.dev</handle>
        <did>did:plc:dt7fth2hmap6wya7uyyl2g3v</did>
    </provider>

    <module>
        <provider>mccue.dev</provider>
        <name>com.fasterxml.jackson.core</name>
        <version>2.22.0</version>
    </module>

    <module>
        <provider>mccue.dev</provider>
        <name>com.fasterxml.jackson.annotation</name>
        <version>2.22</version>
    </module>

    <module>
        <provider>ethan-test.bsky.social</provider>
        <name>org.jspecify</name>
    </module>
</jigsaw>
")))))
    (t/testing "Attributes syntax"
      (t/testing "One provider"
        (t/is (= {:modules [] :providers [{:handle "test" :did "abc"}] :index nil}
                 (cli/interpret-xml
                   (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
  <provider handle=\"  test\" did=\"abc  \" />
</jigsaw>")))))

      (t/testing "One module"
        (t/is (= {:modules [{:provider "aaa"
                             :name     "bbb"
                             :version  "5"}] :providers [] :index nil}
                 (cli/interpret-xml
                   (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
    <module provider=\"aaa\" name=\"   bbb  \" version=\"5\" />
</jigsaw>")))))
      (t/testing "Mix and match is a no go"
        (t/is (thrown? Exception (cli/interpret-xml
                                   (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
    <module provider=\"aaa\" name=\"   bbb  \">
        <version>5</version>
    </module>
</jigsaw>"))))
        (t/is (thrown? Exception (cli/interpret-xml
                                   (x "<?xml version='1.0' encoding='UTF-8'?>
<jigsaw>
  <provider did=\"abc  \">
     <handle>...</handle>
  </provider>
</jigsaw>"))))))))


