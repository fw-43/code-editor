package com.fw.codeeditor.index;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** One symbol reported by ctags. {@code parents} are the names it extends or implements. */
public record Symbol(
    String name, Path path, int line, String kind, String scope, List<String> parents) {

  private static final Set<String> TYPES =
      Set.of(
          "class",
          "interface",
          "enum",
          "annotation",
          "record",
          "struct",
          "union",
          "typedef",
          "type");
  private static final Set<String> METHODS = Set.of("method", "function", "func", "prototype");

  public boolean isType() {
    return TYPES.contains(kind);
  }

  public boolean isMethod() {
    return METHODS.contains(kind);
  }

  /** The enclosing type's name without package or outer-class qualifiers. */
  public String simpleScope() {
    int dot = scope.lastIndexOf('.');
    return dot < 0 ? scope : scope.substring(dot + 1);
  }
}
