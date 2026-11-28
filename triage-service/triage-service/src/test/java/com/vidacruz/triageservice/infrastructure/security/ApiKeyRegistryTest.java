package com.vidacruz.triageservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ApiKeyRegistryTest {

	private static final String EXTRA = "dashboard|triage:read|key-dashboard-0001;"
			+ " prometheus | metrics:read | key|with|pipes ";

	private static ApiKeyRegistry registry(String reviewer, String extra) {
		return ApiKeyRegistry.create("owner-key", reviewer, TriageScopes.REVIEWER, extra, TriageScopes.ALL);
	}

	@Test
	void ownerKeyAloneKeepsEveryScope() {
		ApiKeyRegistry registry = ApiKeyRegistry.single("owner-key", TriageScopes.ALL);

		assertThat(registry.authenticate("owner-key")).hasValueSatisfying(principal -> {
			assertThat(principal.keyId()).isEqualTo(ApiKeyRegistry.OWNER_KEY_ID);
			assertThat(principal.scopes()).containsExactlyInAnyOrderElementsOf(TriageScopes.ALL);
		});
	}

	@Test
	void ownerKeyKeepsEveryScopeWhenReviewerAndExtraKeysAreAdded() {
		ApiKeyRegistry registry = registry("reviewer-key", EXTRA);

		assertThat(registry.authenticate("owner-key")).hasValueSatisfying(
				principal -> assertThat(principal.scopes()).containsExactlyInAnyOrderElementsOf(TriageScopes.ALL));
	}

	@Test
	void reviewerKeyGetsReadAndWriteButNotMetrics() {
		ApiKeyRegistry registry = registry("reviewer-key", null);

		assertThat(registry.authenticate("reviewer-key")).hasValueSatisfying(principal -> {
			assertThat(principal.keyId()).isEqualTo(ApiKeyRegistry.REVIEWER_KEY_ID);
			assertThat(principal.scopes()).containsExactlyInAnyOrder(TriageScopes.READ, TriageScopes.WRITE);
		});
	}

	@Test
	void unsetReviewerKeyMeansOnlyTheOwnerKeyExists() {
		ApiKeyRegistry registry = registry(" ", "");

		assertThat(registry.authenticate("owner-key")).isPresent();
		assertThat(registry.authenticate("reviewer-key")).isEmpty();
		assertThat(registry.authenticate(" ")).isEmpty();
	}

	@Test
	void extraKeysGetOnlyTheirOwnScopes() {
		ApiKeyRegistry registry = registry(null, EXTRA);

		assertThat(registry.authenticate("key-dashboard-0001")).hasValueSatisfying(principal -> {
			assertThat(principal.keyId()).isEqualTo("dashboard");
			assertThat(principal.scopes()).containsExactly(TriageScopes.READ);
		});
		assertThat(registry.authenticate("key|with|pipes")).hasValueSatisfying(
				principal -> assertThat(principal.scopes()).containsExactly(TriageScopes.METRICS));
	}

	@Test
	void unknownMissingOrBlankKeysAreRejected() {
		ApiKeyRegistry registry = registry("reviewer-key", EXTRA);

		assertThat(registry.authenticate("nope")).isEmpty();
		assertThat(registry.authenticate(null)).isEmpty();
		assertThat(registry.authenticate(" ")).isEmpty();
	}

	@Test
	void invalidConfigurationFailsFast() {
		Set<String> all = TriageScopes.ALL;
		Set<String> reviewer = TriageScopes.REVIEWER;
		assertThatThrownBy(() -> ApiKeyRegistry.create(" ", null, reviewer, null, all))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
		assertThatThrownBy(() -> ApiKeyRegistry.create("k0", "k0", reviewer, null, all))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reuses");
		assertThatThrownBy(() -> registry(null, "a|triage:admin|k1"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown scope");
		assertThatThrownBy(() -> registry(null, "a|triage:read"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id|scopes|key");
		assertThatThrownBy(() -> registry(null, "a|triage:read|k1;a|triage:write|k2"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
		assertThatThrownBy(() -> registry("r1", "reviewer|triage:read|k2"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
		assertThatThrownBy(() -> registry(null, "a|triage:read|owner-key"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reuses");
		assertThatThrownBy(() -> registry(null, "a b|triage:read|k1"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id must match");
		assertThatThrownBy(() -> registry(null, "a| |k1"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no scope");
		assertThatThrownBy(() -> registry(null, "a|triage:read| "))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
	}

}
