# ADR-06: RF-06 Escalation Notification Channel — Outbox-Driven Webhook

**Status:** Proposed  
**Date:** 2026-11-25  
**Decision:** Deliver escalation alerts via HTTP webhook (HTTP POST) with outbox-driven retry, mock receiver in docker-compose for testing.

---

## Context

The brief requires the medical team to be alerted when a patient is escalated (RF-06, DER-03). The channel, format, and technology are intentionally open (brief §4, RF-06). Common choices:
1. **Email (SMTP):** Familiar, but requires a mail server; delays of seconds to minutes; hard to test locally.
2. **Webhook (HTTP POST):** Fast, testable, integrates with modern alerting systems (PagerDuty, Slack, custom endpoints).
3. **SMS:** Requires a carrier; expensive; real-time but constrained.
4. **Kafka or message queue:** Decouples from the receiver; adds complexity; overkill for a simple alert.

**Key requirements (RF-06, DER-03, AMB-10):**
- Alert delivered at least once.
- Failures are retried.
- Each escalation produces at most one alert (idempotent delivery).

---

## Problem

1. **Reliability:** An escalation event must result in exactly one alert reaching the team, even if the receiver is temporarily unavailable.
2. **Testability:** The evaluation must be testable locally in docker-compose without external dependencies.
3. **Traceability:** The alert must include the patient ID, escalation time, and level for the team to act.
4. **Simplicity:** The brief is a 3-day prototype; the channel should be quick to implement.

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **Webhook + outbox retry** (recommended) | Fast, testable locally, standard for integrations, decoupled from receiver | Requires retry logic, exponential backoff; receiver must be idempotent; network-dependent |
| **Email (SMTP)** | Familiar to teams; audit trail in mail history | Mail server required; delays; hard to test; not real-time; requires SMTP credentials |
| **SMS** | Real-time, mobile-friendly | Expensive; carrier dependency; limited message length; requires phone number storage |
| **Kafka topic (alerts)** | Decouples publisher from receiver; standard in event-driven systems | Over-engineered for a simple alert; adds another topic to manage; still need a consumer |
| **Direct HTTP POST** (no retry) | Simplest implementation | Single point of failure; if receiver is down, alert is lost |

---

## Decision

**Use outbox-driven webhook (HTTP POST) with exponential backoff retry.**

**Flow:**
1. SLA evaluator (change 5, `triage-sla-escalation`) escalates a patient:
   - Update `Paciente.escalado = true`.
   - Write audit row.
   - Publish `paciente_escalado` event (EVT-02) to Kafka.
   - Insert an `AlertaEscalamiento` outbox row in the same transaction.

2. Alert publisher (async background job):
   - Polls `AlertaEscalamiento` table every 5 seconds (configurable).
   - Finds rows with `enviado = false`.
   - Makes HTTP POST to the configured webhook URL with the alert payload.
   - On success (HTTP 2xx): marks row as `enviado = true`, records `fechaEnvio`.
   - On failure (HTTP 4xx, 5xx, or timeout):
     - Increments `intentosReintento`.
     - Calculates next retry time: `2^(intentosReintento - 1)` seconds (exponential backoff: 1s, 2s, 4s, 8s, ..., max 300s = 5 min).
     - Retries up to 10 times; after that, marks as `fallido = true` and alerts are sent to a DLQ (dead-letter queue / fallback channel).

3. Webhook receiver (mocked in docker-compose, real in production):
   - Receives POST request with JSON payload.
   - Must be idempotent (same `escalamientoId` → same action, no duplicate alerts).
   - Responds with 2xx or 4xx (error); 5xx triggers retry.

**Configuration:**
```properties
# application.properties or environment variables
triage.alerts.webhook-url=http://alert-receiver:8080/webhooks/escalation
triage.alerts.retry-interval-seconds=5
triage.alerts.max-retries=10
triage.alerts.backoff-multiplier=2
```

**Alert payload (JSON):**
```json
{
  "escalamientoId": "uuid",
  "pacienteId": "uuid",
  "nivel": "CRITICO",
  "nombrePaciente": "Ana Pérez",
  "slaMinutos": 5,
  "fechaRegistro": "2026-11-24T16:00:00Z",
  "fechaEscalamiento": "2026-11-24T16:05:30Z",
  "correlationId": "...",
  "timestamp": "2026-11-24T16:05:31Z"
}
```

