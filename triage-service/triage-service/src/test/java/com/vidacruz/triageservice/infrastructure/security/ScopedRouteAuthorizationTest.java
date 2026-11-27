package com.vidacruz.triageservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * M-2: the real {@link SecurityConfiguration} with scoped keys in front of stub routes that mirror triage-service's
 * public surface. Asserts per-route scopes, the 403 body and the PHI-read audit line.
 */
@WebFluxTest(controllers = ScopedRouteAuthorizationTest.StubRoutes.class)
@Import({ SecurityConfiguration.class, ScopedRouteAuthorizationTest.StubRoutes.class })
@TestPropertySource(properties = { "triage.api-keys=dashboard|triage:read|reader-key-0001;"
		+ "intake|triage:read,triage:write|writer-key-0002;prometheus|metrics:read|metrics-key-0003",
		"triage.api-key=legacy-key-is-ignored" })
class ScopedRouteAuthorizationTest {

	private static final String PATIENT = "/triage/pacientes/3f2c1b9e-0000-4000-8000-000000000001";

	@Autowired
	private WebTestClient client;

	private ListAppender<ILoggingEvent> audit;

	@BeforeEach
	void captureAuditLog() {
		audit = new ListAppender<>();
		audit.start();
		((Logger) LoggerFactory.getLogger(PhiReadAuditFilter.AUDIT_LOGGER)).addAppender(audit);
	}

	@AfterEach
	void releaseAuditLog() {
		((Logger) LoggerFactory.getLogger(PhiReadAuditFilter.AUDIT_LOGGER)).detachAppender(audit);
	}

	@Test
	void readKeyCanReadButCannotWrite() {
		client.get().uri(PATIENT).header("X-API-Key", "reader-key-0001").exchange().expectStatus().isOk();
		client.post().uri("/triage/pacientes").header("X-API-Key", "reader-key-0001").exchange()
				.expectStatus().isForbidden()
				.expectBody()
				.jsonPath("$.code").isEqualTo("FORBIDDEN")
				.jsonPath("$.correlationId").isNotEmpty();
		client.patch().uri(PATIENT + "/estado").header("X-API-Key", "reader-key-0001").exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void writeKeyCanRegisterAndChangeState() {
		client.post().uri("/triage/pacientes").header("X-API-Key", "writer-key-0002").exchange()
				.expectStatus().isOk();
		client.patch().uri(PATIENT + "/estado").header("X-API-Key", "writer-key-0002").exchange()
				.expectStatus().isOk();
	}

	@Test
	void metricsKeyReachesOnlyTheScrapeEndpoint() {
		client.get().uri("/actuator/prometheus").header("X-API-Key", "metrics-key-0003").exchange()
				.expectStatus().isOk();
		client.get().uri(PATIENT).header("X-API-Key", "metrics-key-0003").exchange().expectStatus().isForbidden();
		client.get().uri("/actuator/prometheus").header("X-API-Key", "reader-key-0001").exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void healthProbesStayPublicAndUnknownRoutesAreDenied() {
		client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
		client.get().uri("/other").header("X-API-Key", "writer-key-0002").exchange().expectStatus().isForbidden();
	}

	@Test
	void missingWrongAndLegacyKeysGet401() {
		client.get().uri(PATIENT).exchange().expectStatus().isUnauthorized();
		client.get().uri(PATIENT).header("X-API-Key", "wrong").exchange().expectStatus().isUnauthorized();
		client.get().uri(PATIENT).header("X-API-Key", "legacy-key-is-ignored").exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void phiReadIsAuditedWithTheKeyIdAndNeverTheKeyValue() {
		client.get().uri(PATIENT).header("X-API-Key", "reader-key-0001").header("X-Correlation-Id", "corr-42")
				.exchange().expectStatus().isOk();

		List<String> lines = audit.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(lines).singleElement().satisfies(line -> assertThat(line)
				.contains("keyId=dashboard")
				.contains("patientId=3f2c1b9e-0000-4000-8000-000000000001")
				.contains("status=200")
				.contains("correlationId=corr-42")
				.doesNotContain("reader-key-0001"));
	}

	@Test
	void deniedPhiReadIsAuditedAndOtherRoutesAreNot() {
		client.get().uri(PATIENT).header("X-API-Key", "metrics-key-0003").exchange().expectStatus().isForbidden();
		client.post().uri("/triage/pacientes").header("X-API-Key", "writer-key-0002").exchange()
				.expectStatus().isOk();
		client.get().uri(PATIENT).exchange().expectStatus().isUnauthorized();

		assertThat(audit.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
				.contains("keyId=prometheus").contains("status=403"));
	}

	@RestController
	static class StubRoutes {

		@GetMapping("/triage/pacientes/{id}")
		String detail(@PathVariable String id) {
			return "{}";
		}

		@PostMapping("/triage/pacientes")
		String register() {
			return "{}";
		}

		@PatchMapping("/triage/pacientes/{id}/estado")
		String changeState(@PathVariable String id) {
			return "{}";
		}

		@GetMapping("/actuator/prometheus")
		String scrape() {
			return "";
		}

		@GetMapping("/actuator/health/readiness")
		String readiness() {
			return "UP";
		}

		@GetMapping("/other")
		String other() {
			return "";
		}

	}

}
