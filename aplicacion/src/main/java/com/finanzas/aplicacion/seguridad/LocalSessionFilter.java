package com.finanzas.aplicacion.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalSessionFilter extends OncePerRequestFilter {

  public static final String COOKIE = "FINANZAS_SESION";
  public static final String MUTATION_HEADER = "X-Finanzas-Solicitud";

  private final SessionService sessions;

  public LocalSessionFilter(SessionService sessions) {
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    boolean publicPath =
        path.equals("/api/v1/salud")
            || path.equals("/api/v1/sesion") && "POST".equalsIgnoreCase(request.getMethod());
    if (!publicPath && path.startsWith("/api/")) {
      String token = readCookie(request);
      if (!sessions.isValid(token)) {
        response.setStatus(401);
        response.setContentType("application/problem+json");
        response
            .getWriter()
            .write(
                "{\"type\":\"urn:finanzas:problema:sesion\",\"title\":\"Sin sesión\",\"status\":401,\"codigo\":\"SESION_AUSENTE\"}");
        return;
      }
      String method = request.getMethod();
      if (!"GET".equalsIgnoreCase(method)
          && !"HEAD".equalsIgnoreCase(method)
          && !"OPTIONS".equalsIgnoreCase(method)) {
        if (!"1".equals(request.getHeader(MUTATION_HEADER))) {
          response.setStatus(400);
          response.setContentType("application/problem+json");
          response
              .getWriter()
              .write(
                  "{\"type\":\"urn:finanzas:problema:solicitud\",\"title\":\"Cabecera de mutación ausente\",\"status\":400,\"codigo\":\"SOLICITUD_INVALIDA\"}");
          return;
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  private static String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie c : cookies) {
      if (COOKIE.equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }
}
