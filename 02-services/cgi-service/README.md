# cgi-service — Texora SecOps CGI Gateway (build step #8)

A controlled front door onto legacy CGI-style handlers. **Not** a general code-execution platform: only routes on an
explicit, two-person-approved allowlist can run; workers are sandboxed, non-root, no-network and force-killed on timeout.

## ⚠ READ FIRST — the handler runtime is a flagged LOCAL STUB (ADR-D5)

The inventory of real legacy handlers and the isolation runtime are **not decided yet** (ADR-D5 checklist is empty).
All handler execution therefore sits behind ONE interface, `execution.adapter.WorkerIsolationAdapter`, implemented
for now by **`LocalStubWorkerIsolationAdapter`**:

* it runs **no real handler and no OS process**, and provides **no real sandboxing**; it simulates five dummy handlers
  (`stub-echo`, `stub-hang`, `stub-ignore-sigterm`, `stub-fork`, `stub-network`) on daemon threads so the gateway,
  allowlist, timeout, kill and audit machinery can be built and tested;
* it logs a loud `LOGGER.warn` banner at startup and on every spawn;
* it **refuses to start under a `prod`/`production` profile**, and refuses any spec that is not no-network + non-root;
* routes can only be submitted for handler ids the adapter reports via `supportsHandler`.

**Not production-ready until** a real adapter is written (contract in the interface Javadoc: process-group/container
SIGKILL, no network namespace, non-root, read-only FS, nproc limit, idempotent kill) and the ADR-D5 boxes are filled.
`AdapterBoundaryTest` guarantees nothing else in the service can spawn a process.

## Run locally

```
# from repo root: add <module>02-services/cgi-service</module> to the root pom.xml (see below)
mvn -pl 02-services/cgi-service -am verify              # unit tests (+ ITs if Docker is running)
export CGI_DB_PASSWORD=...    # no secret has a default in application.yml
mvn -pl 02-services/cgi-service spring-boot:run -Dspring-boot.run.arguments="--server.port=8089 --spring.profiles.active=local"
```
Local port **8089**. Env: `CGI_DB_URL`, `CGI_DB_USER`, `CGI_DB_PASSWORD`, `CGI_KAFKA_BOOTSTRAP`, `CGI_SEC_BASE_URL`
(default `http://localhost:8083`, the sec-stub), `CGI_SEC_SSL_BUNDLE` (named `spring.ssl.bundle.*` for mTLS).
Root `pom.xml` needs: `<module>02-services/cgi-service</module>`.  The service pom already carries
`<relativePath>../../pom.xml</relativePath>`.  DB grants for the app role: `src/main/resources/db/ops/cgi_app_role_grants.sql`.

## How the five requirements are met

| Req | Where | Proof |
|---|---|---|
| 1 Strict allowlist | `RouteMatchingService` matches only `APPROVED` entries of the caller's tenant, exact paths only (`RoutePatterns`); else 404 before policy/log/worker | `RouteMatchingServiceTest`, `GatewayFlowIntegrationTest` |
| 2 Isolation defaults | `IsolationSpec.strictDefaults`; any change (even loosening) is PENDING until a **second** person approves (`WorkerIsolationConfigService`) | `WorkerIsolationConfigServiceTest` |
| 3 Forced timeout kill | `ExecutionTimeoutService` kills in `finally` on every path, before the caller is answered; unconfirmed kill is never COMPLETED | `ExecutionTimeoutServiceTest`, stub adversarial tests, IT 504 |
| 4 Audit never blocks | `CgiAuditWriter.recordNeverFail`: sync local write in its own tx, async Kafka via bounded executor, outbox + scheduled retry; swallows all errors | `CgiAuditWriterTest`, `KafkaOutageIntegrationTest` |
| 5 Redaction | `ParameterRedactionService` runs before `cgi_request_log` / `cgi_audit_event`; faults discard everything; body content is never stored | `ParameterRedactionServiceTest`, `RedactionRegressionTest` (add a row per onboarded handler) |

