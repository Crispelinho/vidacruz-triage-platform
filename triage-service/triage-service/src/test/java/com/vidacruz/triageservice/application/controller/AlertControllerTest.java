package com.vidacruz.triageservice.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.vidacruz.triageservice.domain.model.AlertStatus;
import com.vidacruz.triageservice.domain.model.MedicalAlert;
import com.vidacruz.triageservice.domain.model.MedicalAlertRepository;
import com.vidacruz.triageservice.domain.model.TriageLevel;
import com.vidacruz.triageservice.domain.service.AcknowledgeAlertUseCase;
import com.vidacruz.triageservice.domain.service.ListActiveAlertsUseCase;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = AlertController.class)
@Import({ ListActiveAlertsUseCase.class, AcknowledgeAlertUseCase.class, AlertControllerTest.TestSecurity.class })
class AlertControllerTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	@MockitoBean
	private MedicalAlertRepository repository;

	@Autowired
	private WebTestClient client;

	@Test
	void listsActiveAlertsWithTheSpanishContractAndNoPersonalData() {
		when(repository.findActive()).thenReturn(Flux.just(alert(UUID.randomUUID(), AlertStatus.PENDIENTE)));

		client.get().uri("/triage/alertas")
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
	static class TestSecurity {

		@Bean
		SecurityWebFilterChain permitAll(ServerHttpSecurity http) {
			return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchange -> exchange.anyExchange().permitAll())
					.build();
		}
	}
}
