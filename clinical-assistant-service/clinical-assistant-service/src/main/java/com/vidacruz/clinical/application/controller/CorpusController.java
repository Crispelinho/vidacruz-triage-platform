package com.vidacruz.clinical.application.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.clinical.application.dto.CorpusIngestRequestDTO;
import com.vidacruz.clinical.application.dto.CorpusIngestResponseDTO;
import com.vidacruz.clinical.application.dto.RetrievedChunkDTO;
import com.vidacruz.clinical.domain.services.CorpusIngestionService;
import com.vidacruz.clinical.domain.services.RagRetrievalService;

import reactor.core.publisher.Mono;

/**
 * RF-04A corpus endpoints. Behind the ADR-07 API key: ingestion needs scope {@code corpus:admin}, retrieval needs
 * {@code assistant:query} (M-2, see SecurityConfiguration).
 */
@RestController
@RequestMapping("/clinical/corpus")
public class CorpusController {

	private final CorpusIngestionService ingestionService;

	private final RagRetrievalService retrievalService;

	public CorpusController(CorpusIngestionService ingestionService, RagRetrievalService retrievalService) {
		this.ingestionService = ingestionService;
		this.retrievalService = retrievalService;
	}

	@PostMapping("/ingest")
	public Mono<CorpusIngestResponseDTO> ingest(@RequestBody CorpusIngestRequestDTO request) {
		return ingestionService.ingestDocument(request.title(), request.content()).map(CorpusIngestResponseDTO::from);
	}

	@GetMapping("/retrieve")
	public Mono<List<RetrievedChunkDTO>> retrieve(@RequestParam("query") String query,
			@RequestParam(name = "threshold", defaultValue = "" + RagRetrievalService.DEFAULT_THRESHOLD) double threshold) {
		return retrievalService.retrieve(query, threshold)
				.map(chunks -> chunks.stream().map(RetrievedChunkDTO::from).toList());
	}

}
