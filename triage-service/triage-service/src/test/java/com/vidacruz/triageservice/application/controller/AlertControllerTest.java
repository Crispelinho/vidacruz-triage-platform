package com.vidacruz.triageservice.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.model.TriageMetrics;
import com.vidacruz.triageservice.domain.service.AcknowledgeAlertUseCase;
import com.vidacruz.triageservice.domain.service.ListActiveAlertsUseCase;
import com.vidacruz.triageservice.domain.service.WatchAlertsUseCase;
import com.vidacruz.triageservice.infrastructure.security.ApiKeyAuthenticationFilter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@WebFluxTest(controllers = AlertController.class)
@TestPropertySource(properties = "alerts.stream.heartbeat=100ms")
@Import({ ListActiveAlertsUseCase.class, AcknowledgeAlertUseCase.class, AlertControllerTest.TestSecurity.class })
class AlertControllerTest {

	private static final String API_KEY = "test-alert-key";
	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	@MockitoBean
	private MedicalAlertRepository repository;

	@MockitoBean
	private TriageMetrics metrics;

	@MockitoBean
	private WatchAlertsUseCase watchAlerts;

	@Autowired
	private WebTestClient client;

	@Test
	void streamsTheActiveSnapshotAndHeartbeatsOverHttp() {
		when(watchAlerts.watch()).thenReturn(Flux.just(alert(UUID.randomUUID(), AlertStatus.PENDIENTE)));

		StepVerifier.create(client.get()
				.uri("/triage/alertas/stream")
				.header("X-API-Key", API_KEY)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
				.returnResult(byte[].class)
				.getResponseBody()
				.takeUntil(frame -> new String(frame, StandardCharsets.UTF_8).contains(":latido"))
				.map(bytes -> new String(bytes, StandardCharsets.UTF_8))
				.reduce("", String::concat))
				.assertNext(body -> assertThat(body)
						.contains("event:alerta")
						.contains("\"nivel\":\"CRITICO\"")
						.contains(":latido")
						.doesNotContain("\"nombre\"")
						.doesNotContain("\"motivo\""))
				.verifyComplete();
	}

	@Test
	void rejectsANonPositiveHeartbeat() {
		assertThatThrownBy(() -> new AlertStreamProperties(Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("positive duration");
		assertThat(new AlertStreamProperties(Duration.ofSeconds(15)).heartbeat()).isEqualTo(Duration.ofSeconds(15));
	}

	@Test
	void listsActiveAlertsWithTheSpanishContractAndNoPersonalData() {
		when(repository.findActive()).thenReturn(Flux.just(alert(UUID.randomUUID(), AlertStatus.PENDIENTE)));

		client.get().uri("/triage/alertas")
				.header("X-API-Key", API_KEY)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].pacienteId").exists()
				.jsonPath("$[0].nivel").isEqualTo("CRITICO")
				.jsonPath("$[0].estado").isEqualTo("PENDIENTE")
				.jsonPath("$[0].minutosEsperaAlEscalar").isEqualTo(5.0)
				.jsonPath("$[0].reconocidaPor").doesNotExist()
				.jsonPath("$[0].nombre").doesNotExist();
	}

	@Test
	void acknowledgesAnAlert() {
		UUID id = UUID.randomUUID();
		when(repository.acknowledge(eq(id), eq("Dra. Pérez"), any(Instant.class))).thenReturn(Mono.just(true));
		when(repository.findById(id)).thenReturn(Mono.just(alert(id, AlertStatus.RECONOCIDA)));

		client.post().uri("/triage/alertas/{id}/reconocimiento", id)
				.header("X-API-Key", API_KEY)
				.bodyValue(new com.vidacruz.triageservice.application.dto.AcknowledgeAlertRequest("Dra. Pérez"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.estado").isEqualTo("RECONOCIDA")
				.jsonPath("$.reconocidaPor").isEqualTo("Dra. Pérez");
	}

	@Test
	void rejectsAShortResponsibleWithTRG004() {
		client.post().uri("/triage/alertas/{id}/reconocimiento", UUID.randomUUID())
				.header("X-API-Key", API_KEY)
				.bodyValue(new com.vidacruz.triageservice.application.dto.AcknowledgeAlertRequest("no"))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("TRG-004")
				.jsonPath("$.field").isEqualTo("responsable");
	}

	@Test
	void rejectsANonUuidIdWithTRG004() {
		client.post().uri("/triage/alertas/{id}/reconocimiento", "not-a-uuid")
				.header("X-API-Key", API_KEY)
				.bodyValue(new com.vidacruz.triageservice.application.dto.AcknowledgeAlertRequest("Dra. Pérez"))
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.code").isEqualTo("TRG-004")
				.jsonPath("$.field").isEqualTo("id");
	}

	@Test
	void returnsTRG008ForAnUnknownAlert() {
		UUID id = UUID.randomUUID();
		when(repository.acknowledge(eq(id), eq("Dra. Pérez"), any(Instant.class))).thenReturn(Mono.just(false));
		when(repository.findById(id)).thenReturn(Mono.empty());

		client.post().uri("/triage/alertas/{id}/reconocimiento", id)
				.header("X-API-Key", API_KEY)
				.bodyValue(new com.vidacruz.triageservice.application.dto.AcknowledgeAlertRequest("Dra. Pérez"))
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.code").isEqualTo("TRG-008");
	}

	private static MedicalAlert alert(UUID id, AlertStatus status) {
		return new MedicalAlert(id, UUID.randomUUID(), TriageLevel.CRITICO, 5,
				NOW.minusSeconds(300), NOW, BigDecimal.valueOf(5), status,
				status == AlertStatus.PENDIENTE ? null : "Dra. Pérez",
				status == AlertStatus.PENDIENTE ? null : NOW, null, NOW);
	}

	@TestConfiguration
	@EnableConfigurationProperties(AlertStreamProperties.class)
	static class TestSecurity {

		@Bean
		WebFilter apiKeyAuthenticationFilter(ObjectMapper objectMapper) {
			return new ApiKeyAuthenticationFilter(API_KEY, objectMapper);
		}

		@Bean
		SecurityWebFilterChain permitAll(ServerHttpSecurity http) {
			return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchange -> exchange.anyExchange().permitAll())
					.build();
		}
	}
}
