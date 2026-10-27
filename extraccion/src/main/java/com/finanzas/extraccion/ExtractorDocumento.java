package com.finanzas.extraccion;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Routes extraction by MIME. Photos and image PDFs are archived without OCR (no candidates).
 */
public final class ExtractorDocumento {

  public record ResultadoExtraccion(
      boolean extraccionAutomatica, List<ExtractorCsv.CampoExtraido> campos, String estado) {}

  public ResultadoExtraccion extraer(String mime, String nombreArchivo, InputStream in) {
    String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
    String name = nombreArchivo == null ? "" : nombreArchivo.toLowerCase(Locale.ROOT);

    if (m.contains("csv") || name.endsWith(".csv") || m.equals("text/csv") || m.equals("text/plain")) {
      return new ResultadoExtraccion(true, new ExtractorCsv().extraer(in), "PENDIENTE_REVISION");
    }
    if (m.contains("spreadsheet")
        || m.contains("excel")
        || name.endsWith(".xlsx")
        || name.endsWith(".xls")) {
      // MVP: Excel path delegates to CSV-like rows via POI in a later pass; for now mark pending
      return new ResultadoExtraccion(true, List.of(), "PENDIENTE_REVISION");
    }
    if (m.equals("application/pdf") || name.endsWith(".pdf")) {
      // Text PDF would use PDFBox; image PDF → no OCR
      return new ResultadoExtraccion(false, List.of(), "PENDIENTE_REVISION");
    }
    if (m.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
      return new ResultadoExtraccion(false, List.of(), "PENDIENTE_REVISION");
    }
    return new ResultadoExtraccion(false, List.of(), "PENDIENTE_REVISION");
  }
}
