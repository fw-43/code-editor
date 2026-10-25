# Code-Editor

## Features
- <Ctrl+PageUp> to switch to left tab, <Ctrl+PageDown> to switch to right tab
- Have the FileTree reacts if there is an external update to the files and directories in the project
- Fuzzy-find filenames and grep contents

## Swing Components
```
EditorFrame (JFrame)
├── JMenuBar
│   └── JMenu "File"
│       ├── JMenuItem "Open Folder..."   (Ctrl+O)
│       ├── JMenuItem "Save"             (Ctrl+S)
│       └── JMenuItem "Close Tab"        (Ctrl+W)
│
└── content pane (BorderLayout.CENTER)
    └── JSplitPane (horizontal)
        ├── left:  JScrollPane
        │   └── FileTree (extends JTree)
        │       ├── DefaultTreeModel
        │       │   └── DefaultMutableTreeNode (one per file/folder, holds a Path)
        │       └── NameRenderer (extends DefaultTreeCellRenderer)
        │
        └── right: JTabbedPane
            └── EditorTab (extends RTextScrollPane, one per open file)
                ├── viewport
                │   └── RSyntaxTextArea (extends JTextArea)
                └── row header
                    └── Gutter (line numbers and fold indicators)
```

## Run on Local
```bash
./gradlew spotlessApply clean build installDist
# requires custom bash script
code N-project-name
```
