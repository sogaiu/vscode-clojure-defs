# vscode-clojure-defs

## What

Outline view and jump-to-definition (within a file) for Clojure code.

## How

To install from a .vsix:

```
# clone repository
cd ~/src
git clone https://github.com/sogaiu/vscode-clojure-defs
cd vscode-clojure-defs

# command line install of extension
visual-studio-code --install-extension clojure-defs-*.vsix
```

Alternatively, via the VSCode GUI:

* `File > Preferences > Extensions`
* Click on the `...` at the top right of the EXTENSIONS area
* Choose `Install from VSIX...` and find the .vsix file

To run the extension from source:

```
# clone repository
cd ~/src
git clone https://github.com/sogaiu/vscode-clojure-defs
cd vscode-clojure-defs

# create `node_modules` and populate with dependencies
npm install

# open the root folder in VSCode
visual-studio-code . # or whatever vscode is called on your system
```

Now run the extension in an Extension Development Host by pressing `F5`, or choosing `Debug` > `Start`

View some Clojure code and enjoy the outline view.

Jump-to-definition with F12 might work too.

## Acknowledgments

See [tree-sitter-clojure](https://github.com/sogaiu/tree-sitter-clojure)'s [section of the same name](https://github.com/sogaiu/tree-sitter-clojure#acknowledgments).
