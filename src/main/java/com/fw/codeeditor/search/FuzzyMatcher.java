package com.fw.codeeditor.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FuzzyMatcher {

  private FuzzyMatcher() {}

  private record Scored(Path path, String name, int score) {}

  /** Paths that match the query, best first. An empty query matches everything, by name. */
  public static List<Path> rank(String query, List<Path> files, Path base, int limit) {
    String q = query.strip().replace(" ", "");
    List<Scored> scored = new ArrayList<>();
    for (Path file : files) {
      String name = base.relativize(file).toString().replace('\\', '/');
      int score = score(q, name);
      if (score > 0) {
        scored.add(new Scored(file, name, score));
      }
    }
    scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(Scored::name));
    return scored.stream().limit(limit).map(Scored::path).toList();
  }

  /** Higher is better; 0 means no match. */
  static int score(String query, String path) {
    if (query.isEmpty()) {
      return 1;
    }
    int nameStart = path.lastIndexOf('/') + 1;
    int inName = scoreFrom(query, path, nameStart);
    if (inName > 0) {
      return inName + 1000; // the whole query matched inside the file name
    }
    return scoreFrom(query, path, 0);
  }

  private static int scoreFrom(String query, String path, int from) {
    int score = 0;
    int matched = 0;
    int previous = -2;
    for (int i = from; i < path.length() && matched < query.length(); i++) {
      if (Character.toLowerCase(path.charAt(i)) != Character.toLowerCase(query.charAt(matched))) {
        continue;
      }
      score += 10;
      if (i == previous + 1) {
        score += 15;
      }
      if (isWordStart(path, i)) {
        score += 20;
      }
      previous = i;
      matched++;
    }
    if (matched < query.length()) {
      return 0;
    }
    return Math.max(1, score - (path.length() - from) / 4);
  }

  private static boolean isWordStart(String s, int i) {
    if (i == 0) {
      return true;
    }
    char prev = s.charAt(i - 1);
    return prev == '/'
        || prev == '_'
        || prev == '-'
        || prev == '.'
        || (Character.isUpperCase(s.charAt(i)) && Character.isLowerCase(prev));
  }
}
