package com.fw.codeeditor.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
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
  private Consumer<EditorTab> onDirtyChanged = tab -> {};

  public EditorTab(Path path) throws IOException {
    super(new RSyntaxTextArea());
    this.textArea = (RSyntaxTextArea) getViewport().getView();
    this.path = path;

    textArea.setSyntaxEditingStyle(syntaxFor(path));
    textArea.setCodeFoldingEnabled(true);
    textArea.setTabsEmulated(true);
    textArea.setTabSize(2);
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
    setDirty(false);
  }

  private void markDirty() {
    setDirty(true);
  }

  private void setDirty(boolean value) {
    if (dirty != value) {
      dirty = value;
      onDirtyChanged.accept(this);
    }
  }
}
