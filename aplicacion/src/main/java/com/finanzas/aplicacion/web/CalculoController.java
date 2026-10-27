package com.finanzas.aplicacion.web;

import com.finanzas.aplicacion.persistencia.InMemoryDomainStore;
import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.CurrencyMismatchException;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.EfectoAbono;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.finanzas.ObligacionSnapshot;
import com.finanzas.finanzas.flujo.EventoFlujo;
import com.finanzas.finanzas.flujo.FlujoDiarioEngine;
import com.finanzas.finanzas.flujo.PeriodoDia;
import com.finanzas.finanzas.flujo.ResultadoFlujo;
import com.finanzas.optimizacion.ComparacionEstrategias;
import com.finanzas.optimizacion.DeudaEstrategia;
import com.finanzas.optimizacion.PriorizacionEngine;
import com.finanzas.optimizacion.ResultadoEstrategia;
import com.finanzas.proyecciones.AmortizacionEngine;
import com.finanzas.proyecciones.TasaMensualConverter;
import com.finanzas.reportes.ReporteEjecutivoBuilder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CalculoController {

  private final InMemoryDomainStore store;
  private final PriorizacionEngine priorizacion;
  private final AmortizacionEngine amortizacion;
  private final ReporteEjecutivoBuilder reportes;
  private final FlujoDiarioEngine flujoEngine = new FlujoDiarioEngine();
  private final JdbcTemplate jdbc;

  public CalculoController(
      InMemoryDomainStore store,
      PriorizacionEngine priorizacion,
      AmortizacionEngine amortizacion,
      ReporteEjecutivoBuilder reportes,
      JdbcTemplate jdbc) {
    this.store = store;
    this.priorizacion = priorizacion;
    this.amortizacion = amortizacion;
    this.reportes = reportes;
    this.jdbc = jdbc;
  }

  @GetMapping("/flujo-caja")
  public ResponseEntity<?> flujoCaja(
      @RequestParam String desde,
      @RequestParam String hasta,
      @RequestParam(defaultValue = "BASE") String escenario,
      @RequestParam String moneda,
      @RequestParam(required = false) BigDecimal saldoApertura,
      @RequestParam(required = false) Boolean conEventosDemo) {
    CurrencyCode m = CurrencyCode.parse(moneda);
    BigDecimal piso =
        m == CurrencyCode.COP
            ? store.config.minimoSeguridadCop()
            : store.config.minimoSeguridadUsd();

    List<EventoFlujo> eventos = new ArrayList<>();
    // Confirmed non-transfer movements as events
    for (var mov : store.movimientos.values()) {
      if (!"CONFIRMADO".equals(mov.estado()) || mov.moneda() != m) {
        continue;
      }
      if ("TRANSFERENCIA_INTERNA".equals(mov.clasificacion())) {
        continue;
      }
      if ("CREDITO".equals(mov.direccion()) && "INGRESO_REAL".equals(mov.clasificacion())) {
        eventos.add(
            EventoFlujo.entrada(
                mov.fechaValor(), m, mov.monto(), mov.descripcion(), EventoFlujo.TipoEvento.INGRESO));
      } else if ("DEBITO".equals(mov.direccion())) {
        eventos.add(
            EventoFlujo.salida(
                mov.fechaValor(),
                m,
                mov.monto(),
                mov.descripcion(),
                EventoFlujo.TipoEvento.OTRO));
      }
    }

    BigDecimal apertura = saldoApertura == null ? Dinero.monto("0.00") : saldoApertura;
    boolean esenciales = !eventos.isEmpty(); // simplified: presence of any essential-like events
    // For API demo CF-01 style when piso set and no movements, allow empty calendar
    ResultadoFlujo r =
        flujoEngine.calcular(
            m,
            LocalDate.parse(desde),
            LocalDate.parse(hasta),
            apertura,
            LocalDate.parse(desde),
            piso,
            Dinero.monto("0.00"),
            esenciales || piso == null,
            eventos);

    UUID corridaId = UUID.randomUUID();
    persistirCorridaFlujo(corridaId, r);
    store.corridas.put(corridaId, Map.of("tipo", "FLUJO", "resultado", r));

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("corridaId", corridaId.toString());
    resp.put("algoritmo", FlujoDiarioEngine.ALGORITMO);
    resp.put("escenario", escenario);
    resp.put("moneda", m.name());
    resp.put("definitiva", r.definitiva());
    resp.put("esEstimacion", true);
    resp.put("esencialesNoRegistrados", r.esencialesNoRegistrados());
    resp.put("viable", r.viable());
    resp.put("variacionDelMes", r.variacionDelMes().toPlainString());
    resp.put(
        "disponibleParaComprometer",
        r.disponibleParaComprometer() == null
            ? null
            : r.disponibleParaComprometer().toPlainString());
    resp.put("abonosRecomendados", List.of());
    resp.put("motivosBloqueo", r.motivosBloqueo());
    resp.put("reglasAplicadas", r.reglasAplicadas());
    List<Map<String, Object>> dias = new ArrayList<>();
    for (PeriodoDia d : r.dias()) {
      if (d.entradas().signum() == 0
          && d.salidas().signum() == 0
          && !d.fecha().equals(LocalDate.parse(desde))) {
        // still include inviables
        if (d.viable()) {
          continue;
        }
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("fecha", d.fecha().toString());
      row.put("moneda", d.moneda().name());
      row.put("saldoFinal", d.saldoFinal().toPlainString());
      row.put("piso", d.piso() == null ? null : d.piso().toPlainString());
      row.put("libre", d.libre() == null ? null : d.libre().toPlainString());
      row.put("viable", d.viable());
      row.put("faltante", d.faltante().toPlainString());
      dias.add(row);
    }
    resp.put("dias", dias);
    return ResponseEntity.ok(resp);
  }

  @PostMapping("/flujo-caja/calcular")
  public ResponseEntity<?> flujoCalcular(@RequestBody Map<String, Object> body) {
    CurrencyCode m = CurrencyCode.parse(String.valueOf(body.get("moneda")));
    LocalDate desde = LocalDate.parse(String.valueOf(body.get("desde")));
    LocalDate hasta = LocalDate.parse(String.valueOf(body.get("hasta")));
    BigDecimal piso =
        body.get("piso") == null ? null : new BigDecimal(body.get("piso").toString());
    BigDecimal apertura = new BigDecimal(body.get("saldoApertura").toString());
    boolean esenciales =
        body.get("esencialesRegistrados") == null
            || Boolean.parseBoolean(body.get("esencialesRegistrados").toString());
    List<EventoFlujo> eventos = new ArrayList<>();
    if (body.get("eventos") instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> ev) {
          LocalDate f = LocalDate.parse(ev.get("fecha").toString());
          BigDecimal monto = new BigDecimal(ev.get("monto").toString());
          String dir = ev.get("direccion").toString();
          String concepto = ev.get("concepto").toString();
          if ("ENTRADA".equals(dir)) {
            eventos.add(
                EventoFlujo.entrada(f, m, monto, concepto, EventoFlujo.TipoEvento.INGRESO));
          } else {
            eventos.add(
                EventoFlujo.salida(f, m, monto, concepto, EventoFlujo.TipoEvento.ESENCIAL));
          }
        }
      }
    }
    ResultadoFlujo r =
        flujoEngine.calcular(
            m, desde, hasta, apertura, desde, piso, Dinero.monto("0.00"), esenciales, eventos);
    UUID corridaId = UUID.randomUUID();
    persistirCorridaFlujo(corridaId, r);
    store.corridas.put(corridaId, Map.of("tipo", "FLUJO", "resultado", r));
    return ResponseEntity.ok(
        Map.of(
            "corridaId",
            corridaId.toString(),
            "viable",
            r.viable(),
            "disponibleParaComprometer",
            r.disponibleParaComprometer().toPlainString(),
            "variacionDelMes",
            r.variacionDelMes().toPlainString(),
            "motivosBloqueo",
            r.motivosBloqueo()));
  }

  @PostMapping("/estrategias/comparaciones")
  public ResponseEntity<?> comparar(@RequestBody Map<String, Object> body) {
    CurrencyCode moneda = CurrencyCode.parse(String.valueOf(body.get("moneda")));
    @SuppressWarnings("unchecked")
    List<String> ids = (List<String>) body.get("obligacionIds");
    List<DeudaEstrategia> deudas = new ArrayList<>();
    try {
      for (String idStr : ids) {
        UUID id = UUID.fromString(idStr);
        ObligacionSnapshot o = store.obligaciones.get(id);
        if (o == null) {
          return ProblemSupport.of(
              HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Obligación no encontrada", idStr);
        }
        if (o.moneda() != moneda) {
          throw new CurrencyMismatchException(moneda, o.moneda());
        }
        var tasa = TasaMensualConverter.convertir(o.tasaValor(), o.tasaPeriodo(), o.tasaBase());
        if (!tasa.usable()) {
          return ProblemSupport.proyeccionBloqueada(
              ((TasaMensualConverter.Bloqueada) tasa).motivo(),
              "No se puede comparar con supuestos incompletos",
              id.toString(),
              List.of("tasa"));
        }
        BigDecimal cuota =
            o.cuotaContractual() != null ? o.cuotaContractual() : o.pagoMinimo();
        deudas.add(
            new DeudaEstrategia(
                o.identidadId(),
                o.alias(),
                o.moneda(),
                o.saldoTotal(),
                ((TasaMensualConverter.Usable) tasa).tasaMensualEfectiva(),
                cuota));
      }
    } catch (CurrencyMismatchException ex) {
      return ProblemSupport.of(
          HttpStatus.UNPROCESSABLE_ENTITY,
          MotivoBloqueo.MONEDA_MIXTA_SIN_CONVERSION,
          "Moneda mixta sin conversión",
          ex.getMessage());
    }

    ComparacionEstrategias cmp =
        priorizacion.comparar(
            deudas,
            new BigDecimal(body.get("presupuestoDeudaMensual").toString()),
            LocalDate.parse(String.valueOf(body.get("horizonteInicio"))),
            Boolean.TRUE.equals(body.get("presupuestoEsSupuesto")));

    UUID corridaId = UUID.randomUUID();
    store.corridas.put(corridaId, Map.of("tipo", "ESTRATEGIA", "resultado", cmp));
    persistirComparacion(corridaId, cmp);

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("supuestosHash", cmp.supuestosHash());
    resp.put("algoritmo", cmp.algoritmo());
    resp.put("amortizacion", cmp.amortizacion());
    resp.put("moneda", cmp.moneda().name());
    resp.put("esEstimacion", true);
    resp.put("corridaId", corridaId.toString());
    List<Map<String, Object>> estrategias = new ArrayList<>();
    for (ResultadoEstrategia r : cmp.estrategias()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("nombre", r.nombre().name());
      row.put("estado", r.estado());
      row.put("definitiva", r.definitiva());
      row.put(
          "interesTotal",
          r.interesTotal() == null ? null : r.interesTotal().toPlainString());
      row.put(
          "fechaLiquidacionPortafolio",
          r.fechaLiquidacionPortafolio() == null
              ? null
              : r.fechaLiquidacionPortafolio().toString());
      Map<String, String> liq = new LinkedHashMap<>();
      r.liquidacionPorDeuda()
          .forEach((k, v) -> liq.put(etiquetaDeuda(k), v.toString()));
      row.put("liquidacion", liq);
      row.put("esRecomendada", r.esRecomendada());
      estrategias.add(row);
    }
    resp.put("estrategias", estrategias);
    resp.put("justificacion", cmp.justificacion());
    return ResponseEntity.ok(resp);
  }

  @PostMapping("/proyecciones/amortizaciones")
  public ResponseEntity<?> amortizar(@RequestBody Map<String, Object> body) {
    UUID id = UUID.fromString(String.valueOf(body.get("obligacionId")));
    ObligacionSnapshot o = store.obligaciones.get(id);
    if (o == null) {
      return ProblemSupport.of(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Obligación no encontrada", "");
    }
    EfectoAbono efecto =
        body.get("efectoAbono") == null
            ? o.efectoAbono()
            : EfectoAbono.valueOf(body.get("efectoAbono").toString());
    BigDecimal pago =
        body.get("pagoPeriodo") == null
            ? null
            : new BigDecimal(body.get("pagoPeriodo").toString());
    LocalDate primerPago =
        body.get("primerPago") == null
            ? LocalDate.now(java.time.ZoneId.of("America/Bogota")).withDayOfMonth(Math.min(28, 28))
            : LocalDate.parse(body.get("primerPago").toString());
    if (o.diaVencimiento() != null && body.get("primerPago") == null) {
      LocalDate base = LocalDate.of(2026, 11, 1);
      int dia = Math.min(o.diaVencimiento(), base.lengthOfMonth());
      primerPago = base.withDayOfMonth(dia);
    }

    AmortizacionEngine.Resultado r = amortizacion.proyectar(o, pago, primerPago, efecto);
    if (!r.definitiva()) {
      String codigo =
          r.motivosBloqueo().isEmpty()
              ? MotivoBloqueo.TASA_AUSENTE
              : r.motivosBloqueo().getFirst();
      return ProblemSupport.proyeccionBloqueada(
          codigo,
          "La proyección definitiva está bloqueada.",
          id.toString(),
          List.of(codigo.contains("TASA") ? "tasa" : "efectoAbono"));
    }

    UUID corridaId = UUID.randomUUID();
    store.corridas.put(corridaId, Map.of("tipo", "AMORTIZACION", "resultado", r));
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("corridaId", corridaId.toString());
    resp.put("algoritmo", AmortizacionEngine.ALGORITMO);
    resp.put("moneda", r.moneda().name());
    resp.put("definitiva", true);
    resp.put("esEstimacion", true);
    resp.put("tasaMensualEfectivaUsada", r.tasaMensualEfectivaUsada().toPlainString());
    resp.put("interesTotal", r.interesTotal().toPlainString());
    resp.put("fechaLiquidacion", r.fechaLiquidacion().toString());
    List<Map<String, Object>> periodos = new ArrayList<>();
    for (var p : r.periodos()) {
      periodos.add(
          Map.of(
              "fecha",
              p.fecha().toString(),
              "moneda",
              p.moneda().name(),
              "saldoInicial",
              p.saldoInicial().toPlainString(),
              "interes",
              p.interes().toPlainString(),
              "pago",
              p.pago().toPlainString(),
              "saldoFinal",
              p.saldoFinal().toPlainString()));
    }
    resp.put("periodos", periodos);
    return ResponseEntity.ok(resp);
  }

  @GetMapping("/corridas/{corridaId}")
  public ResponseEntity<?> corrida(@PathVariable UUID corridaId) {
    var c = store.corridas.get(corridaId);
    if (c == null) {
      return ProblemSupport.of(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Corrida no encontrada", "");
    }
    return ResponseEntity.ok(Map.of("corridaId", corridaId.toString(), "payload", c));
  }

  @GetMapping("/reportes/ejecutivo")
  public Map<String, Object> reporte(@RequestParam(required = false) UUID escenarioId) {
    UUID corridaId =
        store.corridas.keySet().stream().findFirst().orElse(UUID.randomUUID());
    Object payload = store.corridas.get(corridaId);
    ReporteEjecutivoBuilder.Reporte rep;
    if (payload instanceof Map<?, ?> m && m.get("resultado") instanceof ResultadoFlujo flujo) {
      rep = reportes.desdeFlujo(corridaId, flujo);
    } else if (payload instanceof Map<?, ?> m
        && m.get("resultado") instanceof ComparacionEstrategias cmp) {
      rep = reportes.desdeComparacion(corridaId, cmp);
    } else {
      rep =
          new ReporteEjecutivoBuilder.Reporte(
              UUID.randomUUID(),
              java.time.OffsetDateTime.now(java.time.ZoneId.of("America/Bogota")),
              true,
              List.of(corridaId),
              List.of(),
              List.of(),
              List.of(),
              List.of("Sin corridas previas; genere flujo o estrategias primero."),
              false);
    }
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("reporteId", rep.reporteId().toString());
    resp.put("generadoEn", rep.generadoEn().toString());
    resp.put("esEstimacion", true);
    resp.put("corridaIds", rep.corridaIds().stream().map(UUID::toString).toList());
    resp.put("hechos", rep.hechos());
    resp.put("inferencias", rep.inferencias());
    resp.put(
        "recomendaciones",
        rep.recomendaciones().stream()
            .map(
                r ->
                    Map.of(
                        "texto",
                        r.texto(),
                        "reglas",
                        r.reglas(),
                        "corridaId",
                        r.corridaId().toString()))
            .toList());
    resp.put("advertencias", rep.advertencias());
    resp.put("deudaNuevaRecomendada", false);
    return resp;
  }

  private void persistirCorridaFlujo(UUID id, ResultadoFlujo r) {
    jdbc.update(
        """
        INSERT INTO corrida
          (id, escenario_id, supuestos_hash, algoritmo, creado_en, moneda, tipo, estrategia,
           estado, definitiva, es_estimacion, motivos_bloqueo, interes_total,
           tasa_mensual_efectiva_usada, fecha_liquidacion_portafolio, es_recomendada, justificacion, payload_json)
        VALUES (?, NULL, ?, ?, now(), ?, 'FLUJO', NULL, ?, ?, true, ?, NULL, NULL, NULL, false, NULL, NULL)
        """,
        id,
        "flujo|" + r.moneda(),
        FlujoDiarioEngine.ALGORITMO,
        r.moneda().name(),
        r.viable() ? "COMPLETA" : "NO_DEFINITIVA",
        r.definitiva(),
        String.join(",", r.motivosBloqueo()));
  }

  private void persistirComparacion(UUID id, ComparacionEstrategias cmp) {
    jdbc.update(
        """
        INSERT INTO corrida
          (id, escenario_id, supuestos_hash, algoritmo, creado_en, moneda, tipo, estrategia,
           estado, definitiva, es_estimacion, motivos_bloqueo, interes_total,
           tasa_mensual_efectiva_usada, fecha_liquidacion_portafolio, es_recomendada, justificacion, payload_json)
        VALUES (?, NULL, ?, ?, now(), ?, 'ESTRATEGIA', NULL, 'COMPLETA', true, true, '', NULL, NULL, NULL, false, ?, NULL)
        """,
        id,
        cmp.supuestosHash(),
        cmp.algoritmo(),
        cmp.moneda().name(),
        cmp.justificacion());
  }

  private String etiquetaDeuda(UUID id) {
    ObligacionSnapshot o = store.obligaciones.get(id);
    if (o == null) {
      return id.toString();
    }
    // Keep short labels D1/D2/D3 when synthetic ids match EST-01
    String s = id.toString();
    if (s.endsWith("0001")) {
      return "D1";
    }
    if (s.endsWith("0002")) {
      return "D2";
    }
    if (s.endsWith("0003")) {
      return "D3";
    }
    return o.alias();
  }
}
