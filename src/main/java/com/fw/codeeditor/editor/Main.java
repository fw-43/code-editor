package com.fw.codeeditor.editor;

import com.formdev.flatlaf.FlatDarkLaf;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    Path start = args.length > 0 ? Path.of(args[0]) : Path.of(System.getProperty("user.dir"));
    SwingUtilities.invokeLater(() -> new EditorFrame(start).setVisible(true));
  }
}
