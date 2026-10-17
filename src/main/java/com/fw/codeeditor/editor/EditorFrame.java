package com.fw.codeeditor.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;

public final class EditorFrame extends JFrame {

  private final JTabbedPane tabs = new JTabbedPane();
  private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
  private Path root;

  public EditorFrame(Path root) {
    super("code-editor");
    this.root = root;
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setSize(1200, 800);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    split.setRightComponent(tabs);
    split.setDividerLocation(260);
    add(split, BorderLayout.CENTER);
    showTree();
    setJMenuBar(buildMenuBar());
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            if (confirmCloseAll()) {
              dispose();
              System.exit(0);
            }
          }
        });
  }

  private void showTree() {
    FileTree tree = new FileTree(root, this::openFile);
    JScrollPane scroll = new JScrollPane(tree);
    scroll.setMinimumSize(new Dimension(150, 100));
    split.setLeftComponent(scroll);
    setTitle("code-editor - " + root);
  }

  private JMenuBar buildMenuBar() {
    int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    JMenu file = new JMenu("File");
    file.add(item("Open Folder...", KeyEvent.VK_O, mask, this::chooseFolder));
    file.add(item("Save", KeyEvent.VK_S, mask, this::saveCurrent));
    file.add(item("Close Tab", KeyEvent.VK_W, mask, this::closeCurrent));
    JMenuBar bar = new JMenuBar();
    bar.add(file);
    return bar;
  }

  private static JMenuItem item(String label, int key, int mask, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    item.setAccelerator(KeyStroke.getKeyStroke(key, mask));
    item.addActionListener(e -> action.run());
    return item;
  }

  private void chooseFolder() {
    JFileChooser chooser = new JFileChooser(root.toFile());
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      root = chooser.getSelectedFile().toPath();
      showTree();
    }
  }

  private void openFile(Path path) {
    for (int i = 0; i < tabs.getTabCount(); i++) {
      EditorTab existing = (EditorTab) tabs.getComponentAt(i);
      if (existing.path().equals(path)) {
        tabs.setSelectedIndex(i);
        existing.focusEditor();
        return;
      }
    }
    try {
      EditorTab tab = new EditorTab(path);
      tab.onDirtyChanged(this::refreshTitle);
      tabs.addTab(path.getFileName().toString(), tab);
      tabs.setSelectedComponent(tab);
      tab.focusEditor();
    } catch (IOException e) {
      showError("Cannot open " + path + ": " + e.getMessage());
    }
  }

  private void refreshTitle(EditorTab tab) {
    int index = tabs.indexOfComponent(tab);
    if (index >= 0) {
      tabs.setTitleAt(index, (tab.isDirty() ? "* " : "") + tab.path().getFileName());
    }
  }

  private EditorTab current() {
    return (EditorTab) tabs.getSelectedComponent();
  }

  private void saveCurrent() {
    EditorTab tab = current();
    if (tab != null) {
      save(tab);
    }
  }

  private boolean save(EditorTab tab) {
    try {
      tab.save();
      return true;
    } catch (IOException e) {
      showError("Cannot save " + tab.path() + ": " + e.getMessage());
      return false;
    }
  }

  private void closeCurrent() {
    EditorTab tab = current();
    if (tab != null && confirmClose(tab)) {
      tabs.remove(tab);
    }
  }

  private boolean confirmCloseAll() {
    for (int i = 0; i < tabs.getTabCount(); i++) {
      EditorTab tab = (EditorTab) tabs.getComponentAt(i);
      tabs.setSelectedIndex(i);
      if (!confirmClose(tab)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if the tab may be closed (saved, discarded, or already clean). */
  private boolean confirmClose(EditorTab tab) {
    if (!tab.isDirty()) {
      return true;
    }
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            "Save changes to " + tab.path().getFileName() + "?",
            "Unsaved changes",
            JOptionPane.YES_NO_CANCEL_OPTION);
    return switch (choice) {
      case JOptionPane.YES_OPTION -> save(tab);
      case JOptionPane.NO_OPTION -> true;
      default -> false;
    };
  }

  private void showError(String message) {
    JOptionPane.showMessageDialog(this, message, "code-editor", JOptionPane.ERROR_MESSAGE);
  }
}
