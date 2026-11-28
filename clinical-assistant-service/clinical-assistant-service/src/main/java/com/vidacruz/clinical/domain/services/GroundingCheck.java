package com.vidacruz.clinical.domain.services;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vidacruz.clinical.domain.exceptions.InvalidCitationException;
import com.vidacruz.clinical.domain.models.Citation;
import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.models.GroundedAnswer;

/**
 * Cheap post-check that the {@code respuesta} text is tied to the evidence it cites (M-1). {@link CitationValidator}
 * proves every source exists and was retrieved; this check reduces "citation laundering", where an answer cites a
 * real source but states something the source does not say.
 *
 * <p>It is a heuristic, not a proof of entailment. Residual risk: a model can still paraphrase a false claim with the
 * evidence vocabulary, so the clinician must verify the cited source.
 */
public final class GroundingCheck {

	private static final Pattern MARKER = Pattern.compile("\\[([0-9]{1,9})]");

	private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:[.,][0-9]+)?");

	private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");

	/** Short words ("de", "la", "con") carry no evidence signal. Tokens with a digit are always kept. */
	private static final int MIN_WORD_LENGTH = 4;

	/** Spanish function words of 4+ letters (normalized, no diacritics): they say nothing about the evidence. */
	private static final Set<String> STOPWORDS = Set.of("para", "como", "debe", "deben", "segun", "entre", "sobre",
			"cuando", "este", "esta", "estos", "estas", "desde", "hasta", "tambien", "puede", "pueden", "cada", "todo",
			"toda", "todos", "todas", "otro", "otra", "otros", "otras", "pero", "porque", "donde", "mientras", "antes",
			"despues", "durante", "evidencia", "protocolo", "respuesta", "paciente", "pacientes", "recomienda",
			"indica", "siempre", "luego", "tras", "sino", "aunque", "ademas", "mismo", "misma", "dicho", "dicha");

	private final Policy policy;

	public GroundingCheck(Policy policy) {
		this.policy = Objects.requireNonNull(policy, "policy");
	}

	/**
	 * @param citations the citations {@link CitationValidator} already validated against index and retrieved set
	 * @throws InvalidCitationException when the answer is not tied to the cited evidence under the policy
	 */
	public void verify(GroundedAnswer answer, List<Citation> citations, List<EvidenceFragment> evidence) {
		Objects.requireNonNull(answer, "answer");
		String text = answer.respuesta();
		boolean hasMarker = MARKER.matcher(text).find();
		if (policy.requireMarkers() && !hasMarker) {
			throw new InvalidCitationException("answer has no [n] marker tying it to the cited evidence");
		}
		Set<String> evidenceTokens = new HashSet<>();
		StringBuilder evidenceText = new StringBuilder();
		for (Citation citation : citations) {
			String content = evidence.get(citation.position() - 1).content();
			evidenceTokens.addAll(tokens(content));
			evidenceText.append(normalize(content)).append(' ');
		}
		String claims = MARKER.matcher(text).replaceAll(" ");
		if (policy.numbersMustAppearInEvidence()) {
			Set<String> evidenceNumbers = numbers(evidenceText.toString());
			for (String number : numbers(normalize(claims))) {
				if (!evidenceNumbers.contains(number)) {
					throw new InvalidCitationException("answer states a number that is not in the cited evidence");
				}
			}
		}
		// A markerless answer must at least share vocabulary with what it cites; an answer with markers is held to
		// the stricter, opt-in minLexicalOverlap only.
		double required = Math.max(policy.minLexicalOverlap(), hasMarker ? 0.0d : policy.markerlessMinOverlap());
		if (required > 0.0d) {
			Set<String> answerTokens = tokens(claims);
			if (answerTokens.isEmpty()) {
				if (!hasMarker) {
					throw new InvalidCitationException("answer has neither a marker nor evidence vocabulary");
				}
				return;
			}
			long supported = answerTokens.stream().filter(evidenceTokens::contains).count();
			double overlap = (double) supported / answerTokens.size();
			if (overlap < required) {
				throw new InvalidCitationException("answer lexical overlap with the cited evidence is below " + required);
			}
		}
	}

	static Set<String> tokens(String text) {
		Set<String> tokens = new HashSet<>();
		Matcher matcher = TOKEN.matcher(normalize(text));
		while (matcher.find()) {
			String token = matcher.group();
			if (STOPWORDS.contains(token)) {
				continue;
			}
			if (token.length() >= MIN_WORD_LENGTH || token.chars().anyMatch(Character::isDigit)) {
				tokens.add(token);
			}
		}
		return tokens;
	}

	private static Set<String> numbers(String normalized) {
		Set<String> numbers = new HashSet<>();
		Matcher matcher = NUMBER.matcher(normalized);
		while (matcher.find()) {
			numbers.add(matcher.group().replace(',', '.'));
		}
		return numbers;
	}

	/** Lower case without diacritics, so "Adrenalina" and "adrenalína" compare equal. */
	private static String normalize(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}

	/** Default share of content words a markerless answer must share with the cited evidence. */
	public static final double DEFAULT_MARKERLESS_MIN_OVERLAP = 0.2d;

	/**
	 * @param requireMarkers              the answer must carry at least one {@code [n]} marker; {@link CitationValidator}
	 *                                    already checks that every marker points to a validated source
	 * @param markerlessMinOverlap        share of content words, in [0, 1], that an answer WITHOUT markers must share
	 *                                    with the cited fragments; {@code 0} accepts markerless answers unchecked
	 * @param minLexicalOverlap           share of content words, in [0, 1], that EVERY answer must share with the
	 *                                    cited fragments; {@code 0} disables the check
	 * @param numbersMustAppearInEvidence every number in the answer (doses, ranges, times) must appear in a cited
	 *                                    fragment
	 */
	public record Policy(boolean requireMarkers, double markerlessMinOverlap, double minLexicalOverlap,
			boolean numbersMustAppearInEvidence) {

		public Policy {
			requireUnit("markerlessMinOverlap", markerlessMinOverlap);
			requireUnit("minLexicalOverlap", minLexicalOverlap);
		}

		/**
		 * Default: an answer needs an {@code [n]} marker OR, without one, at least
		 * {@value GroundingCheck#DEFAULT_MARKERLESS_MIN_OVERLAP} lexical overlap with what it cites. Providers do not
		 * always emit markers, so requiring them would turn valid answers into the no-protocol notice. The stricter
		 * checks stay opt-in because the Ollama fixture answer cannot pass them.
		 */
		public static Policy defaults() {
			return new Policy(false, DEFAULT_MARKERLESS_MIN_OVERLAP, 0.0d, false);
		}

		/** Pre-M-1 behaviour, for tests that exercise other gates in isolation. */
		public static Policy disabled() {
			return new Policy(false, 0.0d, 0.0d, false);
		}

		private static void requireUnit(String name, double value) {
			if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
				throw new IllegalArgumentException(name + " must be between 0 and 1");
			}
		}

	}

}
