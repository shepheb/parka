(ns parka.core-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [parka.api :as sut]))


(defn- test-parse [p input]
  (sut/parse-dynamic p "<test>" input))

(defn- error
  [l c base]
  {:error (merge {:parka/parse-error true
                  :parka/loc         (str "<test> line " l " col " c)}
                 base)})

(defn- error-msg
  [l c msg]
  (error l c {:parka/message msg}))

(defn- expected [l c exps]
  (-> (error l c nil)
      (assoc-in [:error :parka/expectations] exps)))

(deftest test-lit
  (is (= {:success "foo"} (test-parse "foo" "foo")))
  (is (= (expected 1 0 ["'foo'"])
         (test-parse "foo" "for"))))

#_(deftest test-lit-ic
  (is (= "foo" (test-parse (sut/lit-ic "foo") "foo")))
  (is (= "foo" (test-parse (sut/lit-ic "foo") "FOO")))
  (is (thrown-with-msg? Exception #"expected literal 'foo'"
                        (test-parse (sut/lit "foo") "for"))))

(deftest test-sets
  (is (= {:success "f"} (test-parse #{\f} "f")))
  (is (= {:success "f"
          :tail    "o"} (test-parse #{\f} "fo")))
  (is (= (expected 1 0 ["[abc]"])
         (test-parse #{\a \b \c} "f"))))

(deftest test-one-of
  (let [p (sut/one-of "abc")]
    (is (= {:success "a"} (test-parse p "a")))
    (is (= {:success "b"} (test-parse p "b")))
    (is (= {:success "c"} (test-parse p "c")))
    (is (= (expected 1 0 ["[abc]"])
           (test-parse p "")))
    (is (= (expected 1 0 ["[abc]"])
           (test-parse p "d")))))

(deftest test-star
  (let [p (sut/* \a)]
    (is (= {:success ["a" "a" "a" "a"]
            :tail    "b_c"}            (test-parse p "aaaab_c")))
    (is (= {:success ["a"]
            :tail    "b_cX"}           (test-parse p "ab_cX")))
    (is (= {:success []
            :tail    "X"}              (test-parse p "X")))))

(deftest test-plus
  (let [p (sut/+ \a)]
    (is (= {:success ["a" "a" "a" "a"]
            :tail    "b_c"}            (test-parse p "aaaab_c")))
    (is (= {:success ["a"]
            :tail    "b_cX"}           (test-parse p "ab_cX")))
    (is (= (expected 1 0 ["a"])        (test-parse p "X")))))

(deftest test-alt
  (testing "basics"
    (let [p (sut/alt "foo" "bar" "baz")]
      (is (= {:success "foo"} (test-parse p "foo")))
      (is (= {:success "bar"} (test-parse p "bar")))
      (is (= {:success "baz"} (test-parse p "baz")))
      (is (= (expected 1 0 ["'foo'" "'bar'" "'baz'"])
             (test-parse p "asdf")))))

  (testing "backtracking sequences"
    (let [p (sut/alt
              ["0x" (sut/* (sut/one-of "0123456789abcdefABCDEF"))]
              (sut/* (sut/one-of "0123456789")))]
      (is (= {:success ["1" "2"]}
             (test-parse p "12")))
      (is (= {:success ["0x" ["1" "2" "a" "B" "3"]]}
             (test-parse p "0x12aB3"))))))

(deftest test-and
  (let [p ["a" (sut/and "bbb") (sut/* "b")]]
    (is (= {:success ["a" nil ["b" "b" "b"]]}
           (test-parse p "abbb")))
    (is (= {:success ["a" nil ["b" "b" "b" "b" "b"]]}
           (test-parse p "abbbbb")))
    (is (= (expected 1 1 ["'bbb'"])
           (test-parse p "abb")))))

(deftest test-not
  (let [p ["a" (sut/not "b") sut/any]]
    (is (= {:success ["a" nil "c"]
            :tail    "d"}
           (test-parse p "acd")))
    (is (= (expected 1 1 ["not 'b'"])
           (test-parse p "abd")))))

(deftest test-?
  (let [p [(sut/? "a") "b"]]
    (is (= {:success ["a" "b"]} (test-parse p "ab")))
    (is (= {:success [nil "b"]} (test-parse p "b")))))

(deftest test-eof
  (let [base ["a" (sut/* \b)]
        eofd (conj base sut/eof)]
    (is (= {:success ["a" ["b" "b" "b"]]
            :tail    "c"}
           (test-parse base "abbbc")))
    (is (= {:success ["a" ["b" "b" "b"] nil]}
           (test-parse eofd "abbb")))
    (is (= (expected 1 4 ["EOF"])
           (test-parse eofd "abbbc")))))

(deftest test-expecting
  (is (= (expected 1 0 ["'a'"])
         (test-parse "a" "b")))
  (is (= (expected 1 0 ["better grades"])
         (test-parse (sut/expecting "a" "better grades") "b"))))

(deftest test-range
  (let [p (sut/range \a \z)]
    (is (= {:success "d"} (test-parse p "d")))
    (is (= {:success "a"} (test-parse p "a")))
    (is (= {:success "z"} (test-parse p "z")))
    (is (= (expected 1 0 ["[a-z]"])
           (test-parse p "A")))))

#_(deftest test-many-min
  (let [p (sut/many-min 3 (sut/alt (sut/lit "_")
                                   (sut/span \a \z)))]
    (is (= [\a \b "_" \c] (test-parse p "ab_c")))
    (is (thrown-with-msg? Exception #"minimum 3" (test-parse p "ab")))))

#_(deftest test-many-drop
  (is (= nil (test-parse (sut/many-drop (sut/one-of " \t\r\n"))
                         "   \t\t  \n\t\t  ")))
  (is (= ["a" nil "b"]
         (test-parse (sut/pseq
                       (sut/lit "a")
                       (sut/many-drop (sut/one-of " \t\r\n"))
                       (sut/lit "b"))
                     "a   \t\t  \n\t\t  b"))))

#_(deftest test-sep-by
  (let [p (sut/sep-by (sut/lit "a") (sut/lit "b"))]
    (is (= [] (test-parse p "")))
    (is (= ["a"] (test-parse p "a")))
    (is (= ["a" "a"] (test-parse p "aba")))
    (is (= ["a" "a" "a" "a"] (test-parse p "abababa")))
    (is (= {:pos 3 :value ["a" "a"]}
           (test-partial p "abab")))
    (is (= {:pos 0 :value []}
           (test-partial p "bb")))))

#_(deftest test-sep-by1
  (let [p (sut/sep-by1 (sut/lit "a") (sut/lit "b"))]
    (is (thrown-with-msg? Exception #"expected at least 1:"
                          (test-parse p "")))
    (is (= ["a"] (test-parse p "a")))
    (is (= ["a" "a"] (test-parse p "aba")))
    (is (= ["a" "a" "a" "a"] (test-parse p "abababa")))
    (is (= {:pos 3 :value ["a" "a"]}
           (test-partial p "abab")))))

#_(deftest test-many-till
  (testing "basics"
    (let [p (sut/pseq-at
              1 (sut/lit "\"")
              (sut/stringify (sut/many-till sut/any (sut/lit "\""))))]
      (is (= "abc" (test-parse p "\"abc\"")))
      (is (= ""    (test-parse p "\"\"")))
      (testing "unterminated string"
        (is (thrown-with-msg? Exception #"expected literal '\"'"
                              (test-parse p "\"ab"))))))
  (testing "bad inner"
    (let [p (sut/between (sut/lit "a") (sut/lit "c")
                         (sut/many (sut/lit "b")))]
      (is (= ["b" "b" "b"] (test-parse p "abbbc")))
      (is (thrown-with-msg? Exception #"expected literal 'c'"
          (test-parse p "abbdc"))))))

#_(deftest test-string-autoupgrade
  (is (= "a" (test-parse "a" "a")))
  (is (= ["a" "b" "c"] (test-parse (sut/pseq "a" (sut/lit "b") "c") "abc"))))

#_(deftest test-symbol-autoupgrade
  (let [g (sut/grammar {:start (sut/pseq "a" :b (sut/sym :c))
                        :b     "b"
                        :c     (sut/lit-ic "c")})]
    (is (= ["a" "b" "c"] (sut/parse-str g "<test>" "abc")))))

