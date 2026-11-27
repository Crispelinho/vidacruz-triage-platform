package com.vidacruz.clinicalassistantservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vidacruz.clinical.domain.ports.ConsultationMetrics;

/**
 * M-2: the real {@link SecurityConfiguration} with scoped keys in front of stub routes that mirror the service's public
 * surface. The main property under test: a query key cannot ingest corpus.
 */
@WebFluxTest(controllers = ScopedRouteAuthorizationTest.StubRoutes.class)
@Import({ SecurityConfiguration.class, ScopedRouteAuthorizationTest.StubRoutes.class })
@TestPropertySource(properties = { "assistant.api-keys=frontend|assistant:query|query-key-0001;"
		+ "curator|corpus:admin|corpus-key-0002;prometheus|metrics:read|metrics-key-0003" })
class ScopedRouteAuthorizationTest {

	@Autowired
	private WebTestClient client;

	/** Required by the RAG @ControllerAdvice the WebFlux slice picks up; unused by the stub routes. */
	@MockitoBean
	private ConsultationMetrics consultationMetrics;

	@Test
	void queryKeyCanAskAndRetrieveButCannotIngest() {
		client.post().uri("/asistente/consulta").header("X-API-Key", "query-key-0001").exchange()
				.expectStatus().isOk();
		client.post().uri("/rag").header("X-API-Key", "query-key-0001").exchange().expectStatus().isOk();
		client.get().uri("/asistente/estado-urgencias").header("X-API-Key", "query-key-0001").exchange()
				.expectStatus().isOk();
		client.get().uri("/clinical/corpus/retrieve").header("X-API-Key", "query-key-0001").exchange()
				.expectStatus().isOk();
		client.post().uri("/clinical/corpus/ingest").header("X-API-Key", "query-key-0001").exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.code").isEqualTo("FORBIDDEN")
				.jsonPath("$.correlationId").isNotEmpty();
	}

	@Test
	void corpusAdminKeyCanIngestButCannotAsk() {
		client.post().uri("/clinical/corpus/ingest").header("X-API-Key", "corpus-key-0002").exchange()
				.expectStatus().isOk();
		client.post().uri("/asistente/consulta").header("X-API-Key", "corpus-key-0002").exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void metricsKeyReachesOnlyTheScrapeEndpoint() {
		client.get().uri("/actuator/prometheus").header("X-API-Key", "metrics-key-0003").exchange()
				.expectStatus().isOk();
		client.post().uri("/rag").header("X-API-Key", "metrics-key-0003").exchange().expectStatus().isForbidden();
	}

	@Test
	void healthIsPublicUnknownRoutesAreDeniedAndBadKeysGet401() {
		client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
		client.get().uri("/other").header("X-API-Key", "query-key-0001").exchange().expectStatus().isForbidden();
		client.post().uri("/rag").exchange().expectStatus().isUnauthorized();
		client.post().uri("/rag").header("X-API-Key", "test-assistant-key").exchange()
				.expectStatus().isUnauthorized();
	}

	@RestController
	static class StubRoutes {

		@PostMapping({ "/asistente/consulta", "/rag", "/clinical/corpus/ingest" })
		String post() {
			return "{}";
		}

		@GetMapping({ "/asistente/estado-urgencias", "/clinical/corpus/retrieve", "/actuator/prometheus",
				"/actuator/health/liveness", "/other" })
		String get() {
			return "{}";
		}

	}

}
