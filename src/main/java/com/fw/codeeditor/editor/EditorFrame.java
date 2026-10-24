package com.fw.codeeditor.editor;

import static com.fw.codeeditor.constants.AppConstants.APP_DIVIDER_LOCATION;
import static com.fw.codeeditor.constants.AppConstants.APP_HEIGHT;
import static com.fw.codeeditor.constants.AppConstants.APP_NAME;
import static com.fw.codeeditor.constants.AppConstants.APP_WIDTH;
import static com.fw.codeeditor.constants.AppConstants.DISK_CHECK_INTERVAL_MS;
import static com.fw.codeeditor.constants.AppConstants.FILE_FINDER_DEBOUNCE_MS;
import static com.fw.codeeditor.constants.AppConstants.GREP_DEBOUNCE_MS;
import static com.fw.codeeditor.constants.AppConstants.JMENU_BACK;
import static com.fw.codeeditor.constants.AppConstants.JMENU_CLOSE_TAB;
import static com.fw.codeeditor.constants.AppConstants.JMENU_FILE;
import static com.fw.codeeditor.constants.AppConstants.JMENU_FIND_IN_FILES;
import static com.fw.codeeditor.constants.AppConstants.JMENU_GO_TO_DEFINITION;
import static com.fw.codeeditor.constants.AppConstants.JMENU_GO_TO_FILE;
import static com.fw.codeeditor.constants.AppConstants.JMENU_GO_TO_IMPLEMENTATION;
import static com.fw.codeeditor.constants.AppConstants.JMENU_NAVIGATE;
import static com.fw.codeeditor.constants.AppConstants.JMENU_OPEN_FOLDER;
import static com.fw.codeeditor.constants.AppConstants.JMENU_SAVE;
import static com.fw.codeeditor.constants.AppConstants.JSCROLLPANE_MIN_HEIGHT;
import static com.fw.codeeditor.constants.AppConstants.JSCROLLPANE_MIN_WIDTH;
import static com.fw.codeeditor.constants.AppConstants.SEARCH_MAX_RESULTS;
import static com.fw.codeeditor.constants.AppConstants.SEARCH_MIN_QUERY_LENGTH;

import com.fw.codeeditor.index.Symbol;
import com.fw.codeeditor.index.SymbolIndex;
import com.fw.codeeditor.search.FuzzyMatcher;
import com.fw.codeeditor.search.Ripgrep;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import javax.swing.Timer;

public final class EditorFrame extends JFrame {

