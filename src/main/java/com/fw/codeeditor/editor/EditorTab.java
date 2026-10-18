package com.fw.codeeditor.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

/** One open file: a text area in a scroll pane, plus its path and dirty flag. */
public final class EditorTab extends RTextScrollPane {

  private static final Map<String, String> SYNTAX_BY_EXTENSION =
      Map.ofEntries(
          Map.entry("java", SyntaxConstants.SYNTAX_STYLE_JAVA),
          Map.entry("c", SyntaxConstants.SYNTAX_STYLE_C),
          Map.entry("h", SyntaxConstants.SYNTAX_STYLE_C),
          Map.entry("go", SyntaxConstants.SYNTAX_STYLE_GO),
          Map.entry("gradle", SyntaxConstants.SYNTAX_STYLE_GROOVY),
          Map.entry("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY),
          Map.entry("xml", SyntaxConstants.SYNTAX_STYLE_XML),
          Map.entry("yml", SyntaxConstants.SYNTAX_STYLE_YAML),
          Map.entry("yaml", SyntaxConstants.SYNTAX_STYLE_YAML),
          Map.entry("sql", SyntaxConstants.SYNTAX_STYLE_SQL),
          Map.entry("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
          Map.entry("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE),
          Map.entry("sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL));

  private final RSyntaxTextArea textArea;
  private final Path path;
  private boolean dirty;
  private FileTime knownModified;
  private long knownSize;
  private boolean loading;
  private Consumer<EditorTab> onDirtyChanged = tab -> {};

  public EditorTab(Path path) throws IOException {
    super(new RSyntaxTextArea());
    this.textArea = (RSyntaxTextArea) getViewport().getView();
    this.path = path;

    textArea.setSyntaxEditingStyle(syntaxFor(path));
    textArea.setCodeFoldingEnabled(true);
    textArea.setTabsEmulated(true);
    textArea.setTabSize(2);
    rememberDiskState();
    textArea.setText(Files.readString(path, StandardCharsets.UTF_8));
    textArea.setCaretPosition(0);
    textArea.discardAllEdits();
    setLineNumbersEnabled(true);
    setFoldIndicatorEnabled(true);

    textArea
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                markDirty();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                markDirty();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {}
            });
  }

  static String syntaxFor(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    return SYNTAX_BY_EXTENSION.getOrDefault(ext, SyntaxConstants.SYNTAX_STYLE_NONE);
  }

  public Path path() {
    return path;
  }

  public boolean isDirty() {
    return dirty;
  }

  public void onDirtyChanged(Consumer<EditorTab> listener) {
    this.onDirtyChanged = listener;
  }

  public void focusEditor() {
    textArea.requestFocusInWindow();
  }

  public void save() throws IOException {
    Files.writeString(path, textArea.getText(), StandardCharsets.UTF_8);
    rememberDiskState();
    setDirty(false);
  }

  /** True if the file on disk is different from what this tab last read or wrote. */
  public boolean changedOnDisk() {
    try {
      return !Files.getLastModifiedTime(path).equals(knownModified)
          || Files.size(path) != knownSize;
    } catch (IOException e) {
      return false; // deleted or unreadable: keep what is in the editor
    }
  }

  /** Stops asking about the current disk version, e.g. after the user chose to keep their edits. */
  public void ignoreDiskChange() {
    rememberDiskState();
  }

  public void reload() throws IOException {
    FileTime modified = Files.getLastModifiedTime(path);
    long size = Files.size(path);
    String text = Files.readString(path, StandardCharsets.UTF_8);
    knownModified = modified;
    knownSize = size;
    if (text.equals(textArea.getText())) {
      setDirty(false);
      return;
    }
    int caret = textArea.getCaretPosition();
    int scroll = getVerticalScrollBar().getValue();
    loading = true;
    try {
      textArea.setText(text);
    } finally {
      loading = false;
    }
    textArea.setCaretPosition(Math.min(caret, textArea.getDocument().getLength()));
    textArea.discardAllEdits();
    setDirty(false);
    SwingUtilities.invokeLater(() -> getVerticalScrollBar().setValue(scroll));
  }

  private void rememberDiskState() {
    try {
      knownModified = Files.getLastModifiedTime(path);
      knownSize = Files.size(path);
    } catch (IOException e) {
      // leave the previous values; the next check will compare against them
    }
  }

  private void markDirty() {
    if (loading) {
      return;
    }
    setDirty(true);
  }

  private void setDirty(boolean value) {
    if (dirty != value) {
      dirty = value;
      onDirtyChanged.accept(this);
    }
  }
}
