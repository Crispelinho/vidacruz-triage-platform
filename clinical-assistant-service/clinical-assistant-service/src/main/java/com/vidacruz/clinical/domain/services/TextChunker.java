package com.vidacruz.clinical.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sliding-window splitter. A token is approximated by a whitespace-delimited word (no model tokenizer is chosen yet,
 * ADR-05). Chunks are slices of the original text, so inner line breaks and punctuation survive.
 *
 * <p>With the defaults (512 tokens, 50% overlap) consecutive chunks share 256 tokens, so a sentence cut at one
 * boundary appears whole in the neighbouring chunk.
 */
public final class TextChunker {

	public static final int DEFAULT_CHUNK_TOKENS = 512;

	public static final double DEFAULT_OVERLAP = 0.5d;

	private static final Pattern WORD = Pattern.compile("\\S+");

	private final int chunkTokens;

	private final int step;

	public TextChunker() {
		this(DEFAULT_CHUNK_TOKENS, DEFAULT_OVERLAP);
	}

	public TextChunker(int chunkTokens, double overlap) {
		if (chunkTokens < 1) {
			throw new IllegalArgumentException("chunkTokens must be positive");
		}
		if (!(overlap >= 0.0d && overlap < 1.0d)) {
			throw new IllegalArgumentException("overlap must be in [0, 1)");
		}
		this.chunkTokens = chunkTokens;
		this.step = Math.max(1, (int) Math.round(chunkTokens * (1.0d - overlap)));
	}

	public List<String> split(String text) {
		List<int[]> words = new ArrayList<>();
		Matcher matcher = WORD.matcher(text);
		while (matcher.find()) {
			words.add(new int[] { matcher.start(), matcher.end() });
		}
		List<String> chunks = new ArrayList<>();
		for (int start = 0; start < words.size(); start += step) {
			int end = Math.min(start + chunkTokens, words.size());
			chunks.add(text.substring(words.get(start)[0], words.get(end - 1)[1]));
			if (end == words.size()) {
				break;
			}
		}
		return chunks;
	}

}
