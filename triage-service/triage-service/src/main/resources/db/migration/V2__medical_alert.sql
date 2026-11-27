-- RF-06: one alert per escalated patient, created inside the escalation transaction.
-- No patient name or complaint is stored: shared screens show only the clinical signal.
CREATE TABLE medical_alert (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL UNIQUE REFERENCES paciente (id),
    nivel VARCHAR(30) NOT NULL,
    sla_minutos INT NOT NULL,
    fecha_registro TIMESTAMPTZ NOT NULL,
    fecha_escalamiento TIMESTAMPTZ NOT NULL,
    minutos_espera NUMERIC(10, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    responsable VARCHAR(100),
    reconocida_en TIMESTAMPTZ,
    resuelta_en TIMESTAMPTZ,
    actualizada_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_medical_alert_estado_actualizada ON medical_alert (estado, actualizada_en);

-- NOTIFY runs after the row is committed, so SSE clients never see uncommitted alerts.
-- Only the id travels in the payload; the client reloads the row through the normal query.
CREATE OR REPLACE FUNCTION notify_medical_alert() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('medical_alert', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER medical_alert_changed
    AFTER INSERT OR UPDATE ON medical_alert
    FOR EACH ROW EXECUTE FUNCTION notify_medical_alert();
