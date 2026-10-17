package com.fw.codeeditor.editor;

import static com.fw.codeeditor.constants.AppConstants.APP_DIVIDER_LOCATION;
import static com.fw.codeeditor.constants.AppConstants.APP_HEIGHT;
import static com.fw.codeeditor.constants.AppConstants.APP_NAME;
import static com.fw.codeeditor.constants.AppConstants.APP_WIDTH;
import static com.fw.codeeditor.constants.AppConstants.JMENU_CLOSE_TAB;
import static com.fw.codeeditor.constants.AppConstants.JMENU_FILE;
import static com.fw.codeeditor.constants.AppConstants.JMENU_OPEN_FOLDER;
import static com.fw.codeeditor.constants.AppConstants.JMENU_SAVE;
import static com.fw.codeeditor.constants.AppConstants.JSCROLLPANE_MIN_HEIGHT;
import static com.fw.codeeditor.constants.AppConstants.JSCROLLPANE_MIN_WIDTH;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
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
  private FileTree tree;

  public EditorFrame(Path root) {
    super(APP_NAME);
    this.root = root;
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setSize(APP_WIDTH, APP_HEIGHT);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    split.setRightComponent(tabs);
    split.setDividerLocation(APP_DIVIDER_LOCATION);
    add(split, BorderLayout.CENTER);
    showTree();
    setJMenuBar(buildMenuBar());
    installTabSwitchKeys();
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

  private void installTabSwitchKeys() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(
            e -> {
              if (!isActive()
                  || e.getID() != KeyEvent.KEY_PRESSED
                  || !e.isControlDown()
                  || e.isShiftDown()
                  || e.isAltDown()) {
                return false;
              }
              if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                switchTab(-1);
                return true;
              }
              if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                switchTab(1);
                return true;
              }
              return false;
            });
  }

  private void switchTab(int delta) {
    int count = tabs.getTabCount();
    if (count < 2) {
      return;
    }
    int next = (tabs.getSelectedIndex() + delta + count) % count;
    tabs.setSelectedIndex(next);
    ((EditorTab) tabs.getComponentAt(next)).focusEditor();
  }

  private void showTree() {
    if (tree != null) {
      tree.close();
    }
    tree = new FileTree(root, this::openFile);
    JScrollPane scroll = new JScrollPane(tree);
    scroll.setMinimumSize(new Dimension(JSCROLLPANE_MIN_WIDTH, JSCROLLPANE_MIN_HEIGHT));
    split.setLeftComponent(scroll);
    setTitle(APP_NAME + " - " + root);
  }

  private JMenuBar buildMenuBar() {
    int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    JMenu file = new JMenu(JMENU_FILE);
    file.add(item(JMENU_OPEN_FOLDER, KeyEvent.VK_O, mask, this::chooseFolder));
    file.add(item(JMENU_SAVE, KeyEvent.VK_S, mask, this::saveCurrent));
    file.add(item(JMENU_CLOSE_TAB, KeyEvent.VK_W, mask, this::closeCurrent));
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
    JOptionPane.showMessageDialog(this, message, APP_NAME, JOptionPane.ERROR_MESSAGE);
  }
}
