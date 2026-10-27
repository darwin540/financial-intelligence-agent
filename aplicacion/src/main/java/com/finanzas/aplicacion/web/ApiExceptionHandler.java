package com.finanzas.aplicacion.web;

import com.finanzas.finanzas.CurrencyMismatchException;
import com.finanzas.finanzas.MotivoBloqueo;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(CurrencyMismatchException.class)
  public ResponseEntity<Map<String, Object>> moneda(CurrencyMismatchException ex) {
    return ProblemSupport.of(
        HttpStatus.UNPROCESSABLE_ENTITY,
        MotivoBloqueo.MONEDA_MIXTA_SIN_CONVERSION,
        "Moneda mixta sin conversión",
        ex.getMessage());
  }

  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<Map<String, Object>> unsupported(UnsupportedOperationException ex) {
    if (MotivoBloqueo.LLM_NO_DISPONIBLE_EN_ESTA_FASE.equals(ex.getMessage())) {
      return ProblemSupport.of(
          HttpStatus.UNPROCESSABLE_ENTITY,
          MotivoBloqueo.LLM_NO_DISPONIBLE_EN_ESTA_FASE,
          "IA no disponible",
          ex.getMessage());
    }
    return ProblemSupport.of(
        HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", "Solicitud inválida", ex.getMessage());
  }
}
