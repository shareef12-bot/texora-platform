# QAP Service â€” Quality Automation Platform

**Texora Platform step #9** | Port `8090` | Schema `qap`

---

## What it does

| Part | Status | Description |
|------|--------|-------------|
| **A â€“ API test orchestration** | âœ… Full | Create suites, add test cases, trigger runs, store results, file deduplicated defect tickets |
| **B â€“ Selenium UI execution** | âš ï¸ Stub | Behind `SeleniumGridAdapter` interface with a loud stub (see below) |
| **B â€“ Issue tracker** | âš ï¸ Stub | Behind `IssueTrackerAdapter` interface with a loud stub (see below) |

---

## Part B stubs â€” NOT production code

### SeleniumGridAdapter
`StubSeleniumGridAdapter` is active under Spring profiles `default`, `selenium-stub`, `test`.
It logs a `WARN` with âš ï¸ and always returns a failed result.

**To wire a real implementation:**
1. Create a class that implements `SeleniumGridAdapter`.
2. Annotate it `@Component @Profile("production")` (or your production profile).
3. Remove `selenium-stub` from active profiles in production.

### IssueTrackerAdapter
`StubIssueTrackerAdapter` is active on all profiles.
It logs a `WARN` with âš ï¸ and returns `STUB-{counter}` as the ticket reference.

**To wire a real implementation:**
1. Create a class that implements `IssueTrackerAdapter`.
2. Mark `StubIssueTrackerAdapter` with `@Profile("!production")`.
3. Activate your real impl under `@Profile("production")`.

---

## Defect deduplication

The same failure (same service + endpoint + exception class) **never creates a second ticket**.
Deduplication is enforced at two levels:

1. **Application layer** â€” `DefectFilingService.processFailure()` queries by `failure_signature_hash + tenant_id` before calling the issue tracker.
2. **Database constraint** â€” `UNIQUE(failure_signature_hash, tenant_id)` on `qap.qap_defect` catches any concurrent race.

---

## Credential security

- `QapCredentialReference` stores **only** the SEC secret reference ID â€” never username, password, or token.
- `CredentialReferenceService` resolves credentials at point-of-use via `POST /api/v1/policies/evaluate` and holds the material in-memory only.
- `ResolvedCredential.toString()` redacts the password field.
- Credential material is **never** written to logs or persisted to database.

---

## Fail-closed execution

| Scenario | Outcome |
|----------|---------|
| Test runner throws any exception | `INCONCLUSIVE` |
| Outer `executeRun` throws | Whole run marked `FAILED` |
| Uncertain/ambiguous result | `INCONCLUSIVE` |
| Normal pass | `PASS` |

`PASSED` is only ever set by a clean, exception-free execution path.

---

## Running locally

```bash
# Start dependencies
docker compose up -d postgres kafka

# Run service
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=default \
  -Dserver.port=8090
```

Swagger UI: http://localhost:8090/swagger-ui.html

---

## Building

```bash
./mvnw clean package -DskipTests

# Unit tests only (no Docker required)
./mvnw test

# Integration tests (requires Docker)
./mvnw verify
```

---

## Helm deployment

```bash
helm upgrade --install qap-service ./helm/qap-service \
  --set image.tag=<version> \
  --namespace texora
```

---

## Assumed contracts (shared-library class names)

The following shared-library class names were assumed. If the real names differ, update the imports:

| Assumed | Used in | Notes |
|---------|---------|-------|
| `TenantContext` | Not used â€” header-based | Pulled from `X-Tenant-ID` header per LLD |
| `GlobalExceptionHandler` | `QapGlobalExceptionHandler` | Bean name `qapGlobalExceptionHandler` to avoid conflict |
| `PlatformException` | Not used | QAP defines its own exception hierarchy |
| `AuditingAspect` | Not used | QAP uses `KafkaTemplate` directly to `qap.audit.events` |
| `texora-secops-starter` | `pom.xml` | Assumed to provide Spring Boot autoconfiguration |
| `texora-iam-client` | `pom.xml` | JWT/OIDC resource server helpers |
| `texora-audit-sdk` | `pom.xml` | Shared audit event publishing |

---

## API reference

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/suites` | Create test suite |
| GET | `/api/v1/suites` | List suites (paginated) |
| GET | `/api/v1/suites/{id}` | Get suite |
| DELETE | `/api/v1/suites/{id}` | Delete suite |
| POST | `/api/v1/suites/{id}/cases` | Add test case |
| GET | `/api/v1/suites/{id}/cases` | List test cases |
| DELETE | `/api/v1/suites/{id}/cases/{caseId}` | Delete test case |
| POST | `/api/v1/runs` | Trigger test run |
| GET | `/api/v1/runs` | List runs |
| GET | `/api/v1/runs/{id}` | Get run |
| GET | `/api/v1/runs/{id}/results` | Get run results |
| GET | `/api/v1/defects` | List defects |
| GET | `/api/v1/defects/{id}` | Get defect |
| GET | `/api/v1/flaky` | List flaky test records |
| PATCH | `/api/v1/flaky/{testCaseId}/quarantine` | Set quarantine |
| POST | `/api/v1/credentials` | Register credential reference |
| GET | `/api/v1/credentials` | List credential references |
| GET | `/api/v1/audit` | Search audit events |
