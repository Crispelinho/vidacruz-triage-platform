package com.vidacruz.clinical.domain.services;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;

/**
 * Checks that every citation marker in an answer points to a retrieved fragment.
 *
 * <p>Marker syntax: {@code [n]} with {@code n} the 1-based position in the evidence list (see {@link EvidenceContext}).
 * An answer without markers is valid; this class never adds citations, it only rejects invented ones.
 */
public final class CitationValidator {

	private static final Pattern MARKER = Pattern.compile(Pattern.quote("[") + "([0-9]{1,9})" + Pattern.quote("]"));

	private CitationValidator() {
	}

	/**
	 * @return the citations actually used by the answer, ordered by position, without duplicates
	 * @throws InvalidCitationException when a marker is {@code [0]} or exceeds the evidence size
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
		return positions.stream().map(position -> Citation.of(position, evidence.get(position - 1))).toList();
	}

}
