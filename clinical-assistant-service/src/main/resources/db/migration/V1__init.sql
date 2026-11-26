-- Load pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create DocumentoClinico table (protocol metadata)
CREATE TABLE documento_clinico (
    id UUID PRIMARY KEY,
    titulo VARCHAR(500) NOT NULL,
    contenido TEXT NOT NULL,
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documento_titulo ON documento_clinico(titulo);

-- Create FragmentoDocumental table (chunks + embeddings)
CREATE TABLE fragmento_documental (
    id UUID PRIMARY KEY,
    documento_id UUID NOT NULL REFERENCES documento_clinico(id),
    numero_fragmento INT NOT NULL,
    contenido TEXT NOT NULL,
    embedding vector(1536),
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- IVFFLAT index on embeddings for similarity search
CREATE INDEX idx_fragmento_embedding ON fragmento_documental USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_fragmento_documento ON fragmento_documental(documento_id);
