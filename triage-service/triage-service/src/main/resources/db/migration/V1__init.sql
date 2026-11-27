CREATE TABLE paciente (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    edad INT NOT NULL,
    frecuencia_cardiaca INT NOT NULL,
    presion_sistolica INT NOT NULL,
    presion_diastolica INT NOT NULL,
    spo2 INT NOT NULL,
    temperatura DECIMAL(4,1) NOT NULL,
    dolor INT NOT NULL,
    gcs INT NOT NULL,
    queja_principal TEXT NOT NULL,
    nivel VARCHAR(30) NOT NULL,
    sla_minutos INT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    escalado BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_registro TIMESTAMPTZ NOT NULL,
    ultimo_cambio_en TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_paciente_estado_escalado ON paciente (estado, escalado);

CREATE TABLE auditoria_clinica (
    id UUID PRIMARY KEY,
    paciente_id UUID NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    tipo_evento VARCHAR(40) NOT NULL
);

CREATE INDEX idx_auditoria_paciente_creado_en ON auditoria_clinica (paciente_id, creado_en);

CREATE TABLE outbox_evento (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    paciente_id UUID NOT NULL,
    paciente_sequence INT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    publicado_en TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    lease_owner VARCHAR(100),
    intentos INT NOT NULL DEFAULT 0,
    primer_fallo_en TIMESTAMPTZ,
    ultimo_fallo_en TIMESTAMPTZ,
    ultimo_error TEXT,
    requiere_revision BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_evento_creado_en ON outbox_evento (created_at);
