# dc-service — Domain Controller (build step #5)

Identity **control plane**: security groups, policy + policy assignment, two-person configuration approval, directory
sync to LDAP, and an HRMS-sourced user projection. It does **not** implement LDAP bind/search.

```
mvn -pl 02-services/dc-service -am verify            # unit tests + enforcer (Lombok ban)
mvn -pl 02-services/dc-service verify -Pcoverage-gate # + 80% service-layer gate (CI)
java -jar target/dc-service.jar --server.port=8085    # needs DC_DB_PASSWORD, Postgres, Kafka
```
Root `pom.xml` needs: `<module>02-services/dc-service</module>`. OpenAPI: `/v3/api-docs`, `/swagger-ui.html` (disable in prod with `DC_API_DOCS_ENABLED=false`).

## ⚠️ LOUD WARNING — two local STUBS (ADR D-2 / no SEC yet)

| Stub | Why | Where | Guard |
|---|---|---|---|
| **`LocalStubHrmsEmployeeAdapter`** | HRMS has **no API or event stream** yet. Serves 4 fake employees (EMP-1001..1004), tenant-agnostic. | `adapter/` — the **only** HRMS seam (`HrmsEmployeeAdapter`) | Logs a banner at startup; **refuses to start** under profile `prod`/`production` |
| **`LocalStubSyncEnvelopeSigner`** | Sync messages must be signed, key must come from SEC/KMS, which does not exist yet. Uses an **ephemeral in-memory HMAC key**. | `sync/client/` (`SyncEnvelopeSigner`) | Same banner + prod refusal |

When HRMS ships an API/feed: add a real `HrmsEmployeeAdapter` (`dc.hrms.adapter=rest`) — nothing else changes.
**Open ADR D-2 items still need answers from the HRMS owner** (API/event availability; confirm security groups live in DC).

## ADR D-2 — what changed vs. the original prompt
* `dc_user` is a projection of HRMS (`hrms_employee_id`, `hrms_last_modified` added). HRMS-owned fields (`username, displayName, orgUnitId, status`) can never be authored in DC → `422 HRMS_OWNED_FIELD`.
* `POST /users` = *materialize one HRMS employee now* (`{"hrmsEmployeeId":"EMP-1001","initialGroups":[...]}`). `PATCH /users/{id}` only toggles the DC-owned `securityLocked` overlay.
* `dc_sync_job` has a second source `HRMS_PULL` (`POST /directory-synchronization {"source":"HRMS_PULL"}` or the scheduled pull, off by default: `dc.hrms.sync.enabled`, `dc.hrms.sync.tenant-ids`). Upserts are idempotent and monotonic (stale/replayed snapshots ignored).
* Policy, config approval and security groups stay in DC.

