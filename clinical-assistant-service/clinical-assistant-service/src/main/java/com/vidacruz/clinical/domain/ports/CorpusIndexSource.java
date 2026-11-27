package com.vidacruz.clinical.domain.ports;

import java.util.List;

import com.vidacruz.clinical.domain.models.ProtocolSection;

import reactor.core.publisher.Mono;

/** Source of the protocol/section pairs currently stored in the corpus (G-03). */
public interface CorpusIndexSource {

	/** All distinct pairs in the store. Empty when the corpus is empty. */
	Mono<List<ProtocolSection>> protocolSections();

}
