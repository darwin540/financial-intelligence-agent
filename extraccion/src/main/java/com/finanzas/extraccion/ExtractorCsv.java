package com.finanzas.extraccion;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts candidate fields from a synthetic CSV extract.
 * Expected header: fecha,descripcion,monto,direccion,referencia,moneda
 */
public final class ExtractorCsv {

  public record CampoExtraido(
      String clave,
      String valorTexto,
      BigDecimal valorDecimal,
      LocalDate valorFecha,
      int fila,
      Integer pagina,
      BigDecimal confianza,
      String referenciaCruda,
      String naturaleza) {}

  public List<CampoExtraido> extraer(InputStream in) {
    List<CampoExtraido> campos = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String header = br.readLine();
      if (header == null) {
        return campos;
      }
      int fila = 1; // header is row 1
      String line;
      while ((line = br.readLine()) != null) {
        fila++;
        if (line.isBlank()) {
          continue;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 5) {
          campos.add(
              new CampoExtraido(
                  "fila",
                  line,
                  null,
                  null,
                  fila,
                  null,
                  null,
                  line,
                  "HECHO_EXTRAIDO"));
          continue;
        }
        LocalDate fecha = LocalDate.parse(parts[0].trim());
        String desc = parts[1].trim();
        BigDecimal monto = new BigDecimal(parts[2].trim()).setScale(2, java.math.RoundingMode.HALF_UP);
        String dir = parts[3].trim().toUpperCase(Locale.ROOT);
        String ref = parts.length > 4 ? parts[4].trim() : "";
        String moneda = parts.length > 5 ? parts[5].trim() : "COP";

        campos.add(campo("fecha", fecha.toString(), null, fecha, fila, desc));
        campos.add(campo("descripcion", desc, null, null, fila, desc));
        campos.add(campo("monto", monto.toPlainString(), monto, null, fila, desc));
        campos.add(campo("direccion", dir, null, null, fila, desc));
        if (!ref.isEmpty()) {
          campos.add(campo("referencia", ref, null, null, fila, ref));
        }
        campos.add(campo("moneda", moneda, null, null, fila, moneda));
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("No se pudo extraer CSV: " + e.getMessage(), e);
    }
    return campos;
  }

  private static CampoExtraido campo(
      String clave,
      String texto,
      BigDecimal decimal,
      LocalDate fecha,
      int fila,
      String cruda) {
    return new CampoExtraido(
        clave,
        texto,
        decimal,
        fecha,
        fila,
        null,
        new BigDecimal("1.0000"),
        cruda,
        "HECHO_EXTRAIDO");
  }
}