## Deviations from the prompt (all deliberate; reviewer should confirm)
1. **Extra endpoint `POST /api/v1/groups`** (`dc.group.write`) — without it no group can exist.
2. **`tenant_id` added** to `dc_group_membership`, `dc_policy_assignment`, `dc_config_approval`, `dc_sync_job` (B.5 says every table). Extra columns: `dc_group.updated_at`, `dc_policy.created_by/at`, `dc_config_item.submitted_at/updated_at/rejection_reason/activated_at`, `dc_config_approval.reason`, `dc_sync_job` (`entity_type, entity_id, operation, idempotency_key, attempts, next_retry_at, requested_by, payload`), `dc_policy_assignment.subject_id` is `VARCHAR` (role names).
3. **`dc_audit_publication`** side table holds Kafka-publish state so `dc_audit_event` stays strictly append-only (DB trigger blocks UPDATE/DELETE/TRUNCATE even for the owner; `dc_app` also loses the grants).
4. **`DcAuditRecorder` instead of a local `AuditingAspect`** — see "Unverified assumptions" #5.
5. Feature packages follow the prompt; Spring `@Configuration` classes live in `infra/` (the prompt's `config/` package is the config-approval feature).
6. Repositories extend Spring's bare `Repository` (not `JpaRepository`) so no un-scoped CRUD is exposed — see #4.
7. Directory-sync endpoint returns `200` with the job (the B.7 status list has no 202).
8. Extra privilege-escalation guards (threat note): no self policy assignment / assignment to a group you belong to / a role you hold (`403 PRIVILEGE_ESCALATION_BLOCKED`); no adding yourself to a group (`SELF_MEMBERSHIP_FORBIDDEN`). Config keys that look like secrets are refused (`SECRET_IN_CONFIG`, B.6.6).
9. `PolicyEvaluationService` has **no HTTP endpoint** (none was specified). Needed by SSO/VPN? Add `POST /api/v1/policy-evaluation` later. Policy *revoke* was likewise not specified, so not built.

## Behaviour summary
* **Two-person rule** (`ConfigApprovalService`): submit → DRAFT → PENDING_APPROVAL; decision by the submitter is always `403 SELF_APPROVAL_FORBIDDEN` (audited, committed independently); one decision per approver; row-locked; only PENDING items. REJECTED needs a reason. ≥3 rejections by one submitter in 24h also emits `CONFIG_REPEATED_REJECTIONS` (outcome `ALERT`) for SIEM.
* **Fail-closed policy evaluation**: ALLOW only if ≥1 matching policy and all matching are ALLOW. No match / ALLOW+DENY conflict / error / SEC failure / **audit failure** ⇒ DENY. A `POLICY_DECISION` audit event is always written.
* **Directory sync**: durable PENDING `dc_sync_job` in the mutating transaction → published after commit → scheduled retry with exponential backoff → DLQ `dc.sync.requested.dlq` + `DEAD_LETTERED` + `SYNC_DEAD_LETTERED` ALERT. `SENT` = broker-acked (LDAP sends no application ack). Idempotency: unique `(tenant_id, idempotency_key)`; Kafka `messageId` = job id.
* **LDAP consumer contract** (envelope JSON on `dc.sync.requested`): verify `signature` over `version\nmessageId\ntenantId\nentityType\nentityId\noperation\nissuedAt\nnonce\npayload`; reject if `issuedAt` outside the replay window; de-duplicate on `messageId`. (`entityId` is the string `null` for FULL_SYNC rows.)
* **Tenancy**: tenant comes only from the validated token. Not-found-in-my-tenant but exists-elsewhere ⇒ `403 TENANT_MISMATCH` (+ audit); truly unknown ⇒ 404.
* **RBAC**: `@RequiresRole` on every handler; a handler without it is denied. Roles used: `dc.user.read|write`, `dc.group.read|write`, `dc.policy.write`, `dc.policy.assign`, `dc.config.read|write|approve`, `dc.sync.trigger`, `dc.audit.read`.

## Unverified assumptions about shared libraries (could not compile here — no Maven/shared jars available)
Each is isolated so a fix is one small edit:
1. `TokenValidator` bean exists (autoconfigured by `texora-iam-client`) and `validate(String)` accepts the raw `Authorization` header — **taken from KNOWN-INTEGRATION-PITFALLS #1**. Not verified against the jar.
2. `TokenClaims` roles are plain strings equal to the role names above (e.g. `dc.config.approve`), and **`getSubject()` equals the `dc_user.id`** (used by the self-membership/self-assignment guards).
3. `com.texora.secops.starter.domain.TenantContext.getTenantId()` as a **static** accessor — used only in `security/TenantResolver` (cross-check; falls back to the token claim if it throws).
4. **`TenantFilteredRepository`: NOT used** (signature unknown). Own tenant-scoped repository interfaces instead.
5. **Shared `@Audited`/`AuditingAspect`: NOT used** (annotation attributes unknown; policy-decision events need an explicit outcome; LLD needs a queryable `dc_audit_event`). `texora-audit-sdk` is on the classpath; its aspect/auto-config may also publish — check for duplicate events on `dc.audit.events` and for any `KafkaTemplate` bean conflicts.
6. `PlatformException` / `GlobalExceptionHandler` (shared): DC does not import them. `DcException` + `DcGlobalExceptionHandler` (`@Component("dcGlobalExceptionHandler")`, catch-all logs) are DC-only. **Assumed** the shared advice does not need to run before DC's for DC exceptions; mine is `@Order(LOWEST_PRECEDENCE)`.
7. `spring.security.oauth2.resourceserver.jwt.issuer-uri` is how the iam-client's `JwtDecoder` is configured — property name is a guess; no `spring-boot-starter-security` is declared (only the interceptor authenticates `/api/v1/**`). If the starter brings Spring Security, a `SecurityFilterChain` may be needed.
8. SEC REST contract (`POST {dc.sec.base-url}/api/v1/policies/decisions` → `{"decision":"ALLOW|DENY"}`) is **a guess**; off by default (`dc.policy.sec-consult-enabled=false`). **mTLS to SEC/Kafka is not wired in code** (needs Boot SSL-bundle/mesh config).
9. DB role `dc_app` for the audit grants is assumed (grants are skipped if the role does not exist).
10. Maven: root property `spring-boot.version` (3.5.16) is referenced for the plugin pin; `springdoc 2.8.15` per pitfall #13.

## Not verified at all
Nothing here was compiled or run: no JDK compiler, Maven or Docker was available. Sources were syntax-checked with a Java parser and all project imports were cross-checked. Run `mvn verify` (Docker needed for `DcApiIT`, pitfall #9) and expect to fix some compile errors, most likely around items 1–3, 5–6 above.

## Runbook (D-008, short)
* **Sync jobs piling up PENDING** → Kafka unreachable; they retry automatically. `DEAD_LETTERED` rows + DLQ messages need manual replay after the cause is fixed.
* **Audit events missing in SIEM** → rows in `dc.dc_audit_publication` with `published_at IS NULL` are retried every 15 s.
* **Startup refuses with "must not run in production"** → the HRMS/signer stubs are still configured; real implementations are required for prod.