**Docker Compose mock receiver:**
```dockerfile
# In docker-compose.yml, a simple Express or Spring Boot app:
alert-receiver:
  image: test-alert-receiver:latest
  ports:
    - "8080:8080"
  environment:
    - LOG_LEVEL=INFO
  # Receives POST /webhooks/escalation, logs the payload, responds 200 OK
```

---

## Consequences

### Positive
- **Testable locally:** Mock receiver in docker-compose logs all alerts for inspection.
- **Reliable delivery:** Outbox pattern + retry ensures at least one delivery; exponential backoff avoids overwhelming a failed receiver.
- **Idempotent by design:** Each escalation event has a unique `escalamientoId`; receiver can deduplicate by this key.
- **Observable:** Retry attempts and eventual failures are logged; support can inspect the `AlertaEscalamiento` table to debug.
- **Production-ready:** Can be replaced with a real HTTP endpoint (PagerDuty webhook, Slack incoming webhook, custom alerting system) without code changes.
- **Decoupled:** Receiver availability does not block the Triage service; retries happen asynchronously.

### Negative
- **Requires outbox table:** Adds `AlertaEscalamiento` table to the Triage database (small overhead).
- **Eventual consistency:** Alert delivery is eventually consistent; a network partition can delay alerts by up to 5 minutes (configurable).
  - **Mitigation:** For a 3-day prototype, acceptable. Production SLAs would set a stricter timeout.
- **Receiver must be idempotent:** If the receiver is not idempotent (e.g., updates a database without checking `escalamientoId`), duplicates can occur during retries.
  - **Mitigation:** Document this clearly; provide an idempotent receiver example.
- **No end-to-end encryption:** Webhook payloads are sent over HTTP (locally) or HTTPS (production). Patient data is included in the payload.
  - **Mitigation:** Use HTTPS in production; store sensitive data (e.g., patient names) only if necessary. For this evaluation, local HTTP is acceptable.

---

## Risks

- **R-11 (webhook URL exposure):** The webhook URL is stored as a configuration property. If logs or build artifacts expose it, attackers can spam the receiver.
  - **Probability:** Low (URL is not a secret, but it identifies the receiver endpoint).
  - **Impact:** Medium (receiver spam; DoS risk).
  - **Mitigation:** Rate-limit the receiver by IP; log all attempts; in production, require authentication (API key or OAuth).

- **R-13 (retry storm):** If a receiver is completely down, the publisher will retry up to 10 times before giving up, sending at least 10 requests. If multiple escalations occur, this scales.
  - **Probability:** Medium (temporary receiver outages are common).
  - **Impact:** Low (10 requests per escalation is not a DoS).
  - **Mitigation:** Exponential backoff already limits the rate; cap total retries at a reasonable number (10 is acceptable).

- **R-14 (data persistence in outbox):** The outbox table grows over time; without cleanup, it consumes disk space.
  - **Probability:** High (inherent to outbox pattern).
  - **Impact:** Low (table cleanup is a standard DBA task).
  - **Mitigation:** Add a cleanup job (remove rows older than 7 days marked as `enviado = true`) post-delivery.

---

## References

- Brief: RF-06 (escalation alerts), AMB-10 (channel open), DER-03 (at least one, idempotent)
- Decision register: DEC-09 (webhook proposed)
- Risk register: R-11 (URL exposure), R-13 (retry storm), R-14 (outbox growth)
- Related ADRs: ADR-03 (transactional outbox), ADR-14 (database ownership)
- OpenSpec change: 7 (medical-escalation-alerts)
- Persistence: `AlertaEscalamiento` table in `vidacruz_triage` database (Triage service owns it)
- Configuration: environment variables for webhook URL and retry parameters

---

## Alternative Channels (Deferred)

If the webhook fails during evaluation or the team prefers a different channel:
- **Email (SMTP):** Requires a mail server in docker-compose or external SMTP service.
- **Kafka topic (escalations):** Add a consumer that reads from the topic and forwards to a channel.
- **In-app notification (future):** Store alert in the database; frontend polls for updates.

For now, webhook is the fastest path to delivery.
