package com.fw.codeeditor.index;

import static com.fw.codeeditor.constants.AppConstants.CTAGS_COMMAND;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Symbol table built from universal-ctags output. Never modified after it is built. */
public final class SymbolIndex {

  private final Map<String, List<Symbol>> byName = new HashMap<>();
  private final Map<String, Set<String>> parentsOf = new HashMap<>();
  private final Map<String, Set<String>> childrenOf = new HashMap<>();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SymbolIndex(List<Symbol> symbols) {
    for (Symbol s : symbols) {
      byName.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(s);
      if (s.isType()) {
        for (String parent : s.parents()) {
          parentsOf.computeIfAbsent(s.name(), k -> new HashSet<>()).add(parent);
          childrenOf.computeIfAbsent(parent, k -> new HashSet<>()).add(s.name());
        }
      }
    }
  }

  public static SymbolIndex build(Path root) throws IOException, InterruptedException {
    List<String> command =
        List.of(
            CTAGS_COMMAND,
            "--output-format=json",
            "--fields=+ni",
            "--languages=Java,C,C++,Go",
            "--exclude=.git",
            "--exclude=build",
            "--exclude=.gradle",
            "-R",
            "-f",
            "-",
            ".");
    Process process =
        new ProcessBuilder(command)
            .directory(root.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    List<Symbol> symbols = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        parse(line, root).ifPresent(symbols::add);
      }
    }
    int exit = process.waitFor();
    if (exit != 0 && symbols.isEmpty()) {
      throw new IOException("ctags exited with code " + exit);
    }
    return new SymbolIndex(symbols);
  }

  public List<Symbol> definitions(String name) {
    return byName.getOrDefault(name, List.of());
  }

  /**
   * For a type name: all direct and indirect subtypes. For a method name: same-named methods in
   * types that inherit from a type declaring that method.
   */
  public List<Symbol> implementations(String name) {
    Set<Symbol> result = new LinkedHashSet<>();
    List<Symbol> declared = definitions(name);
    if (declared.stream().anyMatch(Symbol::isType)) {
      for (String subtype : reachable(childrenOf, name)) {
        for (Symbol s : definitions(subtype)) {
          if (s.isType()) {
            result.add(s);
          }
        }
      }
    }
    for (Symbol method : declared) {
      if (!method.isMethod()) {
        continue;
      }
      for (String ancestor : reachable(parentsOf, method.simpleScope())) {
        if (declaresMethod(ancestor, name)) {
          result.add(method);
          break;
        }
      }
    }
    return List.copyOf(result);
  }

  private boolean declaresMethod(String type, String method) {
    return definitions(method).stream().anyMatch(s -> s.isMethod() && s.simpleScope().equals(type));
  }

  private static Set<String> reachable(Map<String, Set<String>> graph, String start) {
    Set<String> seen = new LinkedHashSet<>();
    Deque<String> queue = new ArrayDeque<>(graph.getOrDefault(start, Set.of()));
    while (!queue.isEmpty()) {
      String next = queue.poll();
      if (seen.add(next)) {
        queue.addAll(graph.getOrDefault(next, Set.of()));
      }
    }
    return seen;
  }

  private static Optional<Symbol> parse(String line, Path root) {
    try {
      JsonNode tag = MAPPER.readTree(line);
      if (!"tag".equals(text(tag, "_type")) || !tag.has("line")) {
        return Optional.empty();
      }
      String name = text(tag, "name");
      String path = text(tag, "path");
      if (name.isEmpty() || path.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new Symbol(
              name,
              root.resolve(path).normalize(),
              tag.get("line").asInt(),
              text(tag, "kind"),
              text(tag, "scope"),
              parents(text(tag, "inherits"))));
    } catch (IOException | RuntimeException e) {
      return Optional.empty(); // not a tag line, or not shaped as expected: skip it
    }
  }

  private static String text(JsonNode node, String key) {
    return node.path(key).asText("");
  }

  private static List<String> parents(String inherits) {
    List<String> result = new ArrayList<>();
    for (String raw : inherits.split(",")) {
      String name = raw.replaceAll("<.*>", "").trim();
      int dot = name.lastIndexOf('.');
      if (dot >= 0) {
        name = name.substring(dot + 1);
      }
      if (!name.isEmpty()) {
        result.add(name);
      }
    }
    return result;
  }
}
