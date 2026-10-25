# Code-Editor

## Features (and Changes)
- Update tab size
- Tab navigation
- Auto-reload EditorTab to reflect external changes (e.g. spotless)
- Have the FileTree reacts if there is an external update to the files and directories in the project
- Fuzzy-find filenames and grep contents
- Go-to-definition & go-to-implementation
- Save-all
- Highlight focused tab
- Update font size
- Indentation marker in EditorTab
- Indentation marker in FileTree

## Dependencies
- Java Swing components
- RSyntaxTextArea 3.5.3
- Flatlaf 3.5.4
- Jackson Databind 2.18.2
- Universal Ctags 5.9.0
- Ripgrep 13.0.0

## Swing Components
```
EditorFrame (JFrame)
├── JMenuBar
│   ├── JMenu "File"
│   │   ├── JMenuItem "Open Folder..."       (Ctrl+O)
│   │   ├── JMenuItem "Save"                 (Ctrl+S)
│   │   ├── JMenuItem "Save All"             (Ctrl+Shift+S)
│   │   └── JMenuItem "Close Tab"            (Ctrl+W)
│   └── JMenu "Navigate"
│       ├── JMenuItem "Go to Definition"     (F12)
│       ├── JMenuItem "Go to Implementation" (Ctrl+F12)
│       ├── JMenuItem "Back"                 (Alt+Left)
│       ├── JMenuItem "Go to File"           (Ctrl+P)
│       ├── JMenuItem "Find in Files"        (Ctrl+Shift+F)
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

FinderDialog (JDialog, owned by EditorFrame, created on demand)
├── JTextField (query)
└── JScrollPane
    └── JList (results)
```

## Run on Local
```bash
./gradlew spotlessApply clean build installDist
## requires custom bash script
code N-project-name
```
