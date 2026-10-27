package com.finanzas.aplicacion.persistencia;

import com.finanzas.extraccion.ExtractorCsv;
import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.ObligacionSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-process domain store for MVP demo wiring. Durable copies of corridas/auditoría also go to JDBC.
 * Configuration floors have no factory defaults.
 */
@Component
public class InMemoryDomainStore {

  public record Config(
      BigDecimal minimoSeguridadCop,
      BigDecimal minimoSeguridadUsd,
      boolean llmHabilitado,
      int version) {}

  public record Documento(
      UUID id,
      String tipo,
      String estado,
      String sha256,
      String nombre,
      String mime,
      String ruta,
      List<ExtractorCsv.CampoExtraido> campos) {}

  public record Revision(
      UUID id,
      String asunto,
      String estado,
      UUID documentoId,
      Integer pagina,
      Integer fila,
      String motivo,
      UUID movimientoId) {}

  public record Cuenta(
      UUID id, String alias, String tipo, CurrencyCode moneda, String numeroEnmascarado) {}

  public record Movimiento(
      UUID id,
      UUID cuentaId,
      LocalDate fechaValor,
      String direccion,
      BigDecimal monto,
      CurrencyCode moneda,
      String descripcion,
      String referencia,
      String huella,
      String clasificacion,
      String estado,
      int version,
      UUID duplicadoDe,
      int evidencias) {}

  public record Ingreso(
      UUID id,
      String concepto,
      String clase,
      String recurrencia,
      CurrencyCode moneda,
      BigDecimal bruto,
      BigDecimal neto,
      BigDecimal descuentos,
      Integer diaEsperado) {}

  public Config config = new Config(null, null, false, 1);
  public final Map<UUID, Documento> documentos = new ConcurrentHashMap<>();
  public final Map<String, UUID> documentosPorSha = new ConcurrentHashMap<>();
  public final Map<UUID, Revision> revisiones = new ConcurrentHashMap<>();
  public final Map<UUID, Cuenta> cuentas = new ConcurrentHashMap<>();
  public final Map<UUID, Movimiento> movimientos = new ConcurrentHashMap<>();
  public final Map<UUID, Ingreso> ingresos = new ConcurrentHashMap<>();
  public final Map<UUID, ObligacionSnapshot> obligaciones = new ConcurrentHashMap<>();
  public final Map<UUID, Map<String, Object>> corridas = new ConcurrentHashMap<>();
  public final List<Map<String, Object>> reportes = new ArrayList<>();

  public synchronized Config updateConfig(BigDecimal cop, BigDecimal usd, Boolean llm) {
    config =
        new Config(
            cop != null ? cop : config.minimoSeguridadCop(),
            usd != null ? usd : config.minimoSeguridadUsd(),
            llm != null ? llm : config.llmHabilitado(),
            config.version() + 1);
    return config;
  }

  public Optional<Movimiento> movimientoPorHuella(UUID cuentaId, String huella) {
    return movimientos.values().stream()
        .filter(
            m ->
                m.cuentaId().equals(cuentaId)
                    && m.huella().equals(huella)
                    && "CONFIRMADO".equals(m.estado()))
        .findFirst();
  }
}
