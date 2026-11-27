package com.vidacruz.clinical.domain.services;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.GroundedAnswer;
import com.vidacruz.clinical.domain.models.ProtocolSection;

/**
 * Citation validation for the G-04 JSON answer. Every {@code (protocolo, seccion)} must exist in the in-memory corpus
 * index (G-03) **and** in the retrieved set above the relevance threshold. {@code [n]} markers in the answer text are
 * optional but, when present, must point to one of those cited fragments.
 */
public final class CitationValidator {

	private static final Pattern MARKER = Pattern.compile(Pattern.quote("[") + "([0-9]{1,9})" + Pattern.quote("]"));

	private CitationValidator() {
	}

	/**
	 * @return citations derived from the JSON {@code fuentes}, ordered by evidence position and without duplicates
	 * @throws InvalidCitationException when a source is outside the corpus index or outside the retrieved set
	 */
	public static List<Citation> resolve(GroundedAnswer answer, List<EvidenceFragment> evidence,
			CorpusIndex corpusIndex) {
		Objects.requireNonNull(answer, "answer");
		Objects.requireNonNull(corpusIndex, "corpusIndex");
		TreeSet<Integer> positions = new TreeSet<>();
		for (ProtocolSection source : answer.fuentes()) {
			if (!corpusIndex.contains(source)) {
				throw new InvalidCitationException("cited source is not in the corpus index: " + source);
			}
			boolean inRetrievedSet = false;
			for (int i = 0; i < evidence.size(); i++) {
				EvidenceFragment fragment = evidence.get(i);
				if (fragment.protocol() != null && fragment.section() != null
						&& ProtocolSection.from(fragment).equals(source)) {
					positions.add(i + 1);
					inRetrievedSet = true;
				}
			}
			if (!inRetrievedSet) {
				throw new InvalidCitationException("cited source is not in the retrieved set: " + source);
			}
		}
		if (positions.isEmpty()) {
			throw new InvalidCitationException("answer has no valid citation");
		}
		Matcher matcher = MARKER.matcher(answer.respuesta());
		while (matcher.find()) {
			int position = Integer.parseInt(matcher.group(1));
			if (!positions.contains(position)) {
				throw new InvalidCitationException("answer cites position " + position
						+ " which is not one of the validated sources");
			}
		}
		return positions.stream().map(position -> Citation.of(position, evidence.get(position - 1))).toList();
	}

	/**
	 * Legacy positional-only validation kept for the provider contract test. Production uses
	 * {@link #resolve(GroundedAnswer, List, CorpusIndex)} which also checks the corpus index.
	 */
	public static List<Citation> resolve(String answer, List<EvidenceFragment> evidence) {
		TreeSet<Integer> positions = new TreeSet<>();
		Matcher matcher = MARKER.matcher(answer);
		while (matcher.find()) {
			int position = Integer.parseInt(matcher.group(1));
			if (position < 1 || position > evidence.size()) {
				throw new InvalidCitationException(
						"answer cites position " + position + " but only " + evidence.size() + " fragment(s) exist");
			}
			positions.add(position);
		}
		if (positions.isEmpty()) {
			throw new InvalidCitationException("answer has no valid citation marker");
		}
		return positions.stream().map(position -> Citation.of(position, evidence.get(position - 1))).toList();
	}

}
