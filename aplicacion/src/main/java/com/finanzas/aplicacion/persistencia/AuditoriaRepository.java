package com.finanzas.aplicacion.persistencia;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditoriaRepository {

  private final JdbcTemplate jdbc;

  public AuditoriaRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertar(
      String entidad,
      UUID identidadId,
      UUID versionId,
      String accion,
      String resumen,
      String motivo) {
    jdbc.update(
        """
        INSERT INTO evento_auditoria
          (id, entidad, identidad_id, version_id, accion, ocurrido_en, usuario_id, resumen, motivo)
        VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)
        """,
        UUID.randomUUID(),
        entidad,
        identidadId,
        versionId,
        accion,
        OffsetDateTime.now(),
        resumen,
        motivo);
  }
}
