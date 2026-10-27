package com.finanzas.aplicacion.seguridad;

import com.finanzas.aplicacion.config.FinanzasProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

  private final FinanzasProperties props;
  private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
  private final AtomicInteger failedAttempts = new AtomicInteger();
  private volatile Instant lockedUntil = Instant.EPOCH;
  private String secretHash;

  public SessionService(FinanzasProperties props) {
    this.props = props;
    this.secretHash = encoder.encode(props.getUsuarioLocalSecreto());
  }

  public synchronized String login(String nombre, String secreto) {
    if (Instant.now().isBefore(lockedUntil)) {
      throw new AuthException("SESION_AUSENTE");
    }
    if (!props.getUsuarioLocalNombre().equalsIgnoreCase(nombre)
        || !encoder.matches(secreto, secretHash)) {
      if (failedAttempts.incrementAndGet() >= 5) {
        lockedUntil = Instant.now().plusSeconds(60);
        failedAttempts.set(0);
      }
      throw new AuthException("SESION_AUSENTE");
    }
    failedAttempts.set(0);
    String token = UUID.randomUUID().toString();
    sessions.put(token, Instant.now().plusSeconds(3600 * 8));
    return token;
  }

  public void logout(String token) {
    if (token != null) {
      sessions.remove(token);
    }
  }

  public boolean isValid(String token) {
    if (token == null) {
      return false;
    }
    Instant exp = sessions.get(token);
    if (exp == null) {
      return false;
    }
    if (Instant.now().isAfter(exp)) {
      sessions.remove(token);
      return false;
    }
    return true;
  }

  public static final class AuthException extends RuntimeException {
    public AuthException(String code) {
      super(code);
    }
  }
}
