CREATE INDEX idx_auditoria_tipo_evento_creado_en ON auditoria_clinica (tipo_evento, creado_en);
CREATE INDEX idx_paciente_fecha_registro ON paciente (fecha_registro);
