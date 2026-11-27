package com.vidacruz.clinical.infrastructure.adapters;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.vidacruz.clinical.domain.ports.EmbeddingProvider;

import reactor.core.publisher.Mono;

/**
 * Deterministic lexical embedding with no external service or API key.
 *
 * <p>Text is lowercased, accents are removed, common Spanish function words are dropped and each remaining token is
 * reduced to a stable stem. The stem is hashed into one bucket of a unit vector; repeated stems use sublinear weight.
 * Cosine similarity between two vectors is therefore stem overlap. This is a lexical baseline calibrated for the
 * seeded protocol corpus, not a general semantic embedding model: paraphrases without shared stems are not detected.
 */
public class LexicalEmbeddingAdapter implements EmbeddingProvider {

	private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

	private static final int STEM_LENGTH = 6;

	private static final Set<String> STOPWORDS = Set.of("a", "al", "ante", "como", "con", "de", "del", "el", "en",
			"es", "hay", "la", "las", "los", "o", "por", "para", "son", "se", "su", "sus", "un", "una", "unos",
			"unas", "y", "cual", "cuales", "cuando", "que");

	private final int dimensions;

	public LexicalEmbeddingAdapter(int dimensions) {
		if (dimensions < 1) {
			throw new IllegalArgumentException("dimensions must be positive");
		}
		this.dimensions = dimensions;
	}

	@Override
	public Mono<float[]> embed(String text) {
		return Mono.fromSupplier(() -> vectorFor(text));
	}

	private float[] vectorFor(String text) {
		double[] buckets = new double[dimensions];
		Map<String, Integer> counts = new HashMap<>();
		String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
		for (String token : NON_WORD.split(normalized)) {
			if (!token.isEmpty() && !STOPWORDS.contains(token)) {
				String stem = stem(token);
				counts.merge(stem, 1, Integer::sum);
			}
		}
		counts.forEach((stem, count) -> buckets[Math.floorMod(stem.hashCode(), dimensions)] += 1.0 + Math.log(count));
		double norm = 0d;
		for (double value : buckets) {
			norm += value * value;
		}
		float[] vector = new float[dimensions];
		if (norm == 0d) {
			return vector;
		}
		double scale = 1.0 / Math.sqrt(norm);
		for (int i = 0; i < dimensions; i++) {
			vector[i] = (float) (buckets[i] * scale);
		}
		return vector;
	}

	private static String stem(String token) {
		return token.length() > STEM_LENGTH ? token.substring(0, STEM_LENGTH) : token;
	}

}
