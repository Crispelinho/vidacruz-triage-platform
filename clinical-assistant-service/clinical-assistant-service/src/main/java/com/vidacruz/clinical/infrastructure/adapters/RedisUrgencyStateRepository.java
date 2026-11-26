package com.vidacruz.clinical.infrastructure.adapters;

import com.vidacruz.clinical.domain.model.urgency.PatientSnapshot;
import com.vidacruz.clinical.domain.model.urgency.TriageLevel;
import com.vidacruz.clinical.domain.model.urgency.UrgencyState;
import com.vidacruz.clinical.domain.ports.UrgencyStateRepository;
import com.vidacruz.clinical.infrastructure.config.UrgencyStateProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

/**
 * Projection of the emergency room in Redis: latest state of each patient and one set of active patients per level.
 * Every change is one Lua script, so a reader never sees a patient counted twice or in no level.
 */
@Repository
public class RedisUrgencyStateRepository implements UrgencyStateRepository {

    private static final long APPLIED = 1L;
    private static final String ACTIVE = "1";
    private static final String INACTIVE = "0";

    private final ReactiveStringRedisTemplate redis;
    private final String dischargedRetentionSeconds;
    private final RedisScript<Long> applyScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> readScript;

    /** Scripts are read here, at startup: reading them lazily would do file IO on a reactive thread. */
    public RedisUrgencyStateRepository(ReactiveStringRedisTemplate redis, UrgencyStateProperties properties) {
        this.redis = redis;
        this.dischargedRetentionSeconds = String.valueOf(properties.dischargedRetention().toSeconds());
        this.applyScript = RedisScript.of(script("redis/apply-patient-state.lua"), Long.class);
        this.readScript = RedisScript.of(script("redis/read-urgency-state.lua"), List.class);
    }

    private static String script(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read Redis script " + path, error);
        }
    }

    @Override
    public Mono<Boolean> apply(PatientSnapshot snapshot) {
        var keys = new ArrayList<String>();
        keys.add(UrgencyKeys.patient(snapshot.patientId()));
        keys.add(UrgencyKeys.UPDATED_AT);
        keys.addAll(UrgencyKeys.activeOfEveryLevel());

        var arguments = new ArrayList<String>();
        arguments.add(snapshot.patientId().toString());
        arguments.add(snapshot.level().map(TriageLevel::code).orElse(""));
        arguments.add(snapshot.status().name());
        arguments.add(String.valueOf(toMicros(snapshot.occurredAt())));
        arguments.add(snapshot.isActive() ? ACTIVE : INACTIVE);
        arguments.add(dischargedRetentionSeconds);
        arguments.add(String.valueOf(snapshot.sequence()));
        for (var level : TriageLevel.values()) {
            arguments.add(level.code());
        }

        return redis.execute(applyScript, keys, arguments)
                .next()
                .map(result -> result == APPLIED)
                .onErrorMap(error -> unavailable(error));
    }

    @Override
    public Mono<UrgencyState> current() {
        var keys = new ArrayList<>(UrgencyKeys.activeOfEveryLevel());
        keys.add(UrgencyKeys.UPDATED_AT);
        return redis.execute(readScript, keys, List.of())
                .next()
                .map(RedisUrgencyStateRepository::toState)
                .onErrorMap(error -> unavailable(error));
    }

    @Override
    public Mono<Boolean> isInitialized() {
        return redis.hasKey(UrgencyKeys.INITIALIZED).onErrorMap(error -> unavailable(error));
    }

    @Override
    public Mono<Void> markInitialized() {
        return redis.opsForValue().set(UrgencyKeys.INITIALIZED, Instant.now().toString())
                .then()
                .onErrorMap(error -> unavailable(error));
    }

    /** Counts of the levels in enum order, then the time of the latest event ('' before any event). */
    static UrgencyState toState(List<?> result) {
        var counts = new EnumMap<TriageLevel, Long>(TriageLevel.class);
        var levels = TriageLevel.values();
        for (var index = 0; index < levels.length; index++) {
            counts.put(levels[index], ((Number) result.get(index)).longValue());
        }
        var updatedAt = String.valueOf(result.get(levels.length));
        return new UrgencyState(counts, updatedAt.isEmpty()
                ? Optional.empty()
                : Optional.of(Instant.EPOCH.plus(Long.parseLong(updatedAt), ChronoUnit.MICROS)));
    }

    static long toMicros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private static Throwable unavailable(Throwable error) {
        return error instanceof UrgencyStateUnavailableException
                ? error
                : new UrgencyStateUnavailableException("La proyección de urgencias no está disponible", error);
    }
}
