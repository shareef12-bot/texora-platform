# ftp-service — Texora SecOps Secure File Transfer

Managed file transfer control plane (SFTP/FTPS) with endpoint allowlisting, idempotent checksums, **fail-closed**
quarantine, two-person release, and an append-only audit trail. Platform step #4; the first service outside the Identity
Domain and the first to consume SSO-issued tokens.

## 1. Wire it into the platform

1. Copy this folder to `02-services/ftp-service/`.
2. Root `pom.xml` — add (PITFALLS #4A): `<module>02-services/ftp-service</module>`
3. This module's `<parent>` already carries `<relativePath>../../pom.xml</relativePath>` (PITFALLS #4B).
4. Run locally on **8084** (PITFALLS #12):  `--server.port=8084` (the yml default is `${FTP_SERVER_PORT:8084}`).
   Needs: PostgreSQL (`FTP_DB_*`, schema `ftp` is created by Flyway), Kafka, the sso-service JWKS (8081), SEC (8082 / stub 8083).
5. `mvn verify` runs unit tests, Testcontainers tests (Docker Desktop must be running locally — PITFALLS #9),
   JaCoCo (≥80 % on every `*/service/**` package) and the Lombok ban (enforcer). `scripts/check-no-lombok.sh` is the CI guard.

## 2. API (base `/api/v1`, bearer token required everywhere except `/actuator/health`)

| Endpoint | Role |
|---|---|
| `GET /transfer-jobs`, `GET /transfer-jobs/{id}` | `ftp.job.read` |
| `POST /transfer-jobs` (201), `POST /transfer-jobs/{id}/cancel` | `ftp.job.write` |
| `GET /checksum/audit-tracking` | `ftp.checksum.read` |
| `GET /quarantine-controls` / `POST /quarantine-controls` (201) | `ftp.quarantine.read` / `ftp.quarantine.write` |
| `POST /quarantine-controls/{id}/release` (`RELEASE` or `DESTROY`) | `ftp.quarantine.release` **and reviewer ≠ job initiator** |
| `GET /endpoints` / `POST /endpoints` (201) | `ftp.endpoint.read` / `ftp.endpoint.write` |
| `GET /config` / `POST /config` | `ftp.config.read` / `ftp.config.write` |
| `GET /policies` | `ftp.policy.read` |
| `GET /audit` | `ftp.audit.read` |
| `GET /actuator/health` | internal only (not routed via the gateway) |

Errors use the standard B.7 body. FTP-specific `code`s: `ENDPOINT_NOT_ALLOWLISTED` (422), `SELF_RELEASE_BLOCKED` (403),
`TENANT_ACCESS_DENIED` (403), `POLICY_DENIED` (403), `POLICY_UNAVAILABLE` (504), `INSPECTION_MANDATORY` (422),
`JOB_NOT_CANCELLABLE` / `QUARANTINE_ALREADY_RESOLVED` / `CHECKSUM_CONFLICT` (409).

## 3. How the four requirements are met

1. **Allowlisting** — `EndpointAllowlistService.requireAllowlisted` runs before the job row exists (422), is re-run by the
   orchestrator immediately before any connection, and rejections are audited. Registration also rejects loopback /
   link-local / any-local hosts and requires a pinned SSH host-key fingerprint for SFTP; the adapter re-checks the *resolved* address.
2. **Fail-closed quarantine** — only an explicit `CLEAN` verdict releases. `MALICIOUS`, `INCONCLUSIVE`, unavailable service,
   timeout, null/garbled verdict, or any exception → `QUARANTINED`. Nothing in the quarantine package is `@Scheduled`
   (asserted by a test). The sandbox is an external HTTP service; no code path opens, parses or executes file bytes
   (source-scan test forbids `ProcessBuilder`, `Runtime.getRuntime`, script engines, class loading).
3. **Two-person release** — `QuarantineService.release` compares reviewer subject with `initiator_sso_subject`; self-release/destroy is a
   403, audited as `DENIED` in an independent transaction, and changes nothing. Release delivers *after commit* via an event and
   re-hashes the staged file against the verified checksum first.
4. **Idempotent checksums** — one row per `(job, algorithm)` (DB `UNIQUE` + `INSERT … ON CONFLICT DO NOTHING`). Same values on
   retry = no-op; a different value against a verified record = `CHECKSUM_CONFLICT`, never an overwrite or duplicate.

Other controls: single adapter class (`SecureTransferEngineAdapter`) is the only importer of MINA SSHD / Commons Net (test-enforced);
FTPS uses full chain + hostname validation (Commons Net's default trust manager only checks dates, so it is replaced); plain FTP is unsupported;
no secrets in DB/config (endpoints hold an opaque `credential_ref`, material comes from SEC per attempt);
`ftp_audit_event` is append-only via trigger **and** grants **and** a repository with no update/delete methods;
composite `(id, tenant_id)` foreign keys make cross-tenant references impossible in the database;
`ftp_transfer_attempt` has one row per try (phase `FETCH`/`RELEASE`), including cancelled and crashed attempts.

## 4. Deviations from / additions to the written spec (please review)

* **Extra columns** (needed to make the flow work): `tenant_id` on every child table (B.5 says "every table"); endpoint `port`,
  `base_path`, `credential_ref`, `host_key_fingerprint`; job `file_name`, `require_inspection`, `expected_sha256`, `staging_uri`,
  `failure_reason`, `updated_at`; attempt `phase`; quarantine `resolution_note`; inspection `transfer_job_id`, `detail`, nullable
  `quarantine_record_id` (CLEAN results have no quarantine row); audit `actor_id` is `VARCHAR` so `SYSTEM:ftp-orchestrator` fits.
* **Extra table** `ftp_config` for `/config` (allow-listed, non-secret keys only).
* **Payload staging is a local volume** (NIO), not Object Storage — no object-store SDK/vendor was specified. `StagingStorage` is the single seam to replace.
* **Audit**: `FtpAuditTrail` (facade carrying the shared `@Audited`) writes the searchable `ftp_audit_event` row; Kafka emission is left to the shared Audit SDK. No local aspect.
* `GlobalExceptionHandler` has **no** catch-all `Exception` handler (it would shadow the starter's handlers); see class Javadoc.
* Spec lists only GET/POST for endpoints, so there is no revoke/update call; a non-allowlisted endpoint is created with `allowlisted:false`.
* `POST /config` returns 200 (upsert), not 201.

## 5. Known limitations

* **Single replica unless staging is a shared RWX volume** (see `helm/values.yaml`): a quarantined file lives on one pod's disk.
* No stuck-job sweeper: a job left `IN_PROGRESS`/`SUBMITTED` by a pod crash stays so (see runbook).
* Resume trusts the partial file; supply `expectedSha256` on integrity-critical jobs so a changed remote file is caught.
* IPv6 literal hosts unsupported. FTPS DNS-rebinding protection is weaker than SFTP (SFTP connects to the vetted IP).
* Not compiled or run where it was written — see section 7.

## 6. Runbook (deliverable D-008, starter)

* **Job stuck** (`SUBMITTED`/`IN_PROGRESS`/`RESUMED` long after the pod restarted): confirm no worker owns it, then
  `UPDATE ftp.ftp_transfer_job SET status='FAILED', failure_reason='ORPHANED', updated_at=now() WHERE id=… ;` and have the user resubmit
  (job rows may be updated; only `ftp_audit_event` is immutable). Remove `…/<tenant>/<job>/` from the staging volume.
* **Everything quarantines with `INSPECTION_UNAVAILABLE`**: `FTP_INSPECTION_BASE_URL` is blank/unreachable. This is by design; fix the sandbox, then
  reviewers release (a second person) or destroy.
* **Policy 504s on submit**: SEC unreachable → transfers are denied (fail closed). Check `FTP_SEC_BASE_URL` / mTLS.
* **Host key rotated** (`HOST_KEY_MISMATCH`): verify out-of-band, register a new endpoint with the new fingerprint.
* **Audit review**: `GET /api/v1/audit?action=FTP_QUARANTINE_RELEASE_DENIED` etc. Rows cannot be changed, only added.

## 7. Verification status

Written without Maven Central / a JDK compiler available. What *was* done: every Java file parsed with a Java parser (0 syntax
errors), every internal import / constructor arity / accessor cross-referenced against the project. What was **not** done:
compiling, running unit/integration tests, or any check against the real `texora-*` jars. Expect a short compile-fix pass —
the likely candidates are listed in the final answer ("places I was unsure").
