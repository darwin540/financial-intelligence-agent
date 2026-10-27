package com.finanzas.conciliacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DeduplicadorMovimientos {

  public enum ResultadoTipo {
    NUEVO,
    DUPLICADO_EXACTO,
    POSIBLE_DUPLICADO
  }

  public record Candidato(
      UUID cuentaId,
      LocalDate fecha,
      BigDecimal monto,
      String direccion,
      String referencia,
      String descripcion,
      int pagina,
      int fila) {}

  public record Decision(ResultadoTipo tipo, String huella, UUID canonicoId) {}

  private final Map<String, UUID> confirmadosPorHuella = new HashMap<>();

  public void registrarConfirmado(String huella, UUID movimientoId) {
    confirmadosPorHuella.put(huella, movimientoId);
  }

  public Decision evaluar(Candidato c) {
    String huella =
        HuellaMovimiento.calcular(
            c.cuentaId(), c.fecha(), c.monto(), c.direccion(), c.referencia(), c.descripcion());
    UUID existente = confirmadosPorHuella.get(huella);
    if (existente != null) {
      return new Decision(ResultadoTipo.DUPLICADO_EXACTO, huella, existente);
    }
    if (c.referencia() == null || c.referencia().isBlank()) {
      // same day/amount/direction without reference and different description → review
      Optional<Map.Entry<String, UUID>> mismoMontoSinRef =
          confirmadosPorHuella.entrySet().stream()
              .filter(
                  e -> {
                    // cannot decode huella; caller should use separate index for soft match
                    return false;
                  })
              .findFirst();
      if (mismoMontoSinRef.isPresent()) {
        return new Decision(
            ResultadoTipo.POSIBLE_DUPLICADO, huella, mismoMontoSinRef.get().getValue());
      }
    }
    return new Decision(ResultadoTipo.NUEVO, huella, null);
  }

  public Decision evaluarConIndiceBlando(
      Candidato c, Map<String, UUID> indiceSinReferenciaMismaClave) {
    Decision base = evaluar(c);
    if (base.tipo() != ResultadoTipo.NUEVO) {
      return base;
    }
    if (c.referencia() == null || c.referencia().isBlank()) {
      String claveBlanda =
          c.cuentaId()
              + "|"
              + c.fecha()
              + "|"
              + c.monto().toPlainString()
              + "|"
              + c.direccion();
      UUID otro = indiceSinReferenciaMismaClave.get(claveBlanda);
      if (otro != null) {
        String huella =
            HuellaMovimiento.calcular(
                c.cuentaId(), c.fecha(), c.monto(), c.direccion(), c.referencia(), c.descripcion());
        return new Decision(ResultadoTipo.POSIBLE_DUPLICADO, huella, otro);
      }
    }
    return base;
  }

  public static String claveBlanda(
      UUID cuentaId, LocalDate fecha, BigDecimal monto, String direccion) {
    return cuentaId + "|" + fecha + "|" + monto.toPlainString() + "|" + direccion;
  }
}
