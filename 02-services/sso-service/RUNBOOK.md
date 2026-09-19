# SSO / IAM Service — Runbook (deliverable D-008)

## Service summary
Single point of authentication trust for the Texora SecOps platform and IdP
for five products (ilmora, texora-jobs, taskorbit, hrms, crm — ADR D-3).
Every other service validates SSO-issued tokens; SSO itself validates
credentials via LDAP Directory (build order #2) and never implements its
own crypto (Spring Security OIDC/SAML only).

## Health & readiness
- `GET /actuator/health` — internal only, not exposed outside the cluster.
- `readiness` fails if PostgreSQL, Redis, or Kafka is unreachable.
- `liveness` fails only on unrecoverable JVM-level issues.

## Key dependencies
| Dependency | Failure mode if unavailable |
|---|---|
| PostgreSQL (`sso` schema) | Service fails to start (Flyway) / 500s on any DB-backed call |
| Redis | Session/token validation falls back to DB reads (slower, not broken) |
| Kafka (`sso.audit.events`) | Audit DB row still written; SIEM stream lags until Kafka recovers — `AuditingAspect` logs the publish failure loudly |
| LDAP Directory service | All logins fail closed (`BIND_FAILED`) — this is stubbed until LDAP (build order #2) exists |
| SEC policy API | All logins fail closed (`POLICY_DENIED`) per the fail-closed contract — this is intentional, not a bug |

## Common incidents

### "Everyone is getting denied at login"
1. Check SEC service health first — `ConsentPolicyService` denies whenever
   SEC returns no usable decision, by design. This is the #1 suspect.
2. Check LDAP Directory service health — `LdapBindClient` fails closed with
   `LDAP_UNAVAILABLE` on any transport error.
3. Check `sso_audit_event` for `outcome=DENIED` volume and the audit
   `payload` to see which step is failing (bind/MFA/consent).

### "Session revocation isn't taking effect immediately"
- Confirm Redis is reachable — `SessionService.revokeSession` evicts the
  Redis entry BEFORE marking the DB row REVOKED, so if Redis eviction is
  silently failing, downstream services may still see a cached ACTIVE
  session until its TTL expires.

### "New product isn't showing up as a relying party"
- Confirm ADR D-3 registration checklist is complete for that product_id
  and that `POST /api/v1/application-registration` succeeded — check
  `sso_application` for the row and `sso_oidc_saml_config` for its
  federation config.

## Rollback
Standard Helm rollback: `helm rollback sso-service <revision> -n texora-secops`.
Sessions/tokens are unaffected by an app rollback (state lives in
PostgreSQL/Redis, not in-process).

## On-call escalation
Security Platform Engineering owns this service (per the prompt header).
Any suspected auth bypass or fail-open behavior is a Sev-1 — page
immediately, do not wait for business hours.
