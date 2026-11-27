package com.vidacruz.clinical.infrastructure.adapters;

import com.vidacruz.clinical.domain.exceptions.LLMUnavailableException;

import reactor.core.publisher.Mono;

/** Maps provider HTTP outcomes to the single failover trigger without echoing prompts or response bodies. */
final class ProviderFailures {

	private ProviderFailures() {
	}

	static <T> Mono<T> map(String node, String model, Mono<T> mono) {
		return mono.onErrorMap(error -> {
			int status = statusOf(error);
			if (status == 429 || status >= 500) {
				return new LLMUnavailableException(node + "/" + model + " returned HTTP " + status, error);
			}
			if (error instanceof LLMUnavailableException) {
				return error;
			}
			return new LLMUnavailableException(node + "/" + model + " unavailable", error);
		});
	}

	private static int statusOf(Throwable error) {
		if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException responseException) {
			return responseException.getStatusCode().value();
		}
		return 0;
	}

}
