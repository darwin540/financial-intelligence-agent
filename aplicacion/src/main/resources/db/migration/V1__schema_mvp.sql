-- Financial Intelligence Agent MVP schema (PostgreSQL 16+)
-- Synthetic data only in development; no real bank extracts.

CREATE TABLE usuario_local (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    nombre_visible TEXT NOT NULL,
    hash_secreto TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_usuario_vigente ON usuario_local (identidad_id) WHERE vigente;

CREATE TABLE configuracion (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    usuario_id UUID NOT NULL,
    minimo_seguridad_cop NUMERIC(19,2),
    minimo_seguridad_usd NUMERIC(19,2),
    zona_horaria TEXT NOT NULL DEFAULT 'America/Bogota',
    llm_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    llm_proveedor TEXT,
    algoritmos TEXT NOT NULL
);

CREATE UNIQUE INDEX uq_config_vigente ON configuracion (identidad_id) WHERE vigente;

CREATE TABLE documento_fuente (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    usuario_id UUID NOT NULL,
    tipo TEXT NOT NULL,
    nombre_original TEXT NOT NULL,
    sha256_plano CHAR(64) NOT NULL,
    sha256_cifrado CHAR(64) NOT NULL,
    ruta_relativa TEXT NOT NULL,
    key_id TEXT NOT NULL,
    mime TEXT NOT NULL,
    bytes BIGINT NOT NULL,
    periodo_inicio DATE,
    periodo_fin DATE,
    estado TEXT NOT NULL
);

CREATE UNIQUE INDEX uq_documento_sha ON documento_fuente (usuario_id, sha256_plano) WHERE vigente;

CREATE TABLE campo_extraido (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    documento_id UUID NOT NULL,
    pagina INT,
    fila INT,
    clave TEXT NOT NULL,
    valor_texto TEXT,
    valor_decimal NUMERIC(19,2),
    valor_fecha DATE,
    confianza NUMERIC(5,4),
    naturaleza TEXT NOT NULL,
    estado TEXT NOT NULL,
    referencia_cruda TEXT
);

CREATE TABLE elemento_revision (
    identidad_id UUID PRIMARY KEY,
    estado TEXT NOT NULL,
    asunto TEXT NOT NULL,
    documento_id UUID,
    pagina INT,
    fila INT,
    campo_id UUID,
    movimiento_id UUID,
    motivo TEXT NOT NULL,
    decidido_en TIMESTAMPTZ,
    decision_nota TEXT
);

CREATE TABLE cuenta (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    usuario_id UUID NOT NULL,
    alias TEXT NOT NULL,
    tipo TEXT NOT NULL,
    moneda CHAR(3) NOT NULL CHECK (moneda IN ('COP', 'USD')),
    numero_enmascarado TEXT,
    numero_cifrado BYTEA,
    institucion_alias TEXT,
    activa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX uq_cuenta_vigente ON cuenta (identidad_id) WHERE vigente;

CREATE TABLE saldo_registrado (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    cuenta_id UUID NOT NULL,
    fecha_corte DATE NOT NULL,
    monto NUMERIC(19,2) NOT NULL,
    moneda CHAR(3) NOT NULL,
    naturaleza TEXT NOT NULL,
    documento_id UUID,
    pagina INT,
    fila INT,
    estado TEXT NOT NULL
);

CREATE TABLE movimiento (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    cuenta_id UUID NOT NULL,
    fecha_valor DATE NOT NULL,
    fecha_contable DATE,
    direccion TEXT NOT NULL,
    monto NUMERIC(19,2) NOT NULL CHECK (monto > 0),
    moneda CHAR(3) NOT NULL,
    descripcion_original TEXT,
    descripcion_normalizada TEXT,
    referencia_cifrada BYTEA,
    referencia_enmascarada TEXT,
    huella TEXT NOT NULL,
    clasificacion TEXT NOT NULL,
    estado TEXT NOT NULL,
    duplicado_de_id UUID,
    grupo_transferencia_id UUID,
    obligacion_id UUID,
    naturaleza TEXT NOT NULL
);

CREATE UNIQUE INDEX uq_movimiento_huella_confirmado
    ON movimiento (cuenta_id, huella)
    WHERE vigente AND estado = 'CONFIRMADO';

CREATE TABLE movimiento_evidencia (
    id UUID PRIMARY KEY,
    movimiento_id UUID NOT NULL,
    documento_id UUID NOT NULL,
    pagina INT,
    fila INT,
    confianza NUMERIC(5,4),
    es_principal BOOLEAN NOT NULL
);

CREATE TABLE grupo_transferencia (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    monto NUMERIC(19,2) NOT NULL,
    fecha_valor DATE NOT NULL,
    moneda CHAR(3) NOT NULL,
    cuenta_origen_id UUID NOT NULL,
    cuenta_destino_id UUID NOT NULL,
    estado TEXT NOT NULL
);

-- Point-in-time FX only: rate belongs to this row, not to configuration.
CREATE TABLE conversion_moneda (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    fecha_valor DATE NOT NULL,
    monto_origen NUMERIC(19,2) NOT NULL,
    moneda_origen CHAR(3) NOT NULL CHECK (moneda_origen IN ('COP', 'USD')),
    monto_destino NUMERIC(19,2) NOT NULL,
    moneda_destino CHAR(3) NOT NULL CHECK (moneda_destino IN ('COP', 'USD')),
    tipo_cambio_operacion NUMERIC(18,8),
    cuenta_origen_id UUID NOT NULL,
    cuenta_destino_id UUID NOT NULL,
    naturaleza TEXT NOT NULL,
    estado TEXT NOT NULL,
    nota TEXT,
    CONSTRAINT chk_conversion_monedas_distintas CHECK (moneda_origen <> moneda_destino)
);

CREATE TABLE ingreso (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    concepto TEXT NOT NULL,
    clase TEXT NOT NULL,
    recurrencia TEXT NOT NULL,
    moneda CHAR(3) NOT NULL,
    bruto NUMERIC(19,2),
    neto NUMERIC(19,2),
    descuentos NUMERIC(19,2),
    dia_esperado INT,
    documento_id UUID,
    pagina INT,
    fila INT,
    naturaleza TEXT NOT NULL,
    estado TEXT NOT NULL
);

CREATE TABLE ocurrencia_ingreso (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    ingreso_id UUID,
    fecha DATE NOT NULL,
    escenario TEXT NOT NULL,
    monto_neto NUMERIC(19,2) NOT NULL,
    moneda CHAR(3) NOT NULL,
    naturaleza TEXT NOT NULL,
    estado TEXT NOT NULL
);

CREATE TABLE programacion_esencial (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    concepto TEXT NOT NULL,
    monto NUMERIC(19,2) NOT NULL,
    moneda CHAR(3) NOT NULL,
    fecha DATE,
    dia_mes INT,
    prioridad INT NOT NULL,
    naturaleza TEXT NOT NULL,
    estado TEXT NOT NULL
);

CREATE TABLE reserva (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    concepto TEXT NOT NULL,
    monto NUMERIC(19,2) NOT NULL,
    moneda CHAR(3) NOT NULL,
    constituida_desde DATE NOT NULL,
    fecha_uso DATE,
    consumida BOOLEAN NOT NULL DEFAULT FALSE,
    naturaleza TEXT NOT NULL
);

CREATE TABLE obligacion (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    alias TEXT NOT NULL,
    tipo TEXT NOT NULL,
    estado TEXT NOT NULL,
    moneda CHAR(3) NOT NULL CHECK (moneda IN ('COP', 'USD')),
    saldo_total NUMERIC(19,2),
    pago_minimo NUMERIC(19,2),
    cuota_contractual NUMERIC(19,2),
    tasa_valor NUMERIC(18,8),
    tasa_expresion TEXT,
    tasa_periodo TEXT,
    tasa_base TEXT,
    dia_vencimiento INT,
    fecha_vencimiento_proxima DATE,
    efecto_abono TEXT NOT NULL DEFAULT 'DESCONOCIDO',
    regla_recalculo_cuota TEXT,
    cupo_total NUMERIC(19,2),
    cupo_disponible NUMERIC(19,2),
    cuenta_pago_id UUID,
    documento_id UUID,
    pagina INT,
    fila INT,
    naturaleza TEXT NOT NULL
);

CREATE UNIQUE INDEX uq_obligacion_vigente ON obligacion (identidad_id) WHERE vigente;

CREATE TABLE escenario (
    version_id UUID PRIMARY KEY,
    identidad_id UUID NOT NULL,
    numero_version INT NOT NULL,
    vigente BOOLEAN NOT NULL,
    reemplaza_version_id UUID,
    registrado_en TIMESTAMPTZ NOT NULL,
    nombre TEXT NOT NULL,
    tipo TEXT NOT NULL,
    horizonte_inicio DATE,
    horizonte_fin DATE,
    moneda CHAR(3),
    objetivo TEXT NOT NULL DEFAULT 'MENOR_INTERES_TOTAL',
    presupuesto_deuda NUMERIC(19,2),
    presupuesto_es_supuesto BOOLEAN NOT NULL DEFAULT FALSE,
    supuestos_hash TEXT
);

CREATE TABLE corrida (
    id UUID PRIMARY KEY,
    escenario_id UUID,
    supuestos_hash TEXT NOT NULL,
    algoritmo TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL,
    moneda CHAR(3),
    tipo TEXT NOT NULL,
    estrategia TEXT,
    estado TEXT NOT NULL,
    definitiva BOOLEAN NOT NULL,
    es_estimacion BOOLEAN NOT NULL,
    motivos_bloqueo TEXT,
    interes_total NUMERIC(19,2),
    tasa_mensual_efectiva_usada NUMERIC(18,8),
    fecha_liquidacion_portafolio DATE,
    es_recomendada BOOLEAN NOT NULL DEFAULT FALSE,
    justificacion TEXT,
    payload_json TEXT
);

CREATE TABLE periodo_flujo (
    id UUID PRIMARY KEY,
    corrida_id UUID NOT NULL REFERENCES corrida (id),
    fecha DATE NOT NULL,
    moneda CHAR(3) NOT NULL,
    saldo_inicial NUMERIC(19,2) NOT NULL,
    entradas NUMERIC(19,2) NOT NULL,
    salidas NUMERIC(19,2) NOT NULL,
    saldo_final NUMERIC(19,2) NOT NULL,
    piso NUMERIC(19,2),
    reservas NUMERIC(19,2) NOT NULL,
    libre NUMERIC(19,2),
    viable BOOLEAN NOT NULL,
    faltante NUMERIC(19,2) NOT NULL,
    naturaleza TEXT NOT NULL
);

CREATE TABLE linea_estrategia (
    id UUID PRIMARY KEY,
    corrida_id UUID NOT NULL REFERENCES corrida (id),
    obligacion_id UUID NOT NULL,
    numero_periodo INT NOT NULL,
    fecha_pago DATE NOT NULL,
    moneda CHAR(3) NOT NULL,
    saldo_inicial NUMERIC(19,2) NOT NULL,
    interes NUMERIC(19,2) NOT NULL,
    pago NUMERIC(19,2) NOT NULL,
    saldo_final NUMERIC(19,2) NOT NULL,
    cuota_contractual_vigente NUMERIC(19,2),
    liquidada_en_el_periodo BOOLEAN NOT NULL,
    presupuesto_no_usado NUMERIC(19,2) NOT NULL
);

CREATE TABLE reporte (
    id UUID PRIMARY KEY,
    corrida_ids TEXT NOT NULL,
    generado_en TIMESTAMPTZ NOT NULL,
    secciones_json TEXT NOT NULL,
    es_estimacion BOOLEAN NOT NULL
);

CREATE TABLE evento_auditoria (
    id UUID PRIMARY KEY,
    entidad TEXT NOT NULL,
    identidad_id UUID,
    version_id UUID,
    accion TEXT NOT NULL,
    ocurrido_en TIMESTAMPTZ NOT NULL,
    usuario_id UUID,
    resumen TEXT NOT NULL,
    motivo TEXT
);

-- Application DB role should not UPDATE/DELETE audit and immutable run tables.
-- Enforced at deployment; local MVP uses the app owner for simplicity.
