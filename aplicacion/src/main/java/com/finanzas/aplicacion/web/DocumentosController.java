package com.finanzas.aplicacion.web;

import com.finanzas.aplicacion.persistencia.AuditoriaRepository;
import com.finanzas.aplicacion.persistencia.InMemoryDomainStore;
import com.finanzas.aplicacion.seguridad.DocumentStore;
import com.finanzas.conciliacion.DeduplicadorMovimientos;
import com.finanzas.conciliacion.HuellaMovimiento;
import com.finanzas.extraccion.ExtractorCsv;
import com.finanzas.extraccion.ExtractorDocumento;
import com.finanzas.finanzas.CurrencyCode;
import com.finanzas.finanzas.Dinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentosController {

  private final InMemoryDomainStore store;
  private final DocumentStore documents;
  private final AuditoriaRepository auditoria;
  private final ExtractorDocumento extractor = new ExtractorDocumento();

  public DocumentosController(
      InMemoryDomainStore store, DocumentStore documents, AuditoriaRepository auditoria) {
    this.store = store;
    this.documents = documents;
    this.auditoria = auditoria;
  }

  @PostMapping("/documentos")
  public ResponseEntity<?> subir(
      @RequestParam("archivo") MultipartFile archivo, @RequestParam("tipo") String tipo)
      throws Exception {
    byte[] plain = archivo.getBytes();
    if (plain.length > 25 * 1024 * 1024) {
      return ProblemSupport.of(
          HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE", "Archivo demasiado grande", "");
    }
    var stored = documents.store(plain, archivo.getOriginalFilename());
    if (store.documentosPorSha.containsKey(stored.sha256Plain())) {
      return ProblemSupport.of(
          HttpStatus.CONFLICT, "DOCUMENTO_DUPLICADO", "Documento duplicado", "Mismo sha256_plano");
    }
    UUID id = UUID.randomUUID();
    var doc =
        new InMemoryDomainStore.Documento(
            id,
            tipo,
            "RECIBIDO",
            stored.sha256Plain(),
            archivo.getOriginalFilename(),
            archivo.getContentType() == null ? "application/octet-stream" : archivo.getContentType(),
            stored.relativePath(),
            List.of());
    store.documentos.put(id, doc);
    store.documentosPorSha.put(stored.sha256Plain(), id);
    auditoria.insertar("documento", id, id, "CREAR", "Documento recibido " + doc.nombre(), null);
    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("documentoId", id.toString());
    resp.put("tipo", tipo);
    resp.put("estado", "RECIBIDO");
    resp.put("sha256Plano", stored.sha256Plain());
    resp.put("nombreEnmascarado", enmascararNombre(archivo.getOriginalFilename()));
    return ResponseEntity.status(201).body(resp);
  }

  @PostMapping("/documentos/{documentoId}/extraccion")
  public ResponseEntity<?> extraer(@PathVariable UUID documentoId) {
    var doc = store.documentos.get(documentoId);
    if (doc == null) {
      return ProblemSupport.of(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Documento no encontrado", "");
    }
    byte[] plain = documents.load(doc.ruta());
    var resultado =
        extractor.extraer(doc.mime(), doc.nombre(), new java.io.ByteArrayInputStream(plain));
    var actualizado =
        new InMemoryDomainStore.Documento(
            doc.id(),
            doc.tipo(),
            resultado.estado(),
            doc.sha256(),
            doc.nombre(),
            doc.mime(),
            doc.ruta(),
            resultado.campos());
    store.documentos.put(documentoId, actualizado);

    // Open review items for monto fields / create pending movements for CSV rows
    if (resultado.extraccionAutomatica() && !resultado.campos().isEmpty()) {
      abrirRevisionesDesdeCsv(documentoId, resultado.campos());
    } else {
      store.revisiones.put(
          UUID.randomUUID(),
          new InMemoryDomainStore.Revision(
              UUID.randomUUID(),
              "CAMPO",
              "ABIERTA",
              documentoId,
              null,
              null,
              "Sin extracción automática (foto/PDF imagen o sin texto). Sin OCR.",
              null));
    }

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("documentoId", documentoId.toString());
    resp.put("estado", resultado.estado());
    long pendientes =
        resultado.campos().stream().filter(c -> "monto".equals(c.clave())).count();
    resp.put("camposPendientes", pendientes);
    resp.put("camposAmbiguos", 0);
    resp.put("extraccionAutomatica", resultado.extraccionAutomatica());
    return ResponseEntity.accepted().body(resp);
  }

  @GetMapping("/documentos/{documentoId}/campos")
  public Map<String, Object> campos(@PathVariable UUID documentoId) {
    var doc = store.documentos.get(documentoId);
    List<Map<String, Object>> elementos = new ArrayList<>();
    if (doc != null) {
      for (ExtractorCsv.CampoExtraido c : doc.campos()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("campoId", UUID.nameUUIDFromBytes((documentoId + ":" + c.fila() + ":" + c.clave()).getBytes()).toString());
        m.put("clave", c.clave());
        m.put("valorDecimal", c.valorDecimal() == null ? null : c.valorDecimal().toPlainString());
        m.put("pagina", c.pagina());
        m.put("fila", c.fila());
        m.put("confianza", c.confianza() == null ? null : c.confianza().toPlainString());
        m.put("naturaleza", c.naturaleza());
        m.put("estado", "PENDIENTE");
        m.put("vigenteEnFinanzas", false);
        elementos.add(m);
      }
    }
    return Map.of("elementos", elementos, "total", elementos.size());
  }

  @GetMapping("/revisiones")
  public Map<String, Object> revisiones(@RequestParam(defaultValue = "ABIERTA") String estado) {
    List<Map<String, Object>> elementos = new ArrayList<>();
    for (var r : store.revisiones.values()) {
      if (!estado.equals(r.estado())) {
        continue;
      }
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("revisionId", r.id().toString());
      m.put("asunto", r.asunto());
      m.put("estado", r.estado());
      m.put("documentoId", r.documentoId() == null ? null : r.documentoId().toString());
      m.put("pagina", r.pagina());
      m.put("fila", r.fila());
      m.put("motivo", r.motivo());
      m.put("confianza", null);
      elementos.add(m);
    }
    return Map.of("elementos", elementos, "total", elementos.size());
  }

  @PostMapping("/revisiones/{revisionId}/decision")
  public ResponseEntity<?> decidir(
      @PathVariable UUID revisionId, @RequestBody Map<String, Object> body) {
    var r = store.revisiones.get(revisionId);
    if (r == null) {
      return ProblemSupport.of(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Revisión no encontrada", "");
    }
    String decision = String.valueOf(body.get("decision"));
    if ("RECHAZAR".equals(decision)) {
      store.revisiones.put(
          revisionId,
          new InMemoryDomainStore.Revision(
              r.id(), r.asunto(), "RECHAZADA", r.documentoId(), r.pagina(), r.fila(), r.motivo(), r.movimientoId()));
      auditoria.insertar("revision", revisionId, null, "RECHAZAR", "Revisión rechazada", null);
      return ResponseEntity.ok(Map.of("revisionId", revisionId.toString(), "estado", "RECHAZADA"));
    }

    // CONFIRMAR — create or link movement from CSV row embedded in motivo payload
    UUID movimientoId = r.movimientoId();
    int evidencias = 1;
    String monto = "0.00";
    if (movimientoId != null && store.movimientos.containsKey(movimientoId)) {
      var mov = store.movimientos.get(movimientoId);
      var existente = store.movimientoPorHuella(mov.cuentaId(), mov.huella());
      if (existente.isPresent() && !existente.get().id().equals(movimientoId)) {
        // exact duplicate → add evidence only
        var can = existente.get();
        store.movimientos.put(
            can.id(),
            new InMemoryDomainStore.Movimiento(
                can.id(),
                can.cuentaId(),
                can.fechaValor(),
                can.direccion(),
                can.monto(),
                can.moneda(),
                can.descripcion(),
                can.referencia(),
                can.huella(),
                can.clasificacion(),
                can.estado(),
                can.version(),
                can.duplicadoDe(),
                can.evidencias() + 1));
        store.movimientos.put(
            movimientoId,
            new InMemoryDomainStore.Movimiento(
                mov.id(),
                mov.cuentaId(),
                mov.fechaValor(),
                mov.direccion(),
                mov.monto(),
                mov.moneda(),
                mov.descripcion(),
                mov.referencia(),
                mov.huella(),
                mov.clasificacion(),
                "DUPLICADO",
                mov.version(),
                can.id(),
                0));
        evidencias = can.evidencias() + 1;
        movimientoId = can.id();
        monto = can.monto().toPlainString();
        auditoria.insertar(
            "movimiento", movimientoId, null, "ENLAZAR_EVIDENCIA", "Evidencia enlazada", null);
      } else {
        String clasificacion =
            body.get("clasificacion") == null
                ? mov.clasificacion()
                : body.get("clasificacion").toString();
        store.movimientos.put(
            movimientoId,
            new InMemoryDomainStore.Movimiento(
                mov.id(),
                mov.cuentaId(),
                mov.fechaValor(),
                mov.direccion(),
                mov.monto(),
                mov.moneda(),
                mov.descripcion(),
                mov.referencia(),
                mov.huella(),
                clasificacion,
                "CONFIRMADO",
                1,
                null,
                1));
        monto = mov.monto().toPlainString();
        auditoria.insertar("movimiento", movimientoId, null, "CONFIRMAR", "Movimiento confirmado", null);
      }
    }

    store.revisiones.put(
        revisionId,
        new InMemoryDomainStore.Revision(
            r.id(), r.asunto(), "CONFIRMADA", r.documentoId(), r.pagina(), r.fila(), r.motivo(), movimientoId));

    Map<String, Object> resp = new LinkedHashMap<>();
    resp.put("revisionId", revisionId.toString());
    resp.put("estado", "CONFIRMADA");
    resp.put("movimientoId", movimientoId == null ? null : movimientoId.toString());
    resp.put("version", 1);
    resp.put("evidencias", evidencias);
    resp.put("montoContadoUnaVez", monto);
    return ResponseEntity.ok(resp);
  }

  private void abrirRevisionesDesdeCsv(UUID documentoId, List<ExtractorCsv.CampoExtraido> campos) {
    // Group by row
    Map<Integer, List<ExtractorCsv.CampoExtraido>> porFila = new LinkedHashMap<>();
    for (var c : campos) {
      porFila.computeIfAbsent(c.fila(), k -> new ArrayList<>()).add(c);
    }
    UUID cuentaDefault =
        store.cuentas.values().stream()
            .filter(c -> c.moneda() == CurrencyCode.COP)
            .map(InMemoryDomainStore.Cuenta::id)
            .findFirst()
            .orElseGet(
                () -> {
                  UUID id = UUID.randomUUID();
                  store.cuentas.put(
                      id,
                      new InMemoryDomainStore.Cuenta(
                          id, "Cuenta primaria", "AHORROS", CurrencyCode.COP, "****1001"));
                  return id;
                });

    DeduplicadorMovimientos dedup = new DeduplicadorMovimientos();
    for (var m : store.movimientos.values()) {
      if ("CONFIRMADO".equals(m.estado())) {
        dedup.registrarConfirmado(m.huella(), m.id());
      }
    }

    for (var entry : porFila.entrySet()) {
      Map<String, ExtractorCsv.CampoExtraido> byKey = new LinkedHashMap<>();
      for (var c : entry.getValue()) {
        byKey.put(c.clave(), c);
      }
      if (!byKey.containsKey("monto") || !byKey.containsKey("fecha")) {
        continue;
      }
      LocalDate fecha = byKey.get("fecha").valorFecha();
      BigDecimal monto = byKey.get("monto").valorDecimal();
      String dir = byKey.containsKey("direccion") ? byKey.get("direccion").valorTexto() : "DEBITO";
      String desc = byKey.containsKey("descripcion") ? byKey.get("descripcion").valorTexto() : "";
      String ref = byKey.containsKey("referencia") ? byKey.get("referencia").valorTexto() : null;
      CurrencyCode moneda =
          byKey.containsKey("moneda")
              ? CurrencyCode.parse(byKey.get("moneda").valorTexto())
              : CurrencyCode.COP;

      String huella =
          HuellaMovimiento.calcular(cuentaDefault, fecha, monto, dir, ref, desc);
      var decision =
          dedup.evaluar(
              new DeduplicadorMovimientos.Candidato(
                  cuentaDefault, fecha, monto, dir, ref, desc, 1, entry.getKey()));

      UUID movId = UUID.randomUUID();
      String asunto = "MOVIMIENTO";
      String motivo = "Confirmar movimiento fila " + entry.getKey();
      if (decision.tipo() == DeduplicadorMovimientos.ResultadoTipo.DUPLICADO_EXACTO) {
        asunto = "POSIBLE_DUPLICADO";
        motivo = "Misma huella que movimiento confirmado; al confirmar se enlaza evidencia";
      } else if (decision.tipo() == DeduplicadorMovimientos.ResultadoTipo.POSIBLE_DUPLICADO) {
        asunto = "POSIBLE_DUPLICADO";
        motivo = "Misma fecha y monto, sin referencia y con descripción distinta";
      }

      store.movimientos.put(
          movId,
          new InMemoryDomainStore.Movimiento(
              movId,
              cuentaDefault,
              fecha,
              dir,
              Dinero.cuantizarMonto(monto),
              moneda,
              desc,
              ref,
              huella,
              "SIN_CLASIFICAR",
              "PENDIENTE_REVISION",
              1,
              decision.canonicoId(),
              0));

      UUID revId = UUID.randomUUID();
      store.revisiones.put(
          revId,
          new InMemoryDomainStore.Revision(
              revId, asunto, "ABIERTA", documentoId, 1, entry.getKey(), motivo, movId));
    }
  }

  private static String enmascararNombre(String name) {
    if (name == null) {
      return "archivo";
    }
    return name.replaceAll("\\d{5,}", "****");
  }
}
