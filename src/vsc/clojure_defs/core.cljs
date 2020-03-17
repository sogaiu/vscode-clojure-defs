(ns vsc.clojure-defs.core
  (:require
    [goog.object :as go]
    [promesa.core :as pc]
    ["path" :as path]
    ["vscode" :as vscode]
    ["web-tree-sitter" :as Parser]))

(def clj-parser
  (atom nil))

(def clj-lang
  (atom nil))

(def decos
  (atom nil))

(def trees
  (atom {}))

(defn node-seq
  [node]
  (tree-seq
    #(< 0 (.-childCount ^js %)) ; branch?
    #(.-children ^js %) ; children
    node)) ; root

(defn make-range
  [^js node]
  (new vscode/Range
    (go/get (.-startPosition node) "row")
    (go/get (.-startPosition node) "column")
    (go/get (.-endPosition node) "row")
    (go/get (.-endPosition node) "column")))

(defn make-location
  [^js doc a-range]
  #js {"range" a-range
       "uri" (.-uri doc)})

(defn make-fn-sym-info
  [fn-name ^js doc fn-range]
  #js {"containerName" ""
       "kind" 11 ; vscode.SymbolKind.Function
       "location" (make-location doc fn-range)
       "name" fn-name})

(defn find-id-nodes
  [nodes]
  (->> nodes
    (reduce
      (fn [acc ^js node]
        (let [node-type (.-type node)]
          #_(.log js/console "node type:" node-type)
          ;; XXX: consider query api?
          (if (= node-type "list")
            (let [child-count (.-childCount ^js node)]
              (if (< 3 child-count)
                (if-let [first-elt (.child node 1)]
                  (if (and (= (.-type first-elt) "symbol")
                        ;; XXX: other def things?
                        (#{"def" "definterface" "defmacro"
                           "defmethod" "defmulti" "defn" "defn-"
                           "defonce" "defprotocol" "defrecord" "deftype"
                           "ns"}
                          (.-text first-elt)))
                    (loop [i 2]
                      (if (<= i (dec child-count))
                        (let [elt (.child node i)]
                          (if (= (.-type elt) "symbol")
                            (conj acc elt)
                            (recur (inc i))))
                        acc))
                      acc)
                    acc)
                  acc))  ; XXX: meh, acc rep
            acc)))
      [])))

(defn doc-syms
  [^js doc ^js tok]  
  ;; XXX
  (.log js/console "doc-syms entered")
  (when (= "clojure" (.-languageId doc))
    (when-let [uri (.-uri doc)]
      (pc/let [^js tree (.parse @clj-parser (.getText doc))]
        (when tree
          (swap! trees assoc
            (.-path uri) tree)
          (let [id-nodes (find-id-nodes (node-seq (.-rootNode tree)))]
            (->> id-nodes
              (map (fn [id-node]
                    (make-fn-sym-info (.-text id-node)
                      doc
                      (make-range id-node))))
              clj->js)))))))

;; uses tree sitter query api
(defn doc-syms-2-old
  [^js doc ^js tok]
  ;; XXX
  (.log js/console "doc-syms-2: entered")
  (when (= "clojure" (.-languageId doc))
    (when-let [uri (.-uri doc)]
      (.log js/console "doc-syms-2: uri:" (.-path uri))
      (.log js/console "doc-syms-2: tree:" (get @trees (.-path uri)))
      (when-let [^js tree (get @trees (.-path uri))]
        (.log js/console "doc-syms-2: about to query tree")
        (let [q (.query @clj-lang
                  (str "((list "
                       "   (symbol) @head "
                       "   (symbol) @name) "
                       " (match? @head \"^def[a-zA-Z]*\"))"))
              ms (.matches q (.-rootNode tree))]
          (->> ms
            (keep (fn [match]
                    (when match
                      (when-let [caps (go/get match "captures")]
                        (when (= 2 (count caps))
                          (when-let [name-node (go/get (nth caps 1) "node")]
                            (make-fn-sym-info (.-text name-node)
                              doc
                              (make-range name-node))))))))
            clj->js))))))

(defn doc-syms-2
  [^js doc ^js tok]
  ;; XXX
  (.log js/console "doc-syms-2: entered")
  (when (= "clojure" (.-languageId doc))
    (when-let [uri (.-uri doc)]
      (pc/let [^js tree (.parse @clj-parser (.getText doc))]
        (when tree
          (swap! trees assoc
            (.-path uri) tree)
          (.log js/console "doc-syms-2: about to query tree")
          (let [q (.query @clj-lang
                    (str "((list "
                        "   (symbol) @head "
                        "   (symbol) @name) "
                        " (match? @head \"^def[a-zA-Z]*\"))"))
                ms (.matches q (.-rootNode tree))]
            (->> ms
              (keep (fn [match]
                      (when match
                        (when-let [caps (go/get match "captures")]
                          (when (= 2 (count caps))
                            (when-let [name-node (go/get (nth caps 1) "node")]
                              (make-fn-sym-info (.-text name-node)
                                doc
                                (make-range name-node))))))))
              clj->js)))))))

