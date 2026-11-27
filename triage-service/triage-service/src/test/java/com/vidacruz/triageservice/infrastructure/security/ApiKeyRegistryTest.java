package com.vidacruz.triageservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ApiKeyRegistryTest {

	private static final String KEYS = "dashboard|triage:read|key-dashboard-0001;"
			+ " intake | triage:read , triage:write | key-intake-0002 ;prometheus|metrics:read|key|with|pipes";

	@Test
	void blankMultiKeyPropertyFallsBackToTheLegacyKeyWithEveryScope() {
		ApiKeyRegistry registry = ApiKeyRegistry.parse(" ", "legacy-key", TriageScopes.ALL);

		assertThat(registry.authenticate("legacy-key")).hasValueSatisfying(principal -> {
			assertThat(principal.keyId()).isEqualTo(ApiKeyRegistry.LEGACY_KEY_ID);
			assertThat(principal.scopes()).containsExactlyInAnyOrderElementsOf(TriageScopes.ALL);
		});
	}

	@Test
	void eachKeyGetsOnlyItsOwnScopes() {
		ApiKeyRegistry registry = ApiKeyRegistry.parse(KEYS, "ignored-legacy", TriageScopes.ALL);

		assertThat(registry.authenticate("key-dashboard-0001")).hasValueSatisfying(principal -> {
			assertThat(principal.keyId()).isEqualTo("dashboard");
			assertThat(principal.scopes()).containsExactly(TriageScopes.READ);
		});
		assertThat(registry.authenticate("key-intake-0002")).hasValueSatisfying(principal -> assertThat(
				principal.scopes()).containsExactlyInAnyOrder(TriageScopes.READ, TriageScopes.WRITE));
		assertThat(registry.authenticate("key|with|pipes")).hasValueSatisfying(
				principal -> assertThat(principal.keyId()).isEqualTo("prometheus"));
	}

	@Test
	void legacyKeyIsNotAcceptedOnceScopedKeysAreConfigured() {
		ApiKeyRegistry registry = ApiKeyRegistry.parse(KEYS, "legacy-key", TriageScopes.ALL);

		assertThat(registry.authenticate("legacy-key")).isEmpty();
	}

	@Test
	void unknownMissingOrBlankKeysAreRejected() {
		ApiKeyRegistry registry = ApiKeyRegistry.parse(KEYS, null, TriageScopes.ALL);

		assertThat(registry.authenticate("nope")).isEmpty();
		assertThat(registry.authenticate(null)).isEmpty();
		assertThat(registry.authenticate(" ")).isEmpty();
	}

	@Test
	void invalidConfigurationFailsFast() {
		Set<String> scopes = TriageScopes.ALL;
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a|triage:admin|k1", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown scope");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a|triage:read", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id|scopes|key");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a|triage:read|k1;a|triage:write|k2", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a|triage:read|k1;b|triage:write|k1", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reuses");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a b|triage:read|k1", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id must match");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a| |k1", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no scope");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("a|triage:read| ", null, scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
		assertThatThrownBy(() -> ApiKeyRegistry.parse("", " ", scopes))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
	}

}
