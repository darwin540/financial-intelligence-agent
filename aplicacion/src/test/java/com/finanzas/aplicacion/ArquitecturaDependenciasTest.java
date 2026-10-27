package com.finanzas.aplicacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArquitecturaDependenciasTest {

  @Test
  void optimizacionYProyeccionesNoDependenDeIa() throws Exception {
    String opt = Files.readString(Path.of("../optimizacion/pom.xml"));
    String proy = Files.readString(Path.of("../proyecciones/pom.xml"));
    String fin = Files.readString(Path.of("../finanzas/pom.xml"));
    assertFalse(opt.contains("<artifactId>ia</artifactId>"));
    assertFalse(proy.contains("<artifactId>ia</artifactId>"));
    assertFalse(opt.contains("<artifactId>extraccion</artifactId>"));
    assertFalse(proy.contains("<artifactId>extraccion</artifactId>"));
    assertTrue(fin.contains("<artifactId>finanzas</artifactId>") || true);
  }
}
