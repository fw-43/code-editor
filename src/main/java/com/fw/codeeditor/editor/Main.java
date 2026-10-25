package com.fw.codeeditor.editor;

import static com.fw.codeeditor.constants.AppConstants.INDENT_GUIDE_COLOR;
import static com.fw.codeeditor.constants.AppConstants.TAB_ACCENT;
import static com.fw.codeeditor.constants.AppConstants.TAB_SELECTED_BACKGROUND;
import static com.fw.codeeditor.constants.AppConstants.TAB_SELECTION_HEIGHT;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    UIManager.put("TabbedPane.underlineColor", TAB_ACCENT);
    UIManager.put("TabbedPane.inactiveUnderlineColor", TAB_ACCENT);
    UIManager.put("TabbedPane.tabSelectionHeight", TAB_SELECTION_HEIGHT);
    UIManager.put("TabbedPane.selectedBackground", TAB_SELECTED_BACKGROUND);
    UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
    UIManager.put("Tree.paintLines", true);
    UIManager.put("Tree.hash", INDENT_GUIDE_COLOR);
    Path start = args.length > 0 ? Path.of(args[0]) : Path.of(System.getProperty("user.dir"));
    SwingUtilities.invokeLater(() -> new EditorFrame(start).setVisible(true));
  }
}
