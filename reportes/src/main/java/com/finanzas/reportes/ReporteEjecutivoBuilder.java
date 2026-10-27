package com.finanzas.reportes;

import com.finanzas.finanzas.flujo.PeriodoDia;
import com.finanzas.finanzas.flujo.ResultadoFlujo;
import com.finanzas.optimizacion.ComparacionEstrategias;
import com.finanzas.optimizacion.ResultadoEstrategia;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds executive report from already computed runs. Does not recalculate interest.
 */
public final class ReporteEjecutivoBuilder {

  public record Recomendacion(String texto, List<Integer> reglas, UUID corridaId) {}

  public record Reporte(
      UUID reporteId,
      OffsetDateTime generadoEn,
      boolean esEstimacion,
      List<UUID> corridaIds,
      List<String> hechos,
      List<String> inferencias,
      List<Recomendacion> recomendaciones,
      List<String> advertencias,
      boolean deudaNuevaRecomendada) {}

  public Reporte desdeFlujo(UUID corridaId, ResultadoFlujo flujo) {
    List<String> hechos = new ArrayList<>();
    List<String> advertencias = new ArrayList<>();
    List<Recomendacion> recomendaciones = new ArrayList<>();

    for (PeriodoDia d : flujo.dias()) {
      if (d.salidas().signum() > 0 || d.entradas().signum() > 0) {
        hechos.add(
            "Fecha "
                + d.fecha()
                + ": saldo final "
                + d.saldoFinal().toPlainString()
                + " "
                + d.moneda()
                + ".");
      }
    }
    if (!flujo.viable()) {
      advertencias.add(
          "El calendario es inviable en al menos una fecha intermedia aunque el mes pueda cerrar positivo.");
      recomendaciones.add(
          new Recomendacion(
              "No hay abono recomendado en este horizonte.",
              flujo.reglasAplicadas(),
              corridaId));
    }
    advertencias.add(
        "Las cifras futuras son estimaciones del algoritmo " + ResultadoFlujoAlgoritmo.NOMBRE + ".");
    return new Reporte(
        UUID.randomUUID(),
        OffsetDateTime.now(ZoneId.of("America/Bogota")),
        true,
        List.of(corridaId),
        hechos,
        List.of(),
        recomendaciones,
        advertencias,
        false);
  }

  public Reporte desdeComparacion(UUID corridaId, ComparacionEstrategias cmp) {
    List<Recomendacion> recomendaciones = new ArrayList<>();
    ResultadoEstrategia rec =
        cmp.estrategias().stream().filter(ResultadoEstrategia::esRecomendada).findFirst().orElse(null);
    if (rec != null) {
      recomendaciones.add(
          new Recomendacion(cmp.justificacion(), List.of(6, 7, 8), corridaId));
    }
    return new Reporte(
        UUID.randomUUID(),
        OffsetDateTime.now(ZoneId.of("America/Bogota")),
        true,
        List.of(corridaId),
        List.of(),
        List.of(),
        recomendaciones,
        List.of("Las cifras son estimaciones de " + cmp.algoritmo() + "."),
        false);
  }

  /** Rejects recommendation text that invents amounts not present in the cited run. */
  public boolean textoCitaSoloMontosDeCorrida(String texto, List<String> montosPermitidos) {
    // Simple guard: any decimal-looking token must appear in allowed set
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\\d+\\.\\d{2}").matcher(texto);
    while (m.find()) {
      if (!montosPermitidos.contains(m.group())) {
        return false;
      }
    }
    return true;
  }

  private static final class ResultadoFlujoAlgoritmo {
    static final String NOMBRE = "flujo.diario.v1";
  }
}
