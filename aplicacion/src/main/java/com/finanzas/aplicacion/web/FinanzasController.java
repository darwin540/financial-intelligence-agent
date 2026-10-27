package com.finanzas.aplicacion.web;

import com.finanzas.aplicacion.persistencia.AuditoriaRepository;
import com.finanzas.aplicacion.persistencia.InMemoryDomainStore;
import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import com.finanzas.finanzas.EfectoAbono;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.finanzas.ObligacionSnapshot;
import com.finanzas.finanzas.TasaBase;
import com.finanzas.finanzas.TasaPeriodo;
import com.finanzas.finanzas.fx.ConversionMoneda;
import com.finanzas.finanzas.ingresos.CalendarioIngresosEngine;
import com.finanzas.proyecciones.TasaMensualConverter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FinanzasController {

  private final InMemoryDomainStore store;
  private final AuditoriaRepository auditoria;
  private final Map<UUID, ConversionMoneda> conversiones = new ConcurrentHashMap<>();

  public FinanzasController(InMemoryDomainStore store, AuditoriaRepository auditoria) {
    this.store = store;
    this.auditoria = auditoria;
  }

  @PostMapping("/cuentas")
  public Map<String, Object> crearCuenta(@RequestBody Map<String, Object> body) {
    UUID id = UUID.randomUUID();
    CurrencyCode moneda = CurrencyCode.parse(String.valueOf(body.get("moneda")));
    String ultimos4 = body.get("ultimos4") == null ? "0000" : body.get("ultimos4").toString();
    var c =
        new InMemoryDomainStore.Cuenta(
            id,
            String.valueOf(body.get("alias")),
            String.valueOf(body.getOrDefault("tipo", "AHORROS")),
            moneda,
            "****" + ultimos4);
    store.cuentas.put(id, c);
    auditoria.insertar("cuenta", id, id, "CREAR", "Cuenta " + c.numeroEnmascarado(), null);
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("cuentaId", id.toString());
    resp.put("alias", c.alias());
    resp.put("moneda", c.moneda().name());
    resp.put("numeroEnmascarado", c.numeroEnmascarado());
    return resp;
  }

  @GetMapping("/movimientos")
  public Map<String, Object> movimientos(
      @RequestParam(required = false) UUID cuentaId,
      @RequestParam(required = false) String estado) {
    List<Map<String, Object>> elementos = new ArrayList<>();
    for (var m : store.movimientos.values()) {
      if (cuentaId != null && !m.cuentaId().equals(cuentaId)) {
        continue;
      }
      if (estado != null && !estado.equals(m.estado())) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("movimientoId", m.id().toString());
      row.put("cuentaId", m.cuentaId().toString());
      row.put("fechaValor", m.fechaValor().toString());
      row.put("direccion", m.direccion());
      row.put("monto", m.monto().toPlainString());
      row.put("moneda", m.moneda().name());
      row.put("clasificacion", m.clasificacion());
      row.put("estado", m.estado());
      row.put("huella", m.huella());
      row.put("version", m.version());
      row.put("evidencias", m.evidencias());
      elementos.add(row);
    }
    return Map.of("elementos", elementos, "total", elementos.size());
  }

  @PostMapping("/movimientos/{movimientoId}/versiones")
  public ResponseEntity<?> nuevaVersionMovimiento(
      @PathVariable UUID movimientoId, @RequestBody Map<String, Object> body) {
    var m = store.movimientos.get(movimientoId);
    if (m == null) {
      return ProblemSupport.of(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Movimiento no encontrado", "");
    }
    String clasificacion =
        body.get("clasificacion") == null ? m.clasificacion() : body.get("clasificacion").toString();
    if ("INGRESO_REAL".equals(clasificacion)
        && "TRANSFERENCIA_INTERNA".equals(m.clasificacion())) {
      return ProblemSupport.of(
          HttpStatus.UNPROCESSABLE_ENTITY,
          MotivoBloqueo.TRANSFERENCIA_NO_ES_INGRESO,
          "Transferencia no es ingreso",
          "");
    }
    var nuevo =
        new InMemoryDomainStore.Movimiento(
            m.id(),
            m.cuentaId(),
            m.fechaValor(),
            m.direccion(),
            m.monto(),
            m.moneda(),
            m.descripcion(),
            m.referencia(),
            m.huella(),
            clasificacion,
            m.estado(),
            m.version() + 1,
            m.duplicadoDe(),
            m.evidencias());
    store.movimientos.put(movimientoId, nuevo);
    auditoria.insertar(
        "movimiento",
        movimientoId,
        null,
        "NUEVA_VERSION",
        "Corrección v" + nuevo.version(),
        String.valueOf(body.get("motivo")));
    return ResponseEntity.ok(
        Map.of(
            "movimientoId",
            movimientoId.toString(),
            "version",
            nuevo.version(),
            "clasificacion",
            nuevo.clasificacion()));
  }

  @PostMapping("/ingresos")
  public ResponseEntity<?> crearIngreso(@RequestBody Map<String, Object> body) {
    BigDecimal bruto = decimalOrNull(body.get("bruto"));
    BigDecimal neto = decimalOrNull(body.get("neto"));
    BigDecimal descuentos = decimalOrNull(body.get("descuentos"));
    try {
      CalendarioIngresosEngine.validarCoherenciaMontos(bruto, neto, descuentos);
    } catch (CalendarioIngresosEngine.IngresoIncoherenteException e) {
      return ProblemSupport.of(
          HttpStatus.UNPROCESSABLE_ENTITY,
          MotivoBloqueo.MONTOS_INGRESO_INCOHERENTES,
          "Montos de ingreso incoherentes",
          "");
    }
    UUID id = UUID.randomUUID();
    var ing =
        new InMemoryDomainStore.Ingreso(
            id,
            String.valueOf(body.get("concepto")),
            String.valueOf(body.get("clase")),
            String.valueOf(body.get("recurrencia")),
            CurrencyCode.parse(String.valueOf(body.get("moneda"))),
            bruto,
            neto,
            descuentos,
            body.get("diaEsperado") == null
                ? null
                : Integer.valueOf(body.get("diaEsperado").toString()));
    store.ingresos.put(id, ing);
    return ResponseEntity.status(201).body(Map.of("ingresoId", id.toString()));
  }

  @GetMapping("/calendario-ingresos")
  public Map<String, Object> calendario(
      @RequestParam String desde, @RequestParam String hasta) {
    List<CalendarioIngresosEngine.IngresoDefinicion> defs = new ArrayList<>();
    for (var i : store.ingresos.values()) {
      defs.add(
          new CalendarioIngresosEngine.IngresoDefinicion(
              i.concepto(),
              CalendarioIngresosEngine.ClaseIngreso.valueOf(i.clase()),
              CalendarioIngresosEngine.Recurrencia.valueOf(i.recurrencia()),
              i.moneda(),
              i.bruto(),
              i.neto(),
              i.descuentos(),
              i.diaEsperado()));
    }
    var cal =
        new CalendarioIngresosEngine()
            .proyectar(LocalDate.parse(desde), LocalDate.parse(hasta), defs);
    Map<String, Object> escenarios = new LinkedHashMap<>();
    for (var e : CalendarioIngresosEngine.Escenario.values()) {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (var o : cal.escenarios().get(e)) {
        rows.add(
            Map.of(
                "concepto",
                o.concepto(),
                "fecha",
                o.fecha().toString(),
                "moneda",
                o.moneda().name(),
                "montoNeto",
                o.montoNeto().toPlainString(),
                "naturaleza",
                o.naturaleza()));
      }
      escenarios.put(e.name(), rows);
    }
    List<Map<String, Object>> vars = new ArrayList<>();
    for (var v : cal.variablesNoProyectados()) {
      vars.add(
          Map.of(
              "concepto",
              v.concepto(),
              "moneda",
              v.moneda().name(),
              "montoFuturo",
              v.montoFuturo().toPlainString(),
              "motivo",
              v.motivo()));
    }
    return Map.of(
        "desde",
        desde,
        "hasta",
        hasta,
        "escenarios",
        escenarios,
        "variablesNoProyectados",
        vars);
  }

  @PostMapping("/obligaciones")
  public Map<String, Object> crearObligacion(@RequestBody Map<String, Object> body) {
    UUID id = UUID.randomUUID();
    CurrencyCode moneda = CurrencyCode.parse(String.valueOf(body.get("moneda")));
    Map<?, ?> tasa = body.get("tasa") instanceof Map<?, ?> t ? t : null;
    BigDecimal tasaValor =
        tasa == null || tasa.get("valor") == null
            ? null
            : new BigDecimal(tasa.get("valor").toString());
    TasaPeriodo periodo =
        tasa == null || tasa.get("periodo") == null
            ? null
            : TasaPeriodo.valueOf(tasa.get("periodo").toString());
    TasaBase base =
        tasa == null || tasa.get("base") == null
            ? null
            : TasaBase.valueOf(tasa.get("base").toString());
    EfectoAbono efecto =
        body.get("efectoAbono") == null
            ? EfectoAbono.DESCONOCIDO
            : EfectoAbono.valueOf(body.get("efectoAbono").toString());
    ObligacionSnapshot obl =
        new ObligacionSnapshot(
            id,
            String.valueOf(body.get("alias")),
            moneda,
            decimalOrNull(body.get("saldoTotal")),
            decimalOrNull(body.get("pagoMinimo")),
            decimalOrNull(body.get("cuotaContractual")),
            tasaValor,
            periodo,
            base,
            body.get("diaVencimiento") == null
                ? null
                : Integer.valueOf(body.get("diaVencimiento").toString()),
            efecto,
            body.get("reglaRecalculoCuota") == null
                ? null
                : body.get("reglaRecalculoCuota").toString(),
            decimalOrNull(body.get("cupoTotal")),
            decimalOrNull(body.get("cupoDisponible")));
    store.obligaciones.put(id, obl);

    List<String> motivos = new ArrayList<>();
    boolean proyeccionPosible = true;
    if (efecto == EfectoAbono.DESCONOCIDO) {
      motivos.add(MotivoBloqueo.EFECTO_ABONO_DESCONOCIDO);
      proyeccionPosible = false;
    }
    var tasaR = TasaMensualConverter.convertir(tasaValor, periodo, base);
    if (!tasaR.usable()) {
      motivos.add(((TasaMensualConverter.Bloqueada) tasaR).motivo());
      proyeccionPosible = false;
    }

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("obligacionId", id.toString());
    resp.put("moneda", moneda.name());
    resp.put(
        "saldoTotal",
        obl.saldoTotal() == null ? null : obl.saldoTotal().toPlainString());
    resp.put(
        "pagoMinimo",
        obl.pagoMinimo() == null ? null : obl.pagoMinimo().toPlainString());
    resp.put(
        "cuotaContractual",
        obl.cuotaContractual() == null ? null : obl.cuotaContractual().toPlainString());
    resp.put(
        "salidaParaEvitarMora",
        obl.salidaParaEvitarMora() == null ? null : obl.salidaParaEvitarMora().toPlainString());
    resp.put(
        "salidaPactada",
        obl.salidaPactada() == null ? null : obl.salidaPactada().toPlainString());
    resp.put("cupoEsIngreso", false);
    resp.put("proyeccionDefinitivaPosible", proyeccionPosible && motivos.isEmpty());
    resp.put("motivosBloqueo", motivos);
    resp.put("estado", obl.saldoTotal() == null ? "PENDIENTE_DATOS" : "VIGENTE");
    return resp;
  }

  @GetMapping("/obligaciones")
  public Map<String, Object> listarObligaciones() {
    List<Map<String, Object>> elementos = new ArrayList<>();
    for (var o : store.obligaciones.values()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("obligacionId", o.identidadId().toString());
      m.put("alias", o.alias());
      m.put("moneda", o.moneda().name());
      m.put("saldoTotal", o.saldoTotal() == null ? null : o.saldoTotal().toPlainString());
      m.put("pagoMinimo", o.pagoMinimo() == null ? null : o.pagoMinimo().toPlainString());
      m.put(
          "cuotaContractual",
          o.cuotaContractual() == null ? null : o.cuotaContractual().toPlainString());
      m.put("cupoDisponible", o.cupoDisponible() == null ? null : o.cupoDisponible().toPlainString());
      m.put("efectoAbono", o.efectoAbono() == null ? null : o.efectoAbono().name());
      elementos.add(m);
    }
    return Map.of("elementos", elementos, "total", elementos.size());
  }

  @PostMapping("/obligaciones/{obligacionId}/versiones")
  public ResponseEntity<?> versionObligacion(
      @PathVariable UUID obligacionId, @RequestBody Map<String, Object> body) {
    var o = store.obligaciones.get(obligacionId);
    if (o == null) {
      return ProblemSupport.of(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Obligación no encontrada", "");
    }
    EfectoAbono efecto =
        body.get("efectoAbono") == null
            ? o.efectoAbono()
            : EfectoAbono.valueOf(body.get("efectoAbono").toString());
    ObligacionSnapshot nuevo =
        new ObligacionSnapshot(
            o.identidadId(),
            o.alias(),
            o.moneda(),
            o.saldoTotal(),
            o.pagoMinimo(),
            o.cuotaContractual(),
            o.tasaValor(),
            o.tasaPeriodo(),
            o.tasaBase(),
            o.diaVencimiento(),
            efecto,
            o.reglaRecalculoCuota(),
            o.cupoTotal(),
            o.cupoDisponible());
    store.obligaciones.put(obligacionId, nuevo);
    auditoria.insertar(
        "obligacion",
        obligacionId,
        null,
        "NUEVA_VERSION",
        "Nueva versión obligación",
        String.valueOf(body.get("motivo")));
    return ResponseEntity.ok(Map.of("obligacionId", obligacionId.toString(), "efectoAbono", efecto.name()));
  }

  /**
   * Point-in-time FX conversion. Rate is on this operation only — never read from configuration.
   */
  @PostMapping("/conversiones-moneda")
  public ResponseEntity<?> conversion(@RequestBody Map<String, Object> body) {
    CurrencyCode origen = CurrencyCode.parse(String.valueOf(body.get("monedaOrigen")));
    CurrencyCode destino = CurrencyCode.parse(String.valueOf(body.get("monedaDestino")));
    if (origen == destino) {
      return ProblemSupport.of(
          HttpStatus.UNPROCESSABLE_ENTITY,
          MotivoBloqueo.MONEDA_MIXTA_SIN_CONVERSION,
          "Monedas deben ser distintas",
          "");
    }
    UUID id = UUID.randomUUID();
    ConversionMoneda c;
    if (body.get("tipoCambioOperacion") != null && body.get("montoDestino") == null) {
      c =
          ConversionMoneda.deTipoIndicado(
              id,
              LocalDate.parse(String.valueOf(body.get("fechaValor"))),
              new BigDecimal(body.get("montoOrigen").toString()),
              origen,
              destino,
              new BigDecimal(body.get("tipoCambioOperacion").toString()),
              UUID.fromString(String.valueOf(body.get("cuentaOrigenId"))),
              UUID.fromString(String.valueOf(body.get("cuentaDestinoId"))),
              body.get("nota") == null ? null : body.get("nota").toString(),
              ConversionMoneda.Estado.CONFIRMADO);
    } else {
      c =
          ConversionMoneda.deMontos(
              id,
              LocalDate.parse(String.valueOf(body.get("fechaValor"))),
              new BigDecimal(body.get("montoOrigen").toString()),
              origen,
              new BigDecimal(body.get("montoDestino").toString()),
              destino,
              body.get("tipoCambioOperacion") == null
                  ? null
                  : new BigDecimal(body.get("tipoCambioOperacion").toString()),
              UUID.fromString(String.valueOf(body.get("cuentaOrigenId"))),
              UUID.fromString(String.valueOf(body.get("cuentaDestinoId"))),
              body.get("nota") == null ? null : body.get("nota").toString(),
              ConversionMoneda.Estado.CONFIRMADO);
    }
    conversiones.put(id, c);
    auditoria.insertar(
        "conversion_moneda",
        id,
        id,
        "CONFIRMAR",
        "Conversión puntual "
            + c.montoOrigen().toPlainString()
            + " "
            + c.monedaOrigen()
            + " → "
            + c.montoDestino().toPlainString()
            + " "
            + c.monedaDestino(),
        null);
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("conversionId", id.toString());
    resp.put("montoOrigen", c.montoOrigen().toPlainString());
    resp.put("monedaOrigen", c.monedaOrigen().name());
    resp.put("montoDestino", c.montoDestino().toPlainString());
    resp.put("monedaDestino", c.monedaDestino().name());
    resp.put(
        "tipoCambioOperacion",
        c.tipoCambioOperacion() == null ? null : c.tipoCambioOperacion().toPlainString());
    resp.put("reutilizable", false);
    return ResponseEntity.status(201).body(resp);
  }

  private static BigDecimal decimalOrNull(Object o) {
    return o == null ? null : Dinero.cuantizarMonto(new BigDecimal(o.toString()));
  }
}
