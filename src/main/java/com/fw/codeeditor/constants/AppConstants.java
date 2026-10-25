package com.fw.codeeditor.constants;

import java.awt.Color;

public final class AppConstants {

  public static final String APP_NAME = "code-editor";
  public static final Integer APP_WIDTH = 1200;
  public static final Integer APP_HEIGHT = 800;
  public static final Integer APP_DIVIDER_LOCATION = 260;
  public static final Integer JSCROLLPANE_MIN_WIDTH = 150;
  public static final Integer JSCROLLPANE_MIN_HEIGHT = 0;
  public static final int DISK_CHECK_INTERVAL_MS = 1000;
  public static final int EDITOR_FONT_SIZE = 13;

  public static final String JMENU_FILE = "File";
  public static final String JMENU_OPEN_FOLDER = "Open Folder...";
  public static final String JMENU_SAVE = "Save";
  public static final String JMENU_SAVE_ALL = "Save All";
  public static final String JMENU_CLOSE_TAB = "Close Tab";
  public static final String JMENU_NAVIGATE = "Navigate";
  public static final String JMENU_GO_TO_DEFINITION = "Go to Definition";
  public static final String JMENU_GO_TO_IMPLEMENTATION = "Go to Implementation";
  public static final String JMENU_BACK = "Back";
  public static final String JMENU_GO_TO_FILE = "Go to File";
  public static final String JMENU_FIND_IN_FILES = "Find in Files";

  public static final String CTAGS_COMMAND = "ctags";

  public static final String RG_COMMAND = "rg";
  public static final String SEARCH_MAX_FILESIZE = "1M";
  public static final int SEARCH_MAX_RESULTS = 200;
  public static final int SEARCH_MAX_LINE_CHARS = 200;
  public static final int SEARCH_MIN_QUERY_LENGTH = 2;
  public static final int FILE_FINDER_DEBOUNCE_MS = 30;
  public static final int GREP_DEBOUNCE_MS = 200;
  public static final int FINDER_WIDTH = 700;
  public static final int FINDER_HEIGHT = 420;

  public static final Color TAB_ACCENT = new Color(0x4F9DFF);
  public static final Color TAB_SELECTED_BACKGROUND = new Color(0x4B5052);
  public static final Color INDENT_GUIDE_COLOR = new Color(0x75787A);
  public static final int TAB_SELECTION_HEIGHT = 4;
}
