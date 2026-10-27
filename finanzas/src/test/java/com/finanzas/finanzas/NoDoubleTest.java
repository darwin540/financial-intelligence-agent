package com.finanzas.finanzas;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoDoubleTest {

  private static final Pattern FORBIDDEN =
      Pattern.compile("\\b(double|float)\\s+[a-zA-Z_]|\\b(double|float)\\s*\\[|\\((double|float)\\)");

  @Test
  void finanzasNoUsaDoubleParaMontos() throws IOException {
    Path root = Path.of("src/main/java");
    if (!Files.exists(root)) {
      root = Path.of("finanzas/src/main/java");
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(
              p -> {
                try {
                  String src = Files.readString(p);
                  String code =
                      src.replaceAll("/\\*.*?\\*/", " ").replaceAll("//.*", " ");
                  assertFalse(
                      FORBIDDEN.matcher(code).find(),
                      "tipo binario prohibido para montos/tasas en " + p);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }
}
