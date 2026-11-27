package com.vidacruz.clinical.infrastructure.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.ProtocolSection;
import com.vidacruz.clinical.domain.models.ScoredEvidenceFragment;

/**
 * Blocking JDBC access to {@code evidence_fragments} (schema owned by Flyway, {@code V3}). Never call it from an
 * event-loop thread: go through {@code PgvectorVectorStoreAdapter}, which isolates it on a bounded scheduler.
 *
 * <p>Distance is cosine ({@code <=>}), matching the {@code vector_cosine_ops} index in the migration.
 */
public class VectorRepository {

	private static final String COLUMNS = "id, document_id, chunk_index, protocol, section, content, "
			+ "CAST(embedding AS text) AS embedding_text";

	private static final RowMapper<EvidenceFragment> ROW_MAPPER = VectorRepository::mapRow;

	private final JdbcClient jdbc;

	private final int dimensions;

	public VectorRepository(JdbcClient jdbc, int dimensions) {
		this.jdbc = jdbc;
		this.dimensions = dimensions;
	}

	/** Inserts or updates by id. A different id for an existing (document, chunk) pair violates the unique key. */
	public EvidenceFragment upsert(EvidenceFragment fragment) {
		float[] embedding = requireDimensions(fragment.embedding());
		jdbc.sql("""
				INSERT INTO evidence_fragments
				    (id, document_id, chunk_index, protocol, section, content, embedding)
				VALUES
				    (:id, :documentId, :chunkIndex, :protocol, :section, :content, CAST(:embedding AS vector))
				ON CONFLICT (id) DO UPDATE SET
				    document_id = EXCLUDED.document_id,
				    chunk_index = EXCLUDED.chunk_index,
				    protocol = EXCLUDED.protocol,
				    section = EXCLUDED.section,
				    content = EXCLUDED.content,
				    embedding = EXCLUDED.embedding
				""")
				.param("id", fragment.id())
				.param("documentId", fragment.documentId())
				.param("chunkIndex", fragment.chunkIndex())
				.param("protocol", fragment.protocol(), Types.VARCHAR)
				.param("section", fragment.section(), Types.VARCHAR)
				.param("content", fragment.content())
				.param("embedding", toVectorLiteral(embedding))
				.update();
		return fragment;
	}

	public Optional<EvidenceFragment> findById(UUID id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM evidence_fragments WHERE id = :id")
				.param("id", id)
				.query(ROW_MAPPER)
				.optional();
	}

	public boolean deleteById(UUID id) {
		return jdbc.sql("DELETE FROM evidence_fragments WHERE id = :id").param("id", id).update() > 0;
	}

	/** Nearest fragments by cosine distance, closest first. */
	public List<EvidenceFragment> searchSimilar(float[] queryEmbedding, int topK) {
		return searchSimilarWithScores(queryEmbedding, topK).stream().map(ScoredEvidenceFragment::fragment).toList();
	}

	/**
	 * Nearest fragments with cosine similarity score in [0, 1], closest first.
	 * Score is {@code 1 - cosine_distance}.
	 */
	public List<ScoredEvidenceFragment> searchSimilarWithScores(float[] queryEmbedding, int topK) {
		if (topK < 1) {
			throw new IllegalArgumentException("topK must be positive");
		}
		float[] query = requireDimensions(queryEmbedding);
		return jdbc.sql("SELECT " + COLUMNS
				+ ", GREATEST(0, LEAST(1, 1 - (embedding <=> CAST(:query AS vector)))) AS score "
				+ "FROM evidence_fragments ORDER BY embedding <=> CAST(:query AS vector) LIMIT :limit")
				.param("query", toVectorLiteral(query))
				.param("limit", topK)
				.query(VectorRepository::mapScoredRow)
				.list();
	}

	/** Distinct citation keys present in the corpus; used to initialise the in-memory index (G-03). */
	public List<ProtocolSection> protocolSections() {
		return jdbc.sql("SELECT DISTINCT protocol, section FROM evidence_fragments "
				+ "WHERE protocol IS NOT NULL AND section IS NOT NULL ORDER BY protocol, section")
				.query((rs, rowNum) -> ProtocolSection.of(rs.getString("protocol"), rs.getString("section")))
				.list();
	}

	private float[] requireDimensions(float[] embedding) {
		if (embedding.length != dimensions) {
			throw new IllegalArgumentException(
					"embedding has " + embedding.length + " dimensions, expected " + dimensions);
		}
		return embedding;
	}

	static String toVectorLiteral(float[] embedding) {
		StringBuilder literal = new StringBuilder(embedding.length * 8).append('[');
		for (int i = 0; i < embedding.length; i++) {
			if (i > 0) {
				literal.append(',');
			}
			literal.append(embedding[i]);
		}
		return literal.append(']').toString();
	}

	static float[] parseVector(String text) {
		String body = text.substring(1, text.length() - 1);
		if (body.isEmpty()) {
			return new float[0];
		}
		String[] parts = body.split(",");
		float[] vector = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			vector[i] = Float.parseFloat(parts[i]);
		}
		return vector;
	}

	private static EvidenceFragment mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new EvidenceFragment(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
				rs.getInt("chunk_index"), rs.getString("protocol"), rs.getString("section"),
				rs.getString("content"), parseVector(rs.getString("embedding_text")));
	}

	private static ScoredEvidenceFragment mapScoredRow(ResultSet rs, int rowNum) throws SQLException {
		return new ScoredEvidenceFragment(mapRow(rs, rowNum), rs.getDouble("score"));
	}

}
