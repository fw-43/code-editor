package com.fw.codeeditor.editor;

import static com.fw.codeeditor.constants.AppConstants.FINDER_HEIGHT;
import static com.fw.codeeditor.constants.AppConstants.FINDER_WIDTH;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A type-to-search popup: a text field above a list of results. */
public final class FinderDialog<T> extends JDialog {

  /** Runs on a background thread. Should return early when {@code cancelled} turns true. */
  public interface Searcher<T> {
    List<T> search(String query, BooleanSupplier cancelled);
  }

  private final JTextField field = new JTextField();
  private final DefaultListModel<T> model = new DefaultListModel<>();
  private final JList<T> list = new JList<>(model);
  private final Searcher<T> searcher;
  private final Consumer<T> onChosen;
  private final Timer debounce;
  private final AtomicInteger generation = new AtomicInteger();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "finder-search");
            thread.setDaemon(true);
            return thread;
          });
  private boolean closing;

  public FinderDialog(
      Frame owner,
      String title,
      int debounceMs,
      Function<T, String> label,
      Searcher<T> searcher,
      Consumer<T> onChosen) {
    super(owner, title, false);
    this.searcher = searcher;
    this.onChosen = onChosen;
    this.debounce = new Timer(debounceMs, e -> runSearch());
    debounce.setRepeats(false);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setFocusable(false); // keep typing in the text field
    list.setCellRenderer(
        new DefaultListCellRenderer() {
          @Override
          @SuppressWarnings("unchecked")
          public Component getListCellRendererComponent(
              JList<?> l, Object value, int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(l, value, index, selected, focused);
            setText(label.apply((T) value));
            return this;
          }
        });
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              choose();
            }
          }
        });

    field
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                debounce.restart();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                debounce.restart();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {}
            });
    field.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
              case KeyEvent.VK_DOWN -> move(1);
              case KeyEvent.VK_UP -> move(-1);
              case KeyEvent.VK_ENTER -> choose();
              case KeyEvent.VK_ESCAPE -> dispose();
              default -> {}
            }
          }
        });

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowDeactivated(WindowEvent e) {
            dispose(); // clicking anywhere else closes the popup
          }
        });

    setLayout(new BorderLayout());
    add(field, BorderLayout.NORTH);
    add(new JScrollPane(list), BorderLayout.CENTER);
    setSize(FINDER_WIDTH, FINDER_HEIGHT);
  }

  public void open(String initialQuery) {
    field.setText(initialQuery);
    field.selectAll();
    debounce.stop();
    setLocationRelativeTo(getOwner());
    setVisible(true);
    runSearch();
  }

  private void runSearch() {
    String query = field.getText();
    int id = generation.incrementAndGet();
    worker.execute(
        () -> {
          List<T> results = searcher.search(query, () -> generation.get() != id);
          SwingUtilities.invokeLater(
              () -> {
                if (generation.get() != id) {
                  return; // a newer search replaced this one
                }
                model.clear();
                model.addAll(results);
                if (!results.isEmpty()) {
                  list.setSelectedIndex(0);
                }
              });
        });
  }

  private void move(int delta) {
    int size = model.getSize();
    if (size == 0) {
      return;
    }
    int next = Math.max(0, Math.min(size - 1, list.getSelectedIndex() + delta));
    list.setSelectedIndex(next);
    list.ensureIndexIsVisible(next);
  }

  private void choose() {
    T selected = list.getSelectedValue();
    if (selected != null) {
      dispose();
      onChosen.accept(selected);
    }
  }

  @Override
  public void dispose() {
    if (closing) {
      return;
    }
    closing = true;
    debounce.stop();
    generation.incrementAndGet();
    worker.shutdownNow();
    super.dispose();
  }
}
