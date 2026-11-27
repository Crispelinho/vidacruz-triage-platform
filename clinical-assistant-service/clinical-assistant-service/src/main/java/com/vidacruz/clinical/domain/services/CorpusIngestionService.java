package com.vidacruz.clinical.domain.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import com.vidacruz.clinical.domain.exceptions.InvalidCorpusRequestException;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.IngestedDocument;
import com.vidacruz.clinical.domain.ports.ClinicalDocumentRepository;
import com.vidacruz.clinical.domain.ports.EmbeddingProvider;
import com.vidacruz.clinical.domain.ports.VectorStore;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RF-04A: split a protocol document into overlapping chunks, embed each one and persist them.
 *
 * <p>Idempotent: the document id is derived from title and content, and so is every chunk id. Re-ingesting the same
 * input returns the existing document without calling the embedding model; a run interrupted midway is healed by the
 * next one because chunk writes are upserts and the document row is written last, as the commit marker.
 */
public class CorpusIngestionService {

	public static final int MAX_TITLE_LENGTH = 300;

	private final TextChunker chunker;

	private final EmbeddingProvider embeddingProvider;

	private final VectorStore vectorStore;

	private final ClinicalDocumentRepository documents;

	public CorpusIngestionService(TextChunker chunker, EmbeddingProvider embeddingProvider, VectorStore vectorStore,
			ClinicalDocumentRepository documents) {
		this.chunker = Objects.requireNonNull(chunker, "chunker");
		this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
		this.documents = Objects.requireNonNull(documents, "documents");
	}

	public Mono<IngestedDocument> ingestDocument(String title, String content) {
		return Mono.defer(() -> {
			validate(title, content);
			UUID documentId = documentId(title, content);
			return documents.findById(documentId).switchIfEmpty(Mono.defer(() -> ingest(documentId, title, content)));
		});
	}

	private Mono<IngestedDocument> ingest(UUID documentId, String title, String content) {
		List<String> chunks = chunker.split(content);
		return Flux.fromIterable(IntStream.range(0, chunks.size()).boxed().toList())
				// concatMap keeps embedding calls sequential: bounded load on the embedding provider.
				.concatMap(index -> embeddingProvider.embed(chunks.get(index))
						.flatMap(embedding -> vectorStore.save(new EvidenceFragment(chunkId(documentId, index),
								documentId, index, null, title, chunks.get(index), embedding))))
				.then(Mono.defer(() -> documents.saveIfAbsent(new IngestedDocument(documentId, title, chunks.size()),
						content)));
	}

	private static void validate(String title, String content) {
		if (title == null || title.isBlank()) {
			throw new InvalidCorpusRequestException("title must not be blank");
		}
		if (title.length() > MAX_TITLE_LENGTH) {
			throw new InvalidCorpusRequestException("title must be at most " + MAX_TITLE_LENGTH + " characters");
		}
		if (content == null || content.isBlank()) {
			throw new InvalidCorpusRequestException("content must not be blank");
		}
	}

	/** UUIDv3 over SHA-256(title, NUL, content): equal input, equal id. */
	static UUID documentId(String title, String content) {
		return UUID.nameUUIDFromBytes(sha256(title + "\u0000" + content));
	}

	static UUID chunkId(UUID documentId, int index) {
		return UUID.nameUUIDFromBytes((documentId + ":" + index).getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is guaranteed by the JDK", ex);
		}
	}

}
