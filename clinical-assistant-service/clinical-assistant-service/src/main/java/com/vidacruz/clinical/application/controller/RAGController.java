package com.vidacruz.clinical.application.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.clinical.application.dto.RAGRequestDTO;
import com.vidacruz.clinical.application.dto.RAGResponseDTO;
import com.vidacruz.clinical.domain.ports.ConsultationMetrics;
import com.vidacruz.clinical.domain.services.RAGService;

import reactor.core.publisher.Mono;

/** API-04 endpoint. {@code /rag} is kept as a compatibility alias for existing clients. */
@RestController
public class RAGController {

	private final RAGService ragService;

	private final ConsultationMetrics metrics;

	public RAGController(RAGService ragService, ConsultationMetrics metrics) {
		this.ragService = ragService;
		this.metrics = metrics;
	}

	@PostMapping({ "/asistente/consulta", "/rag" })
	public Mono<RAGResponseDTO> ask(@RequestBody RAGRequestDTO request) {
		// toDomain() runs inside the Mono so an invalid prompt surfaces as an error signal handled by the advice.
		return Mono.fromSupplier(request::toDomain)
				.flatMap(ragService::answer)
				.doOnNext(answer -> {
					if (!answer.hasEvidence()) {
						metrics.noEvidence();
					} else {
						metrics.answered();
					}
				})
				.map(RAGResponseDTO::from);
	}

}
