package com.vidacruz.clinical.domain.models;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * A chunk of an official clinical protocol together with its embedding (one row of {@code evidence_fragments}).
 *
 * <p>The embedding is a {@code float[]}, so equality and the accessor are overridden to compare and expose it by
 * value rather than by reference.
 *
 * @param protocol optional protocol code, e.g. {@code PROTO-001}
 * @param section  optional protocol section, e.g. {@code Medidas iniciales}
 */
public record EvidenceFragment(UUID id, UUID documentId, int chunkIndex, String protocol, String section,
		String content, float[] embedding) {

	public EvidenceFragment {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(documentId, "documentId");
		if (chunkIndex < 0) {
			throw new IllegalArgumentException("chunkIndex must be >= 0");
		}
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("content must not be blank");
		}
		if (embedding == null || embedding.length == 0) {
			throw new IllegalArgumentException("embedding must not be empty");
		}
		for (float value : embedding) {
			if (!Float.isFinite(value)) {
				throw new IllegalArgumentException("embedding must contain only finite values");
			}
		}
		embedding = embedding.clone();
	}

	public static EvidenceFragment create(UUID documentId, int chunkIndex, String protocol, String section,
			String content, float[] embedding) {
		return new EvidenceFragment(UUID.randomUUID(), documentId, chunkIndex, protocol, section, content, embedding);
	}

	@Override
	public float[] embedding() {
		return embedding.clone();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof EvidenceFragment other && id.equals(other.id) && documentId.equals(other.documentId)
				&& chunkIndex == other.chunkIndex && Objects.equals(protocol, other.protocol)
				&& Objects.equals(section, other.section) && content.equals(other.content)
				&& Arrays.equals(embedding, other.embedding);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, documentId, chunkIndex, protocol, section, content, Arrays.hashCode(embedding));
	}

	/** Omits content and embedding: neither belongs in logs. */
	@Override
	public String toString() {
		return "EvidenceFragment[id=" + id + ", documentId=" + documentId + ", chunkIndex=" + chunkIndex
				+ ", protocol=" + protocol + ", section=" + section + ", dimensions=" + embedding.length + "]";
	}

}
