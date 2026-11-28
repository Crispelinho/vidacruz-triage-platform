package com.vidacruz.clinical.infrastructure.adapters;

import java.util.List;
import java.util.Objects;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.domain.ports.NamedLLMProvider;
import com.vidacruz.clinical.domain.services.PiiRedactor;

import reactor.core.publisher.Mono;

/**
 * Wraps every node that sends the question outside the cluster (Gemini, OpenCode, OpenRouter) so the question is
 * redacted with {@link PiiRedactor} before it is serialised (M-5). The in-cluster Ollama node is not wrapped. Evidence comes from the
 * curated protocol corpus, not from the patient, so it is passed through unchanged.
 *
 * <p>Keeping the decorator in front of the adapter means the key and model stay the same, so circuit breakers,
 * metrics and {@code usedModel} are unaffected.
 */
public final class PiiRedactingLLMProvider implements NamedLLMProvider {

	private final NamedLLMProvider delegate;

	public PiiRedactingLLMProvider(NamedLLMProvider delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public String key() {
		return delegate.key();
	}

	@Override
	public String model() {
		return delegate.model();
	}

	@Override
	public Mono<String> generate(String question, List<EvidenceFragment> evidence) {
		return delegate.generate(PiiRedactor.redact(question), evidence);
	}

}
