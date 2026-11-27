-- RF-04A: corpus ingestion. Document registry for the protocol corpus.
--
-- Chunks and their embeddings are NOT duplicated here: they live in evidence_fragments (V3), the table RAGService
-- already searches. evidence_fragments.document_id points at clinical_documents.id. There is deliberately no foreign
-- key because the V4 seed fragments predate this table.
--
-- Numbered V5 (not V1): V3 and V4 already exist, and Flyway rejects a lower version that is pending on a database
-- where higher versions are applied.
--
-- id is deterministic (UUIDv3 of SHA-256(title, content)), so re-ingesting identical input hits the primary key.
-- Indexes: the embedding HNSW index (cosine) and the (document_id, chunk_index) unique index, whose leading column is
-- document_id, already exist on evidence_fragments.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE clinical_documents (
    id            UUID PRIMARY KEY,
    title         VARCHAR(300) NOT NULL,
    content       TEXT         NOT NULL,
    chunks_count  INT          NOT NULL CHECK (chunks_count > 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
