package com.finanzas.conciliacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Movement fingerprint v1:
 * {@code v1|{cuentaId}|{fecha}|{monto}|{DEBITO|CREDITO}|{ref or -}|{desc or -}}
 */
public final class HuellaMovimiento {

  private HuellaMovimiento() {}

  public static String calcular(
      UUID cuentaIdentidadId,
      LocalDate fechaValor,
      BigDecimal monto,
      String direccion,
      String referencia,
      String descripcion) {
    String refNorm = referencia == null || referencia.isBlank() ? null : normalizar(referencia);
    String descNorm =
        descripcion == null || descripcion.isBlank() ? null : normalizar(descripcion);
    String refPart = refNorm != null ? refNorm : "-";
    String descPart = refNorm != null ? "-" : (descNorm != null ? descNorm : "-");
    String canonical =
        "v1|"
            + cuentaIdentidadId
            + "|"
            + fechaValor
            + "|"
            + monto.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            + "|"
            + direccion
            + "|"
            + refPart
            + "|"
            + descPart;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String normalizar(String texto) {
    String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
    String sinMarcas = nfd.replaceAll("\\p{M}+", "");
    return sinMarcas.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }
}
