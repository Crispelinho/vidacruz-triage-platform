-- Status start is distinct from the last mutation: escalation flags an overdue patient without moving it out
-- of REGISTRADO, so it must not restart the time in state (RF-02/RN-03).
ALTER TABLE paciente ADD COLUMN estado_desde TIMESTAMPTZ;
UPDATE paciente SET estado_desde = ultimo_cambio_en;
ALTER TABLE paciente ALTER COLUMN estado_desde SET NOT NULL;
