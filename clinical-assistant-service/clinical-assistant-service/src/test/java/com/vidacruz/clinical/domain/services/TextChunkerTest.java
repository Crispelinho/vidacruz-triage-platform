package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

	private static String words(int count) {
		return IntStream.range(0, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
	}

	@Test
	void shortTextIsASingleChunk() {
		assertThat(new TextChunker().split("crisis hipertensiva\ntratamiento inicial")).containsExactly(
				"crisis hipertensiva\ntratamiento inicial");
	}

	@Test
	void defaultsUse512TokenWindowsWithHalfOverlap() {
		List<String> chunks = new TextChunker().split(words(1000));

		// windows start at 0, 256, 512; the one at 512 already reaches the end, so no redundant tail chunk follows.
		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0).split(" ")).hasSize(512).startsWith("w0").endsWith("w511");
		assertThat(chunks.get(1).split(" ")).startsWith("w256").endsWith("w767");
		assertThat(chunks.get(2).split(" ")).hasSize(488).startsWith("w512").endsWith("w999");
	}

	@Test
	void consecutiveChunksShareHalfTheirTokens() {
		List<String> chunks = new TextChunker(10, 0.5).split(words(20));

		assertThat(chunks.get(0).split(" ")).endsWith("w9");
		assertThat(chunks.get(1).split(" ")).startsWith("w5");
	}

	@Test
	void everyTokenIsCoveredByAtLeastOneChunk() {
		List<String> chunks = new TextChunker(7, 0.5).split(words(50));

		String joined = String.join(" ", chunks);
		IntStream.range(0, 50).forEach(i -> assertThat(joined).contains("w" + i));
		assertThat(chunks.get(chunks.size() - 1)).endsWith("w49");
	}

	@Test
	void blankTextYieldsNoChunks() {
		assertThat(new TextChunker().split("   \n ")).isEmpty();
	}

	@Test
	void rejectsInvalidConfiguration() {
		assertThatThrownBy(() -> new TextChunker(0, 0.5)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TextChunker(10, 1.0)).isInstanceOf(IllegalArgumentException.class);
	}

}
