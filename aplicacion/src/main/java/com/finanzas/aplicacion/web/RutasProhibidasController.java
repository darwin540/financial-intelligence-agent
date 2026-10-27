package com.finanzas.aplicacion.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit 404 for money-moving and phase-2 simulation routes.
 */
@RestController
@RequestMapping("/api/v1")
public class RutasProhibidasController {

  @PostMapping({"/pagos", "/transferencias", "/creditos", "/avances"})
  public ResponseEntity<Map<String, Object>> dinero() {
    return ProblemSupport.of(
        HttpStatus.NOT_FOUND,
        "NO_ENCONTRADO",
        "Ruta no existe",
        "El sistema no ejecuta pagos, transferencias ni créditos.");
  }

  @PostMapping("/simulaciones")
  public ResponseEntity<Map<String, Object>> simulaciones() {
    return ProblemSupport.of(
        HttpStatus.NOT_FOUND,
        "NO_IMPLEMENTADO_EN_ESTA_FASE",
        "Simulaciones en fase 2",
        "El contrato existe; la implementación es fase 2.");
  }
}
