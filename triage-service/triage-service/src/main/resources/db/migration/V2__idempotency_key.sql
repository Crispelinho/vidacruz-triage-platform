CREATE TABLE idempotency_key (
    key VARCHAR(255) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    paciente_id UUID REFERENCES paciente (id),
    response_status SMALLINT,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_key_expires_at ON idempotency_key (expires_at);
