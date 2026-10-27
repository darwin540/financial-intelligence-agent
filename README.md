# Financial Intelligence Agent

MVP (fase 1) de inteligencia financiera personal para **Darwin Mora**.  
UI y documentación de producto en **español**. Código y commits en inglés técnico.

**Este sistema no ejecuta pagos, transferencias ni créditos.** No se conecta a bancos. Solo calcula y recomienda a partir de datos que tú cargas.

## Qué incluye el MVP

- Carga de CSV (Excel/PDF texto parcial; fotos y PDF imagen se archivan **sin OCR**)
- Confirmación humana de montos, deduplicación, transferencias internas fuera de ingresos
- Ingresos (bruto/neto/descuentos), obligaciones COP/USD, flujo diario por moneda
- Cuatro estrategias (avalancha, bola de nieve, liberación de flujo, híbrida) con recomendación por **menor interés total**
- Amortización v1 con conversión **EA anual → mensual efectiva** `(1+EA)^(1/12)-1` (`BigDecimal`)
- FX COP↔USD **solo puntual** por operación (tipo indicado en esa conversión; **sin** tipo reutilizable en configuración)
- Módulo `ia` presente y **desactivado**
- Datos sintéticos de prueba; almacén de documentos cifrado fuera del repo

## Stack

- Java 21, Spring Boot 3.3, Maven multimódulo  
  `extraccion`, `conciliacion`, `finanzas`, `optimizacion`, `proyecciones`, `reportes`, `ia`, `aplicacion`
- PostgreSQL 16+, Flyway
- UI misma origen (static + fuente TypeScript/Angular en `frontend/`)
- REST `/api/v1`, montos JSON en cadena decimal, `BigDecimal` en servidor

## Requisitos locales

- JDK 21
- Maven 3.9+
- PostgreSQL 16+ escuchando en `127.0.0.1`

## Base de datos

```bash
sudo -u postgres createuser -s finanzas   # o rol equivalente
sudo -u postgres createdb -O finanzas finanzas
# contraseña de ejemplo solo para desarrollo local:
psql -U finanzas -c "ALTER USER finanzas PASSWORD 'finanzas_dev';"
```

Flyway aplica `aplicacion/src/main/resources/db/migration` al arrancar.

## Variables de entorno

| Variable | Descripción | Ejemplo local |
|---|---|---|
| `FINANZAS_DB_PASSWORD` | Clave PostgreSQL | `finanzas_dev` |
| `FINANZAS_SECRETO` | Secreto de la app local (no es clave bancaria) | `cambiar-en-local` |
| `FINANZAS_ALMACEN` | Directorio de documentos cifrados (fuera de Git) | `$HOME/.finanzas/documentos` |
| `FINANZAS_CLAVE_PATH` | Archivo de 32 bytes AES-256 (permisos 600) | `$HOME/.finanzas/secrets/document.key` |

Si la clave no existe, la app la genera al primer arranque. **No hay clave de cifrado por defecto en el código.**

La app escucha solo en `127.0.0.1:8080`.

## Arranque

```bash
export FINANZAS_DB_PASSWORD=finanzas_dev
export FINANZAS_SECRETO=cambiar-en-local
mvn -pl aplicacion -am spring-boot:run
```

Abrir `http://127.0.0.1:8080/`.  
Sesión: `POST /api/v1/sesion` con `{"nombre":"darwin","secreto":"<FINANZAS_SECRETO>"}`.  
Mutaciones: cabecera `X-Finanzas-Solicitud: 1`.

## Pruebas

```bash
mvn test
```

Oráculo numérico: casos CF-01, TR-01, DD-01, OB-01, EST-01, AB-01, IN-01, PR-01/02/03, CU-01, FX-01 (tolerancia cero en EST-01).

## Seguridad (resumen)

- Sin PIN/OTP/CVV/contraseña bancaria (rechazo `CAMPO_PROHIBIDO`)
- Documentos AES-256-GCM fuera del checkout
- `ia` desactivada; `llmHabilitado: true` → `422 LLM_NO_DISPONIBLE_EN_ESTA_FASE`
- Rutas `/api/v1/pagos`, `/transferencias`, `/creditos` → `404`
- No commits con extractos reales ni secretos (ver `.gitignore`)

## Preferencias de producto confirmadas

Ver Project store `preferences.md`. Destacado: **FX puntual** (ítem 2b) — cada conversión lleva el tipo de esa operación; no hay tipo reutilizable en configuración.

## Fase siguiente

Fase 2: simulaciones (`POST /api/v1/simulaciones`) sobre los mismos motores.
