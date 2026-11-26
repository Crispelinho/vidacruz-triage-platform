-- Change 3 (clinical-assistant-rag): evidence storage for retrieval-augmented answers.
--
-- Flyway owns this schema (ADR-04); Spring AI table auto-creation is disabled.
-- Numbered V3 for Change 3. This service has no V1/V2 yet; Flyway does not require contiguous versions.
--
-- PROVISIONAL: vector(768) assumes a 768-dimension embedding model (e.g. nomic-embed-text). The model is not chosen
-- yet (ADR-05, G-01). Changing the dimension later needs a new migration and a full re-ingestion. Keep it in sync with
-- clinical.vector-store.dimensions in application.yaml.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE evidence_fragments (
    id           UUID PRIMARY KEY,
    document_id  UUID         NOT NULL,
    chunk_index  INT          NOT NULL CHECK (chunk_index >= 0),
    protocol     VARCHAR(100),
    section      VARCHAR(200),
    content      TEXT         NOT NULL,
    embedding    vector(768)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- One chunk position per document: lets ingestion detect duplicates when it is re-run.
    CONSTRAINT uq_evidence_fragments_document_chunk UNIQUE (document_id, chunk_index)
);

-- Cosine distance, matching the <=> operator used by VectorRepository.
CREATE INDEX idx_evidence_fragments_embedding
    ON evidence_fragments USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_evidence_fragments_protocol ON evidence_fragments (protocol);