  private final JTabbedPane tabs = new JTabbedPane();
  private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
  private Path root;
  private FileTree tree;
  private final Timer diskCheckTimer =
      new Timer(DISK_CHECK_INTERVAL_MS, e -> checkOpenTabsForExternalChanges());
  private boolean checkingDisk;
  private volatile SymbolIndex index;
  private volatile Path indexRoot;
  private final AtomicBoolean indexQueued = new AtomicBoolean();
  private final ExecutorService indexer =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "symbol-indexer");
            thread.setDaemon(true);
            return thread;
          });
  private final Deque<Location> backStack = new ArrayDeque<>();

  private record Location(Path path, int line) {}

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
    diskCheckTimer.start();
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
    index = null;
    refreshIndex();
  }

  private JMenuBar buildMenuBar() {
    int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    JMenu file = new JMenu(JMENU_FILE);
    file.add(item(JMENU_OPEN_FOLDER, KeyEvent.VK_O, mask, this::chooseFolder));
    file.add(item(JMENU_SAVE, KeyEvent.VK_S, mask, this::saveCurrent));
    file.add(item(JMENU_CLOSE_TAB, KeyEvent.VK_W, mask, this::closeCurrent));
    JMenuBar bar = new JMenuBar();
    bar.add(file);
    JMenu navigate = new JMenu(JMENU_NAVIGATE);
    navigate.add(item(JMENU_GO_TO_DEFINITION, KeyEvent.VK_F12, 0, this::goToDefinition));
    navigate.add(item(JMENU_GO_TO_IMPLEMENTATION, KeyEvent.VK_F12, mask, this::goToImplementation));
    navigate.add(item(JMENU_BACK, KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, this::goBack));
    navigate.add(item(JMENU_GO_TO_FILE, KeyEvent.VK_P, mask, this::goToFile));
    navigate.add(
        item(
            JMENU_FIND_IN_FILES,
            KeyEvent.VK_F,
            mask | InputEvent.SHIFT_DOWN_MASK,
            this::findInFiles));
    bar.add(navigate);
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
    int tabIndex = tabs.indexOfComponent(tab);
    if (tabIndex >= 0) {
      tabs.setTitleAt(tabIndex, (tab.isDirty() ? "* " : "") + tab.path().getFileName());
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
      refreshIndex();
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

  private void checkOpenTabsForExternalChanges() {
    if (checkingDisk) {
      return; // a prompt is already open
    }
    checkingDisk = true;
    try {
      for (int i = 0; i < tabs.getTabCount(); i++) {
        EditorTab tab = (EditorTab) tabs.getComponentAt(i);
        if (!tab.changedOnDisk()) {
          continue;
        }
        if (tab.isDirty()) {
          if (!isActive()) {
            continue; // ask later, when the window is in front
          }
          if (!confirmReload(tab)) {
            tab.ignoreDiskChange();
            continue;
          }
        }
        try {
          tab.reload();
          refreshIndex();
        } catch (IOException e) {
          // file may be mid-write; the next tick tries again
        }
      }
    } finally {
      checkingDisk = false;
    }
  }

  private boolean confirmReload(EditorTab tab) {
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            tab.path().getFileName()
                + " changed on disk, but you have unsaved edits.\n"
                + "Reload from disk and discard your edits?",
            "File changed on disk",
            JOptionPane.YES_NO_OPTION);
    return choice == JOptionPane.YES_OPTION;
  }

  private void refreshIndex() {
    indexRoot = root;
    if (!indexQueued.compareAndSet(false, true)) {
      return; // a build is already waiting and will read the latest root
    }
    indexer.execute(
        () -> {
          indexQueued.set(false);
          try {
            index = SymbolIndex.build(indexRoot);
          } catch (IOException e) {
            System.err.println("Indexing failed: " + e.getMessage());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  private void goToDefinition() {
    jump(false);
  }

  private void goToImplementation() {
    jump(true);
  }

  private void jump(boolean implementations) {
    EditorTab tab = current();
    if (tab == null) {
      return;
    }
    SymbolIndex snapshot = index;
    if (snapshot == null) {
      showError("The symbol index is not ready. Is universal-ctags installed?");
      return;
    }
    String word = tab.wordAtCaret();
    if (word.isEmpty()) {
      return;
    }
    List<Symbol> found =
        (implementations ? snapshot.implementations(word) : snapshot.definitions(word))
            .stream()
                .sorted(
                    Comparator.comparing((Symbol s) -> !s.path().equals(tab.path()))
                        .thenComparing(s -> s.path().toString())
                        .thenComparingInt(Symbol::line))
                .toList();
    if (found.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          "No "
              + (implementations ? "implementations" : "definitions")
              + " found for '"
              + word
              + "'",
          APP_NAME,
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    Symbol target = found.size() == 1 ? found.get(0) : pick(found);
    if (target == null) {
      return;
    }
    backStack.push(new Location(tab.path(), tab.caretLine()));
    if (backStack.size() > 100) {
      backStack.removeLast();
    }
    openAt(target.path(), target.line());
  }

  private void goBack() {
    Location previous = backStack.poll();
    if (previous != null) {
      openAt(previous.path(), previous.line());
    }
  }

  private void openAt(Path path, int line) {
    openFile(path);
    EditorTab tab = current();
    if (tab != null && tab.path().equals(path)) {
      tab.goToLine(line);
    }
  }

  private Symbol pick(List<Symbol> candidates) {
    String[] labels = candidates.stream().map(this::label).toArray(String[]::new);
    Object choice =
        JOptionPane.showInputDialog(
            this,
            "Multiple matches:",
            APP_NAME,
            JOptionPane.QUESTION_MESSAGE,
            null,
            labels,
            labels[0]);
    if (choice == null) {
      return null;
    }
    return candidates.get(Arrays.asList(labels).indexOf(choice));
  }

  private String label(Symbol s) {
    Path shown = s.path().startsWith(root) ? root.relativize(s.path()) : s.path();
    String owner = s.simpleScope().isEmpty() ? "" : s.simpleScope() + " > ";
    return owner + s.name() + "  [" + s.kind() + "]  " + shown + ":" + s.line();
  }

  private boolean ensureRipgrep() {
    if (Ripgrep.isInstalled()) {
      return true;
    }
    showError("ripgrep (rg) was not found. Install it with: sudo apt install ripgrep");
    return false;
  }

  private void goToFile() {
    if (!ensureRipgrep()) {
      return;
    }
    Path base = root;
    AtomicReference<List<Path>> cache = new AtomicReference<>();
    FinderDialog<Path> dialog =
        new FinderDialog<>(
            this,
            JMENU_GO_TO_FILE,
            FILE_FINDER_DEBOUNCE_MS,
            path -> base.relativize(path).toString(),
            (query, cancelled) -> {
              List<Path> files = cache.get();
              if (files == null) {
                files = Ripgrep.listFiles(base); // listed once per popup, on the worker thread
                cache.set(files);
              }
              return FuzzyMatcher.rank(query, files, base, SEARCH_MAX_RESULTS);
            },
            this::openFile);
    dialog.open("");
  }

  private void findInFiles() {
    if (!ensureRipgrep()) {
      return;
    }
    Path base = root;
    EditorTab tab = current();
    String initial = tab == null ? "" : tab.wordAtCaret();
    FinderDialog<Ripgrep.Hit> dialog =
        new FinderDialog<>(
            this,
            JMENU_FIND_IN_FILES,
            GREP_DEBOUNCE_MS,
            hit -> base.relativize(hit.path()) + ":" + hit.line() + "  " + hit.text(),
            (query, cancelled) ->
                query.strip().length() < SEARCH_MIN_QUERY_LENGTH
                    ? List.of()
                    : Ripgrep.search(base, query.strip(), SEARCH_MAX_RESULTS, cancelled),
            hit -> navigateTo(hit.path(), hit.line()));
    dialog.open(initial);
  }

  /** Like openAt, but remembers where you were so Alt+Left returns there. */
  private void navigateTo(Path path, int line) {
    EditorTab tab = current();
    if (tab != null) {
      backStack.push(new Location(tab.path(), tab.caretLine()));
      if (backStack.size() > 100) {
        backStack.removeLast();
      }
    }
    openAt(path, line);
  }

  private void showError(String message) {
    JOptionPane.showMessageDialog(this, message, APP_NAME, JOptionPane.ERROR_MESSAGE);
  }
}
