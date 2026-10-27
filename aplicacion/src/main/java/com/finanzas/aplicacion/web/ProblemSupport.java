package com.finanzas.aplicacion.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ProblemSupport {

  private ProblemSupport() {}

  public static ResponseEntity<Map<String, Object>> of(
      HttpStatus status, String codigo, String title, String detalle) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "urn:finanzas:problema:" + codigo.toLowerCase().replace('_', '-'));
    body.put("title", title);
    body.put("status", status.value());
    body.put("codigo", codigo);
    body.put("detalle", detalle);
    return ResponseEntity.status(status).header("Content-Type", "application/problem+json").body(body);
  }

  public static ResponseEntity<Map<String, Object>> proyeccionBloqueada(
      String codigo, String detalle, String obligacionId, List<String> campos) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "urn:finanzas:problema:datos-insuficientes");
    body.put("title", "Proyección definitiva bloqueada");
    body.put("status", 422);
    body.put("codigo", codigo);
    body.put("detalle", detalle);
    body.put("definitiva", false);
    body.put("esEstimacion", true);
    body.put("camposFaltantes", campos);
    body.put("motivosBloqueo", List.of(codigo));
    if (obligacionId != null) {
      body.put("obligacionId", obligacionId);
    }
    return ResponseEntity.unprocessableEntity()
        .header("Content-Type", "application/problem+json")
        .body(body);
  }
}
