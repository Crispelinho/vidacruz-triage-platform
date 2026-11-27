# ADR-07: Authentication — API Key via HTTP Header

**Status:** Accepted — implemented in both services on 2026-11-26 (supersedes the Proposed `ADR-07-authentication.md`)  
**Date:** 2026-11-25  
**Decision:** Secure endpoints with an API key passed in the `X-API-Key` HTTP header, validated by a WebFlux filter.

---

## Context

The brief requires authentication on endpoints (NFR-10) but does not specify the mechanism. Two services run independently (ADR-14), and both require consistent authentication. The evaluation is a prototype; production would use a real identity provider, but that is out of scope (brief §2).

**Endpoints to protect:**
- All `/triage/**` paths (RF-01, RF-02, RF-03, RF-05, RF-06).
- All `/asistente/**` paths (RF-04).
- Health and readiness probes remain public (non-authenticated).

**Non-functional requirements:**
- NFR-10: Authentication on endpoints.
- Testable in docker-compose (no external IdP).
- Simple to mock in integration tests.

---

## Problem

Three common approaches:
1. **API key in a header:** Fast, easy to test, common in microservices for service-to-service calls.
2. **JWT (JSON Web Token):** Stateless, but requires an external issuer or complex key management for a prototype.
3. **Session cookies:** Stateful, requires shared session store; not suitable for microservices.

The brief is a 3-day evaluation. JWT with a local issuer is possible but adds:
- Key generation and rotation logic.
- Token refresh flows.
- Validation library integration.
- More complex test setup.

An API key is simpler and sufficient for the brief's scope.

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **API key in `X-API-Key` header** (recommended) | Simple to implement; testable in docker-compose; standard for service-to-service auth; fast validation | Minimal crypto; key rotation must be manual; not suitable for end-users (use JWT for that) |
| **JWT with local issuer** | Stateless; more "production-like" | Adds complexity; key management; token refresh; validation logic; longer implementation time |
| **Session cookies + shared session store** | Familiar to web developers | Stateful; requires Redis/session store; not idiomatic for microservices; harder to test |
| **OAuth 2.0 with external provider** | Industry standard; production-grade | Out of scope (brief says no production IdP); adds external dependency; not testable locally without a mock |

---

## Decision

**Use API key authentication via the `X-API-Key` HTTP header, validated by a WebFlux filter.**

**Implementation:**
1. **API key storage:** Stored as a configuration property (environment variable or `application.properties`), not in code.
   - Triage service: `triage.api-key` (default: a test key, e.g., `test-triage-key`)
   - Assistant service: `assistant.api-key` (default: a test key, e.g., `test-assistant-key`)

2. **Filter chain (WebFlux):**
   ```java
   // Pseudo-code
   public class ApiKeyAuthenticationFilter {
     public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
       if (isPublicPath(exchange.getRequest().getPath())) {
         return chain.filter(exchange); // Allow through
       }
       String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
       if (isValidApiKey(apiKey, configuredKey)) {
         return chain.filter(exchange); // Allow through
       }
       return unauthorizedResponse(exchange); // 401 Unauthorized
     }
   }
   ```

3. **Public paths (no authentication required):**
   - `/health` (Spring Boot Actuator)
   - `/health/ready` (readiness probe)
   - `/health/live` (liveness probe)
   - All other paths require a valid `X-API-Key` header.

4. **Error response:**
   ```json
   {
     "code": "UNAUTHORIZED",
     "message": "Invalid or missing X-API-Key header",
     "correlationId": "..."
   }
   ```

5. **Request/response logging:**
   - Log all authentication failures with the correlation ID.
   - Do **not** log the API key itself.

---

## Consequences

### Positive
- **Quick to implement:** WebFlux filter is straightforward; no external dependencies.
- **Testable locally:** API key is just a string; docker-compose can set it via environment variables.
- **Service-to-service ready:** If a frontend calls these services, it includes the API key in requests (or a gateway injects it).
- **Non-stateful:** No session store needed; each request is independently validated.
- **Clear security boundary:** Every protected endpoint is guarded; health probes are public.

### Negative
- **Minimal cryptography:** API key is transmitted in plaintext in the HTTP header. **Mitigation:** Use HTTPS (TLS) in production. For this evaluation, docker-compose runs locally without TLS, and the key is a test value.
- **No expiration:** A leaked key remains valid until rotated manually. **Mitigation:** Short TTL for the test key; in production, use a real authentication service (JWT or OAuth).
- **Shared key across all clients:** Every caller uses the same key. **Mitigation:** For this prototype, acceptable. In production, issue per-client keys and rotate them.
- **No role-based access control (RBAC):** All authenticated clients have the same permissions. **Mitigation:** Implement in a follow-up if fine-grained access is needed.

---

## Risks

- **R-11 (key exposure):** API keys in environment variables can be exposed if logs, build artifacts, or container images are not secured.
  - **Probability:** Medium (common mistake).
  - **Impact:** High (attackers can call the APIs).
  - **Mitigation:**
    - Never commit the key to git.
    - Use `.gitignore` for `application-local.properties`.
    - Scan the codebase and Dockerfile for hardcoded keys (pre-commit hook).
    - Logs must not print the full header value.

- **R-12 (brute-force attacks):** An attacker can try many keys without rate limiting.
  - **Probability:** Low (brief is a prototype, not exposed to the internet).
  - **Impact:** Medium (service availability).
  - **Mitigation:** Add rate limiting per IP (optional for this evaluation; document as a future improvement).

---

## References

- Brief: NFR-10 (authentication on endpoints)
- Decision register: DEC-05 (API key vs JWT)
- Ambiguity: AMB-09 (JWT or API key; this ADR chooses API key)
- Related ADRs: ADR-10 (platform baseline; no TLS configured here), ADR-01 (applies to both services)
- OpenSpec change: 10 (security), which applies this ADR to all endpoints
- Linked risk: R-11 (key exposure), R-12 (brute-force)

---

## Alternative (JWT) — Deferred Decision

If the evaluation requires end-user authentication (not just service-level), or if three additional days are available post-delivery, JWT with a local issuer can be substituted:
- Use Spring Security's `JwtDecoder` (Spring Boot 3.5 includes it).
- Issue tokens in a `/auth/login` endpoint (username/password for testing).
- Validate tokens in the same WebFlux filter.

For now, API key is sufficient and faster.
