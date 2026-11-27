package com.vidacruz.clinical.domain.services;

import java.util.Objects;

import com.vidacruz.clinical.domain.ports.CorpusIndexSource;

import reactor.core.publisher.Mono;

/** Loads the in-memory citation index from the corpus at startup (G-03). */
public final class CorpusIndexRefresher {

	private final CorpusIndexSource source;

	private final CorpusIndex index;

	public CorpusIndexRefresher(CorpusIndexSource source, CorpusIndex index) {
		this.source = Objects.requireNonNull(source, "source");
		this.index = Objects.requireNonNull(index, "index");
	}

	public Mono<Void> refresh() {
		return source.protocolSections().doOnNext(index::refresh).then();
	}

}
