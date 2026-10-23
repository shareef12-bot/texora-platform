# vpn-service — Texora SecOps VPN Access Control Plane (build #7)

> ## ⚠️ THE VPN TUNNEL DOES NOT EXIST. THE GATEWAY ADAPTER IS A STUB. ⚠️
> This service is the **control plane only** (policies, approvals, sessions, revocation, audit). The encrypted-tunnel
> dataplane is a vendor product that **has not been chosen** (ADR-D5). All dataplane access goes through ONE interface,
> `gateway/GatewayIntegrationAdapter`. The only implementation today is `LocalStubGatewayIntegrationAdapter`: **not real,
> no tunnel, in-memory, logs WARN on every call.** The same applies to `LocalStubMfaFactorVerifier` (accepts fixed code `000000`).
> Under `-Dspring.profiles.active=prod` the service **refuses to start** while either stub is wired (`StartupGuard`), and
> `application-prod.yml` sets `vpn.gateway.adapter=vendor`, for which no bean exists. **Not production-ready until a real adapter is written.**
> - [ ] ADR-D5: VPN dataplane vendor selected: ________

## Run locally
```
mvn -pl 02-services/vpn-service -am verify      # unit tests (*Test) + Docker integration tests (*IntegrationTest) + coverage gate
mvn -pl 02-services/vpn-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8088
```
Port **8088**. Needs PostgreSQL (schema `vpn`, Flyway creates it) and Kafka (`vpn.audit.events`). Integration tests need Docker running locally.
Root `pom.xml` needs `<module>02-services/vpn-service</module>`.

## Guarantees and where they live
| Guarantee | Where |
|---|---|
| Fail closed on establishment (ambiguous/DENY/device/MFA/SEC failure/any exception → DENY) | `SessionControlService.establish` |
| Proactive revocation on uncertainty; control plane marks REVOKED **first**, then tears down the tunnel; retried until the dataplane confirms | `revokeCore`, `retryPendingTeardowns`, `SessionRevalidationScheduler` |
| Trigger definitions (SEC timeout, identity staleness, …) | `docs/UNCERTAINTY-TRIGGERS.md` — **proposals, please confirm** |
| Two-person approval, enforced in the service layer, attempt audited | `PolicyApprovalService.decide` |
| No credentials stored/logged: opaque `dataplane_session_ref`; audit payload redaction; no secret columns (tested) | schema, `VpnAuditRecorder` |
| Audit retries to Kafka, never dropped; immutable (DB trigger) | `VpnAuditRecorder` + `AuditOutboxPublisher`, `V1__vpn_schema.sql` |
| Dataplane callbacks use mTLS allow-list, not SSO tokens | `RbacInterceptor`, `DataplaneCallbackController` |

## Roles
`vpn.policy.read|write|approve|assign` · `vpn.session.read|revoke|establish` · `vpn.gateway.read|write` · `vpn.config.read|write` ·
`vpn.audit.read` · `vpn.subject.read|write` · `vpn.device.read|write`. An `/api/v1` handler with no access rule is denied.

## Deviations from the prompt (please review)
* **Extra endpoints** (not in the prompt's list, needed for it to work): `POST /sessions`, `/subjects`, `/subjects/{id}/devices`, `/access-policies/{id}/assignments`, `/access-policies/{id}/disable`, `POST …/gateway-integration/dataplane-events` (mTLS).
* **Extra tables/columns:** `vpn_audit_outbox` (so audit can be immutable *and* retried), `vpn_config`, `vpn_gateway_endpoint`; session `expires_at/last_validated_at/next_validation_at/revocation_*`; binding `teardown_*`; `tenant_id` on every table.
* **No `AuditingAspect` class** (you said not to build a local one): a local *writer* `VpnAuditRecorder` is used, and says why in its Javadoc.
* **Disable** needs `vpn.policy.approve` but no second person (it only reduces access).
* **Not delivered:** Helm chart, committed OpenAPI file (springdoc serves it), runbook beyond this README + the trigger doc, real vendor adapter, real MFA.

## ⚠️ NOT COMPILED, NOT RUN
The build environment had no Maven Central and none of the `texora-*` jars. I verified with `javac` only that the sources
parse and that nothing is missing on my own classes. **Nothing was compiled against Spring or the shared libraries, and no test has been executed.**
The JaCoCo ≥80% gate is unverified. Expect a first-compile round for the items below.

## Unsure about shared-library / integration details
1. `TokenValidator` is injected as a Spring bean (assumed registered by `texora-iam-client`). `PlatformException.UnauthenticatedException` imported from `com.texora.secops.starter.domain` (package from PITFALLS #11; nesting from #1).
2. **`TenantContext` is not used** (its API is unknown): tenant comes from `TokenClaims.getTenantId()` via `CallerResolver`. **`TenantFilteredRepository` is not extended** (generics unknown); every repository method takes `tenantId` explicitly. Swap in if you prefer.
3. **`@Audited` / shared `AuditingAspect` not used** (attribute names unknown). `texora-audit-sdk` is declared but unused; its auto-config might need properties I don't set.
4. Shared `GlobalExceptionHandler`: its `@Order` and handling of other `PlatformException` subclasses are unknown; mine is pinned to LOWEST_PRECEDENCE and only maps `UnauthenticatedException` explicitly. A `PlatformException` it doesn't catch would fall into my catch-all as a 500.
5. Library artifact coordinates/versions (`${project.version}`) and the root property `spring-boot.version=3.5.16` are copied from the pitfalls file, not from `sso-service/pom.xml` (not available to me).
6. How the IAM client finds signing keys: I set `spring.security.oauth2.resourceserver.jwt.issuer-uri` as a guess.
7. I declare my own permit-all `SecurityFilterChain`; if the starter also registers one, you'll get a bean clash.
8. OpenTelemetry/Actuator wiring is assumed to come from the starter.
9. **SEC contract assumed:** `POST /api/v1/policies/decisions` → `{"decision":"ALLOW|DENY"}`. **DC contract assumed:** `GET /api/v1/users/{id}` → `{"status":"ACTIVE"}`. Both over mTLS via SSL bundles.
10. Tests: `@MockitoBean` for `TokenValidator`/clients, `mock(TokenClaims.class)` (final class), Objenesis for the exception, and `org.testcontainers.containers.KafkaContainer`.
11. Hibernate maps `String` ↔ `jsonb` (`@JdbcTypeCode(SqlTypes.JSON)`); the integration tests are what would catch a problem.