Fail-closed summary: unknown route, SEC deny/timeout/garbage, RBAC doubt, request-log failure, execution-record failure,
spawn failure, kill failure, ambiguous isolation config, tenant-config read failure ⇒ request rejected (and worker killed).

## API (base `/api/v1`, plus `/gateway/**`)

`ANY /gateway/{route}` (valid token + SEC `cgi.route.invoke`) · `GET|POST /route-allowlist` (`cgi.route.read|write`) ·
`POST /route-allowlist/{id}/decision` (`cgi.route.approve`) · `GET|POST /worker-isolation` (`cgi.worker.read|write`) ·
`POST /worker-isolation/{id}/decision` (`cgi.worker.approve`, **added**) · `GET|POST /execution-timeout-controls`
(`cgi.timeout.read|write`) · `GET|POST /config` (`cgi.config.read|write`) · `GET /policies` (`cgi.policy.read`) ·
`GET /audit` (`cgi.audit.read`) · `GET /actuator/health` (only exposed actuator endpoint; keep network-internal).
OpenAPI: `/v3/api-docs`, Swagger UI (springdoc 2.8.15). Docs paths need a token unless profile `local`.

## Additions beyond the written LLD/prompt (please review)

* New tables: `cgi_execution_timeout_control`, `cgi_config_entry`, `cgi_audit_outbox`; new columns `max_execution_seconds` on the route entry, and
  `status/submitted_by/decided_by/decided_at/created_at` on `cgi_worker_isolation_config` (needed for the two-person rule). `tenant_id` on every table.
* `cgi_audit_event` is append-only via a DB trigger (any role) + grants script; Kafka state lives in the outbox so it never needs UPDATE.
* Role `cgi.worker.approve` and endpoint `POST /worker-isolation/{id}/decision`.
* An **APPROVED route can be revoked by one REJECTED decision** (tightening must not need two people). Remove if you disagree.
* Concurrency bulkhead (`max-concurrent-workers`, 503 `GATEWAY_BUSY`). No per-route circuit breaker yet (LLD mentions one) — follow-up.
* `GET /policies` returns the built-in policy catalogue; it does **not** call SEC.
* `caller_sso_subject` / `submitted_by` are the token `sub` **UUID** (the LLD sample shows a username string).
* Immutable security value classes (`IsolationSpec`, `WorkerSpawnRequest`, `WorkerResult`, `CallerContext`, `AuditRecord`, …) have getters but **no setters** on purpose; every entity/DTO/props class has explicit getters+setters. No Lombok anywhere (enforcer + `SourceRulesTest`).
* `CgiAuditWriter` is used instead of the shared `@Audited`/AuditingAspect because CGI needs redaction + sync-local-first + never-block (pitfall #11 allows this with a comment). `texora-audit-sdk` is still declared in the pom.

## Assumed contracts (NOT confirmed — verify)

1. **SEC** `POST /api/v1/policies/evaluate`: request `{tenantId, subject, roles[], action, resource}`; response `{"decision":"ALLOW"|"DENY"}`
   (anything else = DENY). Action used: `cgi.route.invoke`; resource = the route pattern. No parameters are ever sent.
2. Roles in the token are the literal strings above (e.g. `cgi.route.read`), no `ROLE_` prefix.
3. Shared-library coordinates/versions are `${project.version}`; the three libs auto-register their beans (incl. a `TokenValidator` bean).

## Build status — be aware

Written in an environment with **no Maven and no access to Maven Central, so nothing here has been compiled or run**.
Sources were only syntax-checked with `javac`. Expect to fix small compile errors on first build (most likely around the
shared-library class names listed in the hand-off notes). Run `mvn verify` with Docker running to execute the ITs.
JaCoCo enforces ≥80 % line coverage on `**/service/**` at `verify`; if it trips, add tests rather than lowering the gate
(`-Djacoco.enforce=false` only for a local run).

## Open items before production
ADR-D5 handler inventory · real `WorkerIsolationAdapter` · Helm chart (B.9) · runbook (D-008) · published OpenAPI file (D-004) ·
per-route circuit breaker · confirm SEC contract.
