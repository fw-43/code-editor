package com.fw.codeeditor.search;

import static com.fw.codeeditor.constants.AppConstants.RG_COMMAND;
import static com.fw.codeeditor.constants.AppConstants.SEARCH_MAX_FILESIZE;
import static com.fw.codeeditor.constants.AppConstants.SEARCH_MAX_LINE_CHARS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** File listing and text search, both delegated to the ripgrep command. */
public final class Ripgrep {

  public record Hit(Path path, int line, String text) {}

  /** Flags shared by every call: ignore user config, include dotfiles but not .git. */
  private static final List<String> COMMON =
      List.of(
          "--no-config",
          "--hidden",
          "--glob",
          "!.git",
          "--no-require-git",
          "--null",
          "--color=never");

  private Ripgrep() {}

  public static boolean isInstalled() {
    try {
      Process process =
          new ProcessBuilder(RG_COMMAND, "--version")
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      return process.waitFor() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Every file under root that ripgrep would search, as paths under root. */
  public static List<Path> listFiles(Path root) {
    List<String> command = new ArrayList<>();
    command.add(RG_COMMAND);
    command.addAll(COMMON);
    command.addAll(List.of("--files", "."));
    List<Path> files = new ArrayList<>();
    try {
      Process process = start(command, root, () -> false);
      byte[] output = process.getInputStream().readAllBytes();
      for (String name : new String(output, StandardCharsets.UTF_8).split("\0")) {
        if (!name.isEmpty()) {
          files.add(root.resolve(name).normalize());
        }
      }
    } catch (IOException e) {
      // return whatever was read
    }
    return files;
  }

  /** Lines containing the query (case-insensitive, literal), up to maxHits, ordered by path. */
  public static List<Hit> search(Path root, String query, int maxHits, BooleanSupplier cancelled) {
    List<String> command = new ArrayList<>();
    command.add(RG_COMMAND);
    command.addAll(COMMON);
    command.addAll(
        List.of(
            "--max-filesize",
            SEARCH_MAX_FILESIZE,
            "--line-number",
            "--no-heading",
            "--with-filename",
            "--ignore-case",
            "--fixed-strings",
            "--sort",
            "path",
            "-e",
            query,
            "--",
            "."));
    List<Hit> hits = new ArrayList<>();
    try {
      Process process = start(command, root, cancelled);
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while (hits.size() < maxHits
            && !cancelled.getAsBoolean()
            && (line = reader.readLine()) != null) {
          parse(line, root).ifPresent(hits::add);
        }
      }
      process.destroy(); // enough results, or cancelled: stop ripgrep
    } catch (IOException e) {
      // return whatever was read
    }
    return hits;
  }

  /** Starts ripgrep in root. A background watcher kills it as soon as cancelled turns true. */
  private static Process start(List<String> command, Path root, BooleanSupplier cancelled)
      throws IOException {
    Process process =
        new ProcessBuilder(command)
            .directory(root.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    Thread.startVirtualThread(
        () -> {
          while (process.isAlive()) {
            if (cancelled.getAsBoolean()) {
              process.destroy();
              return;
            }
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              return;
            }
          }
        });
    return process;
  }

  /** Parses {@code path<NUL>line:text}, the format produced by --null with --line-number. */
  private static Optional<Hit> parse(String line, Path root) {
    int nul = line.indexOf('\0');
    if (nul < 0) {
      return Optional.empty();
    }
    int colon = line.indexOf(':', nul + 1);
    if (colon < 0) {
      return Optional.empty();
    }
    try {
      int number = Integer.parseInt(line.substring(nul + 1, colon));
      String text = line.substring(colon + 1).strip();
      if (text.length() > SEARCH_MAX_LINE_CHARS) {
        text = text.substring(0, SEARCH_MAX_LINE_CHARS);
      }
      return Optional.of(new Hit(root.resolve(line.substring(0, nul)).normalize(), number, text));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
