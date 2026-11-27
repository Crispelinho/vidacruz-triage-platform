package com.vidacruz.triageservice.event.infrastructure;

import com.vidacruz.triageservice.event.domain.OutboxEvento;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface OutboxEventoRepository extends R2dbcRepository<OutboxEvento, UUID> {

    Mono<OutboxEvento> findFirstByPacienteIdOrderByPacienteSequenceDesc(UUID pacienteId);

    @Query("UPDATE outbox_evento SET lease_until = :leaseUntil, lease_owner = :leaseOwner " +
           "WHERE event_id = :eventId AND publicado_en IS NULL " +
           "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)")
    Mono<Integer> claimLeaseIfAvailable(@Param("eventId") UUID eventId,
                                        @Param("leaseUntil") OffsetDateTime leaseUntil,
                                        @Param("leaseOwner") String leaseOwner);

    @Query("SELECT * FROM outbox_evento WHERE publicado_en IS NULL " +
           "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP) " +
           "ORDER BY created_at ASC LIMIT :limit")
    Flux<OutboxEvento> findUnpublishedWithoutLease(@Param("limit") int limit);

    // R2DBC @Query UPDATEs complete empty here, so the claim status is read back by owner instead of relying on
    // the UPDATE's count. Without this check the publisher would send rows claimed by another instance.
    @Query("SELECT COUNT(*) FROM outbox_evento WHERE event_id = :eventId " +
           "AND lease_owner = :leaseOwner AND lease_until > CURRENT_TIMESTAMP AND publicado_en IS NULL")
    Mono<Long> countClaimedBy(@Param("eventId") UUID eventId, @Param("leaseOwner") String leaseOwner);

    @Query("SELECT * FROM outbox_evento WHERE paciente_id = :pacienteId " +
           "ORDER BY paciente_sequence ASC")
    Flux<OutboxEvento> findByPacienteIdOrderBySequence(@Param("pacienteId") UUID pacienteId);

    @Query("SELECT COUNT(*) FROM outbox_evento WHERE publicado_en IS NULL")
    Mono<Long> countUnpublished();

    @Query("UPDATE outbox_evento SET lease_until = :leaseUntil, lease_owner = :leaseOwner " +
           "WHERE event_id = :eventId")
    Mono<Void> claimLease(@Param("eventId") UUID eventId,
                          @Param("leaseUntil") OffsetDateTime leaseUntil,
                          @Param("leaseOwner") String leaseOwner);

    @Query("UPDATE outbox_evento SET publicado_en = :publicadoEn, " +
           "lease_until = NULL, lease_owner = NULL " +
           "WHERE event_id = :eventId")
    Mono<Void> markPublished(@Param("eventId") UUID eventId,
                             @Param("publicadoEn") OffsetDateTime publicadoEn);

    @Query("UPDATE outbox_evento SET " +
           "intentos = intentos + 1, " +
           "ultimo_fallo_en = CURRENT_TIMESTAMP, " +
           "ultimo_error = :errorMessage, " +
           "requiere_revision = :requiereRevision " +
           "WHERE event_id = :eventId")
    Mono<Void> recordFailure(@Param("eventId") UUID eventId,
                             @Param("errorMessage") String errorMessage,
                             @Param("requiereRevision") Boolean requiereRevision);

    @Query("UPDATE outbox_evento SET " +
           "primer_fallo_en = CURRENT_TIMESTAMP, " +
           "ultimo_fallo_en = CURRENT_TIMESTAMP, " +
           "ultimo_error = :errorMessage, " +
           "intentos = 1 " +
           "WHERE event_id = :eventId AND primer_fallo_en IS NULL")
    Mono<Void> recordFirstFailure(@Param("eventId") UUID eventId,
                                   @Param("errorMessage") String errorMessage);
}
