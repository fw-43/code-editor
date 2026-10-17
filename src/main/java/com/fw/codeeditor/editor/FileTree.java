package com.fw.codeeditor.editor;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Directory tree that loads each folder's children only when it is expanded. */
public final class FileTree extends JTree {

  private final DefaultTreeModel model;
  private final Consumer<Path> onOpenFile;
  private final WatchService watcher;
  private final Set<Path> dirtyDirs = new HashSet<>();
  private final Timer refreshTimer = new Timer(200, e -> flushDirty());

  public FileTree(Path root, Consumer<Path> onOpenFile) {
    super(new DefaultTreeModel(new DefaultMutableTreeNode(root)));
    this.model = (DefaultTreeModel) getModel();
    this.onOpenFile = onOpenFile;
    this.watcher = openWatcher();
    refreshTimer.setRepeats(false);

    setRootVisible(true);
    setCellRenderer(new NameRenderer());

    DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) model.getRoot();
    loadChildren(rootNode);
    expandPath(new TreePath(rootNode.getPath()));
    startWatching();

    addTreeWillExpandListener(
        new TreeWillExpandListener() {
          @Override
          public void treeWillExpand(TreeExpansionEvent event) {
            DefaultMutableTreeNode node =
                (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
            if (needsLoading(node)) {
              loadChildren(node);
            }
          }

          @Override
          public void treeWillCollapse(TreeExpansionEvent event) {}
        });

    bindEnterKey();
  }

  private void bindEnterKey() {
    String key = "openOrToggle";
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), key);
    getActionMap()
        .put(
            key,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                TreePath selected = getSelectionPath();
                if (selected == null) {
                  return;
                }
                Object value =
                    ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
                if (value instanceof Path p && Files.isRegularFile(p)) {
                  onOpenFile.accept(p);
                } else if (isExpanded(selected)) {
                  collapsePath(selected);
                } else {
                  expandPath(selected);
                }
              }
            });
  }

  private static boolean needsLoading(DefaultMutableTreeNode node) {
    return node.getChildCount() == 1
        && !(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject() instanceof Path);
  }

  private void loadChildren(DefaultMutableTreeNode node) {
    node.removeAllChildren();
    Path dir = (Path) node.getUserObject();
    watch(dir); // register before listing so no change slips between the two
    for (Path entry : listEntries(dir)) {
      node.add(createNode(entry));
    }
    model.nodeStructureChanged(node);
  }

  private static DefaultMutableTreeNode createNode(Path entry) {
    DefaultMutableTreeNode child = new DefaultMutableTreeNode(entry);
    if (Files.isDirectory(entry)) {
      child.add(new DefaultMutableTreeNode("...")); // placeholder so the folder is expandable
    }
    return child;
  }

  private static List<Path> listEntries(Path dir) {
    try (Stream<Path> stream = Files.list(dir)) {
      List<Path> all = new ArrayList<>(stream.toList());
      Set<Path> dirs = new HashSet<>();
      for (Path p : all) {
        if (Files.isDirectory(p)) {
          dirs.add(p);
        }
      }
      all.sort(
          Comparator.comparing((Path p) -> !dirs.contains(p))
              .thenComparing(p -> p.getFileName().toString().toLowerCase())
              .thenComparing(p -> p.getFileName().toString()));
      return all;
    } catch (IOException e) {
      return List.of();
    }
  }

  private static WatchService openWatcher() {
    try {
      return FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      return null; // tree still works, it just won't auto-refresh
    }
  }

  private void watch(Path dir) {
    if (watcher == null) {
      return;
    }
    try {
      dir.register(
          watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
    } catch (IOException e) {
      // ignore: this folder just won't auto-refresh
    }
  }

  private void startWatching() {
    if (watcher == null) {
      return;
    }
    Thread thread = new Thread(this::pollEvents, "file-tree-watcher");
    thread.setDaemon(true);
    thread.start();
  }

  private void pollEvents() {
    try {
      while (true) {
        WatchKey key = watcher.take();
        Path dir = (Path) key.watchable();
        key.pollEvents();
        key.reset();
        SwingUtilities.invokeLater(() -> markDirty(dir));
      }
    } catch (InterruptedException | ClosedWatchServiceException e) {
      // watcher closed, stop quietly
    }
  }

  private void markDirty(Path dir) {
    dirtyDirs.add(dir);
    refreshTimer.restart();
  }

  private void flushDirty() {
    List<Path> dirs = List.copyOf(dirtyDirs);
    dirtyDirs.clear();
    for (Path dir : dirs) {
      DefaultMutableTreeNode node = findNode(dir);
      if (node != null && !needsLoading(node)) {
        syncChildren(node);
      }
    }
  }

  private DefaultMutableTreeNode findNode(Path target) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) model.getRoot();
    Path current = (Path) node.getUserObject();
    if (target.equals(current)) {
      return node;
    }
    if (!target.startsWith(current)) {
      return null;
    }
    for (Path part : current.relativize(target)) {
      current = current.resolve(part);
      DefaultMutableTreeNode found = null;
      for (int i = 0; i < node.getChildCount(); i++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
        if (current.equals(child.getUserObject())) {
          found = child;
          break;
        }
      }
      if (found == null) {
        return null;
      }
      node = found;
    }
    return node;
  }

  /** Applies only the differences, so expanded folders and the selection stay as they are. */
  private void syncChildren(DefaultMutableTreeNode node) {
    List<Path> entries = listEntries((Path) node.getUserObject());
    Set<Path> present = new HashSet<>(entries);
    for (int i = node.getChildCount() - 1; i >= 0; i--) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
      if (!present.contains((Path) child.getUserObject())) {
        model.removeNodeFromParent(child);
      }
    }
    for (int i = 0; i < entries.size(); i++) {
      boolean inPlace =
          i < node.getChildCount()
              && entries
                  .get(i)
                  .equals(((DefaultMutableTreeNode) node.getChildAt(i)).getUserObject());
      if (!inPlace) {
        model.insertNodeInto(createNode(entries.get(i)), node, i);
      }
    }
  }

  public void close() {
    refreshTimer.stop();
    if (watcher != null) {
      try {
        watcher.close();
      } catch (IOException e) {
        // nothing useful to do
      }
    }
  }

  private static final class NameRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean sel,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      Object user = ((DefaultMutableTreeNode) value).getUserObject();
      if (user instanceof Path p && p.getFileName() != null) {
        setText(p.getFileName().toString());
      }
      return this;
    }
  }
}
