# ldap-service

> **Texora SecOps Platform â€” Service #6 of 8**
> LDAP Directory Service Â· Port `8087`

---

## âš ï¸ STUB MODE â€” NO REAL LDAP ENGINE CONNECTED

**ALL directory wire-protocol access is currently a no-op.**

The LDAP engine (Active Directory, OpenLDAP, FreeIPA, etc.) has **not been chosen yet** â€” see **ADR-D5**.
Every call that would touch a real directory server goes through the `LdapEngineAdapter` interface.
The active implementation is `StubLdapEngineAdapter`, which:

| Method | Stub behaviour |
|---|---|
| `ping()` | returns `false` |
| `search(...)` | returns empty `List.of()` |
| `addOrReplace(...)` | no-op |
| `modify(...)` | no-op |
| `delete(...)` | no-op |
| `applySchemaChange(...)` | no-op |
| `isValidFilter(...)` | returns `true` |
| `isValidSyntax(...)` | returns `true` |

**Every stub method logs a loud `WARN` line** â€” check your logs.
To wire a real engine: implement `LdapEngineAdapter`, annotate with `@Component("realLdapEngineAdapter")`, and the stub automatically deactivates (`@ConditionalOnMissingBean(name="realLdapEngineAdapter")`).

---

## Architecture

```
ldap-service (port 8087)
â”œâ”€â”€ security/
â”‚   â”œâ”€â”€ RbacInterceptor          â† calls texora-iam-client TokenValidator
â”‚   â”œâ”€â”€ EndpointRoleRegistry     â† pattern â†’ role constant mapping
â”‚   â””â”€â”€ PolicyDecisionClient     â† fail-closed REST call to SEC policy engine
â”œâ”€â”€ schema/                      â† Two-person approval workflow
â”‚   â”œâ”€â”€ service/SchemaApprovalService
â”‚   â””â”€â”€ controller/SchemaController
â”œâ”€â”€ bind/                        â† Secure bind config (TLS required, no plaintext)
â”œâ”€â”€ search/                      â† Directory search (blocked attributes stripped)
â”œâ”€â”€ sync/
â”‚   â”œâ”€â”€ client/LdapEngineAdapter â† *** THE ONLY PLACE that imports the LDAP client lib ***
â”‚   â”œâ”€â”€ client/StubLdapEngineAdapter
â”‚   â”œâ”€â”€ consumer/DcSyncEventConsumer (Kafka: dc.sync.requested)
â”‚   â””â”€â”€ service/DirectoryReconciliationService (@Scheduled retry)
â”œâ”€â”€ audit/                       â† Immutable audit log (no UPDATE/DELETE)
â”œâ”€â”€ policy/                      â† Policy check passthrough
â””â”€â”€ config/                      â† SecurityConfig, WebMvcConfig, KafkaConfig, OpenApiConfig
```

---

## Security design

| Concern | Enforcement |
|---|---|
| Authentication | `RbacInterceptor` validates JWT via `TokenValidator` (texora-iam-client) |
| Authorisation | `EndpointRoleRegistry` patternâ†’role mapping; missing mapping â†’ 403 (deny) |
| Two-person approval | `SchemaApprovalService.recordDecision()` â€” submitter â‰  approver, enforced in service layer |
| Fail-closed | Any `LdapEngineException` leaves status `PENDING_APPROVAL`; never silently applied |
| No secrets stored | `LdapBindConfig` stores no passwords; `LOGGER.warn` if `allowedSource` is unset |
| Sensitive attribute stripping | `DirectorySearchService` strips `userPassword`, `unicodePwd` etc. from all responses |
| Immutable audit log | `AuditEventRepository` has no `delete`/`update` methods; DB role has no `DELETE`/`UPDATE` grant |

---

## Kafka topics

| Topic | Direction | Purpose |
|---|---|---|
| `dc.sync.requested` | Consume | Trigger directory entry reconciliation |
| `ldap.audit.events` | Produce | Audit event fan-out |

---

## API endpoints

| Method | Path | Role required |
|---|---|---|
| `GET` | `/api/v1/schema/attribute-management` | `ldap.schema.read` |
| `POST` | `/api/v1/schema/attribute-management` | `ldap.schema.write` |
| `POST` | `/api/v1/schema/{id}/submit-for-approval` | `ldap.schema.write` |
| `POST` | `/api/v1/schema/{id}/decision` | `ldap.schema.approve` |
| `GET` | `/api/v1/secure-bind-configuration` | `ldap.bind.read` |
| `GET` | `/api/v1/secure-bind-configuration/{id}` | `ldap.bind.read` |
| `POST` | `/api/v1/secure-bind-configuration` | `ldap.bind.write` |
| `PUT` | `/api/v1/secure-bind-configuration/{id}` | `ldap.bind.write` |
| `POST` | `/api/v1/directory/search` | `ldap.search.read` |
| `POST` | `/api/v1/directory/sync` | `ldap.sync.trigger` |
| `GET` | `/api/v1/audit/events` | `ldap.audit.read` |
| `GET` | `/api/v1/policy/check` | `ldap.policy.read` |
| `GET` | `/api/v1/config` | `ldap.config.read` |
| `GET` | `/actuator/health` | public |
| `GET` | `/v3/api-docs` | public |

---

## Running locally

```bash
# Start dependencies
docker compose up -d postgres kafka

# Run service
./mvnw spring-boot:run -pl 02-services/ldap-service \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8087"

# Swagger UI
open http://localhost:8087/swagger-ui.html
```

---

## Running tests

```bash
# Unit tests only
./mvnw test -pl 02-services/ldap-service

# Integration tests (Docker required)
./mvnw verify -pl 02-services/ldap-service
```

---

## Replacing the stub (when engine is chosen)

1. Add the chosen LDAP client library to `pom.xml`.
2. Create `RealLdapEngineAdapter implements LdapEngineAdapter`.
3. Annotate with `@Component("realLdapEngineAdapter")`.
4. `StubLdapEngineAdapter` automatically deactivates â€” the `WARN` logs stop.
5. Delete `StubLdapEngineAdapter` once confidence is established.

---

## Database

Schema: `ldap` (PostgreSQL).
Flyway migrations: `src/main/resources/db/migration/`.
All tables carry `tenant_id UUID NOT NULL` â€” all queries are tenant-filtered.

---

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `LDAP_SERVER_PORT` | `8087` | HTTP listen port |
| `DB_URL` | â€” | PostgreSQL JDBC URL |
| `DB_USER` | â€” | DB username |
| `DB_PASSWORD` | â€” | DB password |
| `KAFKA_BOOTSTRAP` | â€” | Kafka bootstrap servers |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | â€” | IAM JWK URI |
| `LDAP_ENGINE_HOST` | `STUB` | Engine host (set to real host to enable) |
| `LDAP_ENGINE_PORT` | `389` | Engine port |
| `LDAP_SEC_POLICY_ENDPOINT` | â€” | SEC service policy URL |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | â€” | OpenTelemetry collector |
