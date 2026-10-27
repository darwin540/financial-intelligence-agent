package com.finanzas.aplicacion.seguridad;

import com.finanzas.aplicacion.persistencia.AuditoriaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ProhibitedFieldFilter extends OncePerRequestFilter {

  private static final Set<String> PROHIBITED =
      Set.of("pin", "otp", "cvv", "clavebancaria", "passwordbancario");
  private static final Pattern FIELD =
      Pattern.compile("\"([^\"]+)\"\\s*:", Pattern.CASE_INSENSITIVE);

  private final AuditoriaRepository auditoria;

  public ProhibitedFieldFilter(AuditoriaRepository auditoria) {
    this.auditoria = auditoria;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String method = request.getMethod();
    String ct = request.getContentType();
    if (("POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method))
        && ct != null
        && ct.toLowerCase(Locale.ROOT).contains("application/json")) {
      byte[] body = request.getInputStream().readAllBytes();
      String text = new String(body, StandardCharsets.UTF_8);
      var m = FIELD.matcher(text);
      while (m.find()) {
        String key = m.group(1).toLowerCase(Locale.ROOT).replace("_", "");
        if (PROHIBITED.contains(key)) {
          auditoria.insertar(
              "solicitud",
              null,
              null,
              "RECHAZAR",
              "Campo prohibido rechazado (valor no auditado)",
              "CAMPO_PROHIBIDO");
          response.setStatus(400);
          response.setContentType("application/problem+json");
          response
              .getWriter()
              .write(
                  "{\"type\":\"urn:finanzas:problema:campo-prohibido\",\"title\":\"Campo prohibido\",\"status\":400,\"codigo\":\"CAMPO_PROHIBIDO\"}");
          return;
        }
      }
      filterChain.doFilter(new CachedBodyRequest(request, body), response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream bais = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return bais.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {}

        @Override
        public int read() {
          return bais.read();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }
}
