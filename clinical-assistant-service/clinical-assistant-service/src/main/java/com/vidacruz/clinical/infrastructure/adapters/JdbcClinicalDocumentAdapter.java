package com.vidacruz.clinical.infrastructure.adapters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.vidacruz.clinical.domain.models.IngestedDocument;
import com.vidacruz.clinical.domain.ports.ClinicalDocumentRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Blocking JDBC access to {@code clinical_documents} (Flyway {@code V5}), isolated on the same bounded scheduler as
 * the pgvector adapter (ADR-04, NFR-01): one blocking-I/O policy for the whole corpus. Document content is never
 * logged.
 */
public class JdbcClinicalDocumentAdapter implements ClinicalDocumentRepository {

	private final JdbcClient jdbc;

	private final Scheduler jdbcScheduler;

	public JdbcClinicalDocumentAdapter(JdbcClient jdbc, Scheduler jdbcScheduler) {
		this.jdbc = jdbc;
		this.jdbcScheduler = jdbcScheduler;
	}

	@Override
	public Mono<IngestedDocument> findById(UUID id) {
		return Mono.fromCallable(() -> jdbc.sql("SELECT id, title, chunks_count FROM clinical_documents WHERE id = :id")
				.param("id", id)
				.query((rs, rowNum) -> new IngestedDocument(rs.getObject("id", UUID.class), rs.getString("title"),
						rs.getInt("chunks_count")))
				.optional()
				.orElse(null)).subscribeOn(jdbcScheduler);
	}

	@Override
	public Mono<IngestedDocument> saveIfAbsent(IngestedDocument document, String content) {
		return Mono.fromCallable(() -> {
			jdbc.sql("""
					INSERT INTO clinical_documents (id, title, content, chunks_count)
					VALUES (:id, :title, :content, :chunksCount)
					ON CONFLICT (id) DO NOTHING
					""")
					.param("id", document.documentId())
					.param("title", document.title())
					.param("content", content)
					.param("chunksCount", document.chunksCount())
					.update();
			return document;
		}).subscribeOn(jdbcScheduler);
	}

	@Override
	public Mono<Map<UUID, String>> findTitles(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Mono.just(Map.of());
		}
		return Mono.fromCallable(() -> {
			Map<UUID, String> titles = new HashMap<>();
			jdbc.sql("SELECT id, title FROM clinical_documents WHERE id IN (:ids)")
					.param("ids", ids)
					.query((rs, rowNum) -> {
						titles.put(rs.getObject("id", UUID.class), rs.getString("title"));
						return null;
					})
					.list();
			return titles;
		}).subscribeOn(jdbcScheduler);
	}

}