(defn find-def
  [^js doc ^js pos ^js tok]
  ;; XXX
  (.log js/console "find-def: entered")
  (when (= "clojure" (.-languageId doc))
    (when-let [uri (.-uri doc)]
      (when-let [^js tree (get @trees (.-path uri))]
        (when-let [word-range (.getWordRangeAtPosition doc pos 
                                #"[0-9a-zA-Z\-$!?\\/><*=_+]+")]
          (let [word (.getText doc word-range)
                nodes (node-seq (.-rootNode tree))
                target
                (->> nodes
                  (filter
                    (fn [^js node]
                      (when (= (.-text node) word)
                        (loop [sibling (.-previousNamedSibling node)]
                          (.log js/console "sibling:" sibling)
                          (when sibling
                            (if (and (= (.-type sibling) "symbol")
                                  ;; XXX: other def things?
                                  (#{"def" "definterface" "defmacro"
                                     "defmethod" "defmulti" "defn" "defn-"
                                     "defonce" "defprotocol"
                                     "defrecord" "deftype"
                                     "ns"}
                                    (.-text sibling)))
                              node
                              (recur (.-previousNamedSibling sibling))))))))
                  first)]
            (when target
              (make-location doc
                (make-range target)))))))))

;; uses tree sitter query api
(defn find-def-2
  [^js doc ^js pos ^js tok]
  ;; XXX
  (.log js/console "find-def-2: entered")
  (when (= "clojure" (.-languageId doc))
    (when-let [uri (.-uri doc)]
      (.log js/console "find-def-2: tree:" (get @trees (.-path uri)))
      (when-let [^js tree (get @trees (.-path uri))]
        (when-let [word-range (.getWordRangeAtPosition doc pos 
                                #"[0-9a-zA-Z\-$!?\\/><*=_+]+")]
          (let [word (.getText doc word-range)
                ;; XXX: same query as elsewhere
                q (.query @clj-lang
                    (str "((list "
                         "   (symbol) @head "
                         "   (symbol) @name) "
                         " (match? @head \"^def[a-zA-Z]*\"))"))
                ms (.matches q (.-rootNode tree))]
            (->> ms
              (keep (fn [match]
                      (when match
                        (when-let [caps (go/get match "captures")]
                          (when (= 2 (count caps))
                            (when-let [name-node (go/get (nth caps 1) "node")]
                              (when (= (.-text name-node) word)
                                (make-location doc
                                  (make-range name-node)))))))))
              first)))))))

(defn parse-doc
  [^js doc]
  ;; XXX
  (.log js/console "parse-doc: entered")
  (when (= "clojure" (.-languageId doc))
    (when-let [parser @clj-parser]
      (pc/let [^js tree (.parse parser (.getText doc))
               ^js uri (.-uri doc)]
        (.log js/console "parse-doc: parsed:" (.-path uri))
        (swap! trees assoc
          (.-path uri) tree)))))

(defn parse-editor
  [^js editor]
  ;; XXX
  (.log js/console "parse-editor: entered")
  (when (= "clojure" (.-languageId (.-document editor)))
    (let [^js doc (.-document editor)]
      (.log js/console "parse-editor: calling parse-doc")
      (parse-doc doc))))

(defn parse-all-open
  []
  ;; XXX
  (.log js/console "parse-all-open: entered")
  (doseq [^js editor (.-visibleTextEditors (.-window vscode))]
    ;; XXX
    (.log js/console (.-languageId (.-document editor)))
    (pc/let [_ (parse-editor editor)])))

(defn close-doc
  [^js doc]
  ;; XXX
  (.log js/console "close-doc: entered")
  (swap! trees
    dissoc (.-uri doc)))

(defn activate
  [^js ctx]
  (.log js/console "activate: entered for clojure defs")
  (pc/let [_ (.init Parser)
           wasm-path
           (.join path
             (.-extensionPath ctx) "parsers" "tree-sitter-clojure.wasm")
           lang (.load (.-Language Parser)
                  wasm-path)
           ^js parser (new Parser)
            _ (.setLanguage parser lang)]
    (reset! clj-lang lang)
    (reset! clj-parser parser)
    ;; hooking things up and preparing for cleanup
    (let [ss (.-subscriptions ctx)
          ls (.-languages vscode)
          wd (.-window vscode)
          ws (.-workspace vscode)]
      ;; for outline
      (.push ss
        (.registerDocumentSymbolProvider ls
          "clojure" #js {:provideDocumentSymbols
                         (fn [doc tok]
                           (if-let [syms (doc-syms #_ doc-syms-2 doc tok)]
                             syms
                             #js []))}))
      ;; tweak definition of word in clojure
      (.push ss
        (.setLanguageConfiguration ls
          "clojure" #js {:wordPattern #"[^\s,#()\[\]{};\"\\@\']+"}))
      ;; jump-to-definition
      (.push ss
        (.registerDefinitionProvider ls
          "clojure" #js {:provideDefinition #_ find-def find-def-2}))
      ;;(.push ss (.onDidChangeTextDocument ws handle-change))
      ;; XXX: coverted by onDidChangeActiveTextEditor?
      (.push ss
        (.onDidOpenTextDocument ws parse-doc))
      (.push ss
        (.onDidCloseTextDocument ws close-doc))
      (.push ss
        (.onDidChangeActiveTextEditor wd parse-editor))
      #_(.push ss
        (.onDidChangeVisibleTextEditors wd parse-all-open)))
    (pc/let [_ (parse-all-open)])))

(defn deactivate
  []
  (.log js/console "deactivating clojure defs"))
