package com.finanzas.aplicacion.seguridad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encrypted document store outside the git checkout. Key has no factory default in code.
 */
public final class DocumentStore {

  private static final String KEY_ID = "k1";
  private final Path root;
  private final Path keyPath;
  private final SecretKey key;

  public DocumentStore(Path root, Path keyPath) {
    this.root = root;
    this.keyPath = keyPath;
    try {
      Files.createDirectories(root);
      try {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
        Files.setPosixFilePermissions(root, perms);
      } catch (UnsupportedOperationException ignored) {
        // non-POSIX FS
      }
      this.key = loadOrCreateKey(keyPath);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo inicializar el almacén de documentos", e);
    }
  }

  public record StoredDocument(
      String relativePath, String sha256Plain, String sha256Cipher, String keyId, long bytes) {}

  public StoredDocument store(byte[] plain, String originalName) {
    try {
      String shaPlain = sha256(plain);
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(plain);
      byte[] payload = new byte[nonce.length + encrypted.length];
      System.arraycopy(nonce, 0, payload, 0, nonce.length);
      System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);

      String relative = UUID.randomUUID() + ".bin";
      Path target = root.resolve(relative);
      Files.write(target, payload);
      return new StoredDocument(relative, shaPlain, sha256(payload), KEY_ID, plain.length);
    } catch (Exception e) {
      throw new IllegalStateException("Error cifrando documento", e);
    }
  }

  public byte[] load(String relativePath) {
    try {
      byte[] payload = Files.readAllBytes(root.resolve(relativePath));
      byte[] nonce = new byte[12];
      System.arraycopy(payload, 0, nonce, 0, 12);
      byte[] encrypted = new byte[payload.length - 12];
      System.arraycopy(payload, 12, encrypted, 0, encrypted.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      return cipher.doFinal(encrypted);
    } catch (Exception e) {
      throw new IllegalStateException("Error descifrando documento", e);
    }
  }

  public String keyId() {
    return KEY_ID;
  }

  private static SecretKey loadOrCreateKey(Path keyPath) throws IOException {
    Files.createDirectories(keyPath.getParent());
    if (!Files.exists(keyPath)) {
      byte[] raw = new byte[32];
      new SecureRandom().nextBytes(raw);
      Files.write(keyPath, raw);
      try {
        Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
      }
      return new SecretKeySpec(raw, "AES");
    }
    byte[] raw = Files.readAllBytes(keyPath);
    if (raw.length != 32) {
      throw new IllegalStateException("La clave de cifrado debe tener 32 bytes");
    }
    return new SecretKeySpec(raw, "AES");
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
