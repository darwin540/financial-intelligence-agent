package com.finanzas.aplicacion.web;

import com.finanzas.aplicacion.seguridad.LocalSessionFilter;
import com.finanzas.aplicacion.seguridad.SessionService;
import com.finanzas.finanzas.MotivoBloqueo;
import com.finanzas.ia.AdaptadorIaDesactivado;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SaludSesionController {

  private final SessionService sessions;
  private final com.finanzas.aplicacion.persistencia.InMemoryDomainStore store;
  private final AdaptadorIaDesactivado ia;

  public SaludSesionController(
      SessionService sessions,
      com.finanzas.aplicacion.persistencia.InMemoryDomainStore store,
      AdaptadorIaDesactivado ia) {
    this.sessions = sessions;
    this.store = store;
    this.ia = ia;
  }

  @GetMapping("/salud")
  public Map<String, Object> salud() {
    return Map.of("estado", "OK", "versionApi", "v1");
  }

  @PostMapping("/sesion")
  public ResponseEntity<Void> sesion(@RequestBody Map<String, String> body, HttpServletResponse res) {
    try {
      String token = sessions.login(body.get("nombre"), body.get("secreto"));
      Cookie cookie = new Cookie(LocalSessionFilter.COOKIE, token);
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setAttribute("SameSite", "Strict");
      res.addCookie(cookie);
      return ResponseEntity.noContent().build();
    } catch (SessionService.AuthException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @DeleteMapping("/sesion")
  public ResponseEntity<Void> cerrar(jakarta.servlet.http.HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if (LocalSessionFilter.COOKIE.equals(c.getName())) {
          sessions.logout(c.getValue());
        }
      }
    }
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/configuracion")
  public Map<String, Object> getConfig() {
    var c = store.config;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("minimoSeguridadCop", c.minimoSeguridadCop() == null ? null : c.minimoSeguridadCop().toPlainString());
    m.put("minimoSeguridadUsd", c.minimoSeguridadUsd() == null ? null : c.minimoSeguridadUsd().toPlainString());
    m.put("minimoSeguridadCopDefinitivo", c.minimoSeguridadCop() != null);
    m.put("minimoSeguridadUsdDefinitivo", c.minimoSeguridadUsd() != null);
    m.put("zonaHoraria", "America/Bogota");
    m.put("monedas", List.of("COP", "USD"));
    m.put("llmHabilitado", false);
    m.put(
        "algoritmos",
        List.of("flujo.diario.v1", "priorizacion.v1", "amortizacion.mensual.v1"));
    m.put(
        "fx",
        Map.of(
            "automatico",
            false,
            "tipoReutilizableEnConfiguracion",
            false,
            "modo",
            "PUNTUAL_POR_OPERACION"));
    return m;
  }

  @PutMapping("/configuracion")
  public ResponseEntity<?> putConfig(@RequestBody Map<String, Object> body) {
    if (Boolean.TRUE.equals(body.get("llmHabilitado"))) {
      ia.asegurarDesactivado();
      return ProblemSupport.of(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          MotivoBloqueo.LLM_NO_DISPONIBLE_EN_ESTA_FASE,
          "IA no disponible en esta fase",
          "El módulo ia está presente y desactivado en el MVP.");
    }
    // Reject any attempt to store a reusable FX rate in configuration
    if (body.containsKey("tipoCambio")
        || body.containsKey("tipoCambioCopUsd")
        || body.containsKey("fxRate")) {
      return ProblemSupport.of(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "SOLICITUD_INVALIDA",
          "Tipo de cambio no configurable",
          "El FX COP↔USD es puntual por operación; no hay tipo reutilizable en configuración.");
    }
    java.math.BigDecimal cop =
        body.get("minimoSeguridadCop") == null
            ? null
            : new java.math.BigDecimal(body.get("minimoSeguridadCop").toString());
    java.math.BigDecimal usd =
        body.get("minimoSeguridadUsd") == null
            ? null
            : new java.math.BigDecimal(body.get("minimoSeguridadUsd").toString());
    store.updateConfig(cop, usd, false);
    return ResponseEntity.ok(getConfig());
  }
}
