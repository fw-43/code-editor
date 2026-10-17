package com.fw.codeeditor.editor;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.JTree;
import javax.swing.KeyStroke;
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

  public FileTree(Path root, Consumer<Path> onOpenFile) {
    super(new DefaultTreeModel(new DefaultMutableTreeNode(root)));
    this.model = (DefaultTreeModel) getModel();
    this.onOpenFile = onOpenFile;

    setRootVisible(true);
    setCellRenderer(new NameRenderer());

    DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) model.getRoot();
    loadChildren(rootNode);
    expandPath(new TreePath(rootNode.getPath()));

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

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() != 2) {
              return;
            }
            TreePath clicked = getPathForLocation(e.getX(), e.getY());
            if (clicked == null) {
              return;
            }
            Object value =
                ((DefaultMutableTreeNode) clicked.getLastPathComponent()).getUserObject();
            if (value instanceof Path p && Files.isRegularFile(p)) {
              FileTree.this.onOpenFile.accept(p);
            }
          }
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
    List<Path> entries;
    try (Stream<Path> stream = Files.list(dir)) {
      entries =
          stream
              .sorted(
                  Comparator.comparing((Path p) -> !Files.isDirectory(p))
                      .thenComparing(p -> p.getFileName().toString().toLowerCase()))
              .toList();
    } catch (IOException e) {
      entries = List.of();
    }
    for (Path entry : entries) {
      DefaultMutableTreeNode child = new DefaultMutableTreeNode(entry);
      if (Files.isDirectory(entry)) {
        child.add(new DefaultMutableTreeNode("...")); // placeholder so the folder is expandable
      }
      node.add(child);
    }
    model.nodeStructureChanged(node);
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
