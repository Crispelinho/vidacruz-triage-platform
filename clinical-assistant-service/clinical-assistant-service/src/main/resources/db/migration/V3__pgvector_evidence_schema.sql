-- Change 3 (clinical-assistant-rag): evidence storage for retrieval-augmented answers.
--
-- Flyway owns this schema (ADR-04); Spring AI table auto-creation is disabled.
-- Numbered V3 for Change 3. This service has no V1/V2 yet; Flyway does not require contiguous versions.
--
-- The schema dimension is definitively vector(768) per ADR-05 (2026-11-27). The real embedding model is still pending
-- (nomic-embed-text or text-embedding-004, G-01/TST-RAG); FixtureEmbeddingAdapter remains active until then. Changing
-- the dimension later needs a new migration and a full re-ingestion. Keep it in sync with
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
