package com.vidacruz.triageservice.event.infrastructure;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import reactor.core.publisher.Mono;

/**
 * Records the publication of one outbox event as a producer span in the trace of the request that wrote it (NFR-16).
 * The span is a child of the stored {@code traceparent}; the Kafka header still carries that stored value, so
 * consumers keep joining the request trace as before. Only the destination is attached: no key, payload or exception
 * message.
 */
@Component
public class OutboxPublishTracing {

	private static final String TRACEPARENT = "traceparent";

	private final Tracer tracer;
	private final Propagator propagator;

	public OutboxPublishTracing(Tracer tracer, Propagator propagator) {
		this.tracer = tracer;
		this.propagator = propagator;
	}

	/** Traces {@code send} when the event was written inside a trace; otherwise returns it untouched. */
	<T> Mono<T> traced(String traceParent, String topic, Mono<T> send) {
		if (traceParent == null || traceParent.isBlank()) {
			return send;
		}
		return Mono.defer(() -> {
			Map<String, String> carrier = Map.of(TRACEPARENT, traceParent);
			Span span = propagator.extract(carrier, Map::get)
					.name(topic + " publish")
					.kind(Span.Kind.PRODUCER)
					.tag("messaging.system", "kafka")
					.tag("messaging.destination.name", topic)
					.start();
			return send.doOnError(error -> span.error(new PublicationFailed()))
					.doFinally(signal -> span.end());
		});
	}

	/** No cause, message detail or stack trace: broker errors can quote the record. */
	static final class PublicationFailed extends RuntimeException {

		private PublicationFailed() {
			super("Outbox publication failed", null, false, false);
		}
	}
}
