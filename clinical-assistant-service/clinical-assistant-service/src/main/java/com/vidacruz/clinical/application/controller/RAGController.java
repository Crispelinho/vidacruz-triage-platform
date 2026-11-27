package com.vidacruz.clinical.application.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.clinical.application.dto.RAGRequestDTO;
import com.vidacruz.clinical.application.dto.RAGResponseDTO;
import com.vidacruz.clinical.domain.ports.ConsultationMetrics;
import com.vidacruz.clinical.domain.services.RAGService;

import reactor.core.publisher.Mono;

/**
 * Scaffold endpoint for the RAG use case. The final path and authentication belong to C-06 and the Azure
 * integration (API-04 is {@code POST /asistente/consulta}).
 */
@RestController
@RequestMapping("/rag")
public class RAGController {

	private final RAGService ragService;

	private final ConsultationMetrics metrics;

	public RAGController(RAGService ragService, ConsultationMetrics metrics) {
		this.ragService = ragService;
		this.metrics = metrics;
	}

	@PostMapping
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
