package com.vidacruz.clinical.domain.models;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Provenance of one piece of evidence that grounded an answer.
 *
 * <p>A citation can only be built from a real {@link EvidenceFragment}, so it never carries invented sources. It is
 * identified inside an answer by its 1-based {@code position}, the number the answer uses as a marker, e.g. {@code [2]}.
 *
 * @param position   1-based position in the evidence list handed to the LLM
 * @param fragmentId id of the retrieved {@link EvidenceFragment}
 * @param documentId document the fragment belongs to
 * @param chunkIndex position of the fragment inside its document
 * @param protocol   optional protocol code of the fragment
 * @param section    optional protocol section of the fragment
 */
public record Citation(int position, UUID fragmentId, UUID documentId, int chunkIndex, String protocol,
		String section) {

	public Citation {
		if (position < 1) {
			throw new IllegalArgumentException("position must be >= 1");
		}
		Objects.requireNonNull(fragmentId, "fragmentId");
		Objects.requireNonNull(documentId, "documentId");
		if (chunkIndex < 0) {
			throw new IllegalArgumentException("chunkIndex must be >= 0");
		}
	}

	public static Citation of(int position, EvidenceFragment fragment) {
		Objects.requireNonNull(fragment, "fragment");
		return new Citation(position, fragment.id(), fragment.documentId(), fragment.chunkIndex(),
				fragment.protocol(), fragment.section());
	}

	/** One citation per fragment, numbered from 1 in list order. */
	public static List<Citation> fromEvidence(List<EvidenceFragment> evidence) {
		return IntStream.range(0, evidence.size()).mapToObj(i -> of(i + 1, evidence.get(i))).toList();
	}

}
