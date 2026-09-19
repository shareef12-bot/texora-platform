# sso-service

SSO / IAM Service — Texora SecOps platform, **build order #1**. Identity
Domain. Built per `03-PROMPT-1-sso-service.md`, `01-SHARED-STANDARDS.md`,
`Texora_SecOps_SSO_LLD.docx`, and `ADR-D3-product-registry.md`.

## Module layout
This is `02-services/sso-service/`, matching the Platform Foundation module
pattern. Its `pom.xml` `<parent>` points at
`../../pom.xml` (`com.texora.secops:texora-secops-platform:1.0.0-SNAPSHOT`)
via `<relativePath>`, and depends on the four existing Platform Foundation
artifacts:

- `texora-secops-starter`
- `texora-iam-client`
- `texora-audit-sdk`
- `texora-sec-stub` (test scope — now correctly defaults to DENY on no match)

**This deliverable assumes those four artifacts already exist at
`com.texora.secops:*:1.0.0-SNAPSHOT`** and are resolvable from wherever this
module is built (local Maven repo or your internal Nexus/Artifactory), per
the prompt. It does not re-create them.

## Build-time assumptions about Platform Foundation's public API
Since the actual Platform Foundation source wasn't provided alongside this
prompt, this build assumes reasonable, narrow surface area from it:

- `texora-iam-client` exposes `com.texora.secops.iam.IamTokenClient` with
  `currentClaims(SecurityContext)` and `callerHasRole(SecurityContext, String)`,
  a `TokenClaims` record/class with `tenantId()`, and a
  `com.texora.secops.iam.MfaVerifier.verify(UUID, String, String)` static
  method (wrapped locally in `MfaVerificationClient` so it stays unit-testable).
- `texora-audit-sdk` exposes `com.texora.secops.audit.AuditSdk` with a
  `publish(String topic, String key, String jsonPayload)` method.

If your actual Platform Foundation API differs, the touch points are
isolated to: `tenant/TenantResolvingFilter.java`, `security/RbacInterceptor.java`,
`authn/client/MfaVerificationClient.java`, and `audit/aspect/AuditingAspect.java`
— nothing else in the codebase references those libraries directly.

## What's implemented
- All 8 database tables from the LLD (`sso_application` … `sso_audit_event`),
  every one `tenant_id`-scoped, as explicit-getter/setter JPA entities (no Lombok).
- Package structure exactly as specified in the prompt (`federation/`,
  `application/`, `session/`, `authn/`, `consent/`, `audit/`, `security/`,
  `repository/`).
- The login flow implemented exactly as the 6 numbered steps in the prompt,
  in `LoginOrchestrationService`, with an audit event emitted for every
  outcome via the `@Audited` + `AuditingAspect` pair (not left to convention).
- Fail-closed consent/policy in `ConsentPolicyService` — denies on missing
  consent, on SEC returning no decision, **and** on SEC returning an explicit
  DENY (this distinction is what `ConsentPolicyServiceTest` covers, per your
  note about the SEC stub fix).
- RBAC enforced per-endpoint via `RbacInterceptor`, wired in *before* any
  handler runs (`WebMvcConfig`), covering every endpoint/role pair in the
  prompt's API table.
- Session revocation invalidates Redis immediately, then persists REVOKED,
  then the caller separately revokes all tokens for that session — matching
  "Session revocation must invalidate matching Redis entries IMMEDIATELY".
- Flyway migration (`V1__init_sso_schema.sql`) with all 8 tables, tenant_id
  indexes, and a `REVOKE UPDATE, DELETE` statement on the audit table.
- Maven Enforcer rule that fails the build if `lombok` ever enters the
  dependency tree (belt-and-braces alongside the CI grep command from the
  shared standards).
- Unit tests for the two most safety-critical pieces: fail-closed
  consent/policy (`ConsentPolicyServiceTest` — explicitly covers the
  DENY-from-SEC path, not just ALLOW) and the full login orchestration
  (`LoginOrchestrationServiceTest` — bind failure, MFA-not-enrolled,
  MFA-failed, policy-denied, and success paths).
- Dockerfile (non-root, multi-stage) and a Helm chart skeleton.

## What's intentionally stubbed / left for you to wire up
- `LdapBindClient` and `DcIdentityClient` are stub-mode by default
  (`LDAP_SERVICE_STUB_MODE=true` / `DC_SERVICE_STUB_MODE=true`) and fail
  closed — per the prompt, "Until LDAP exists, stub this." Flip the stub
  flags and set the base URLs once LDAP Directory (build order #2) and DC
  are deployed.
- The actual OIDC/SAML relying-party registrations for the five products
  (`application.yml`'s `spring.security.oauth2.authorizationserver` /
  `saml2.relyingparty.registration` blocks) are placeholders — populate them
  once ADR D-3's open questions (final product codenames, per-product
  redirect URIs, technical contacts) are answered.
- `ApplicationRegistrationControllerIT` is `@Disabled` by default because it
  needs the real Platform Foundation JARs resolvable in this sandbox; it's a
  real Testcontainers-backed test, just gated off here.
- Coverage is currently concentrated on the fail-closed and login-flow logic
  the prompt called out explicitly. `≥80% service-layer coverage` (shared
  standards B.8) means more service-level unit tests than are included here
  — the highest-risk ones are done; `SessionService`, `IdentityProviderAdapterService`,
  and `AuditSearchController` still need their own test classes to hit the bar.

## OAuth2 Authorization Server wiring (login flow now actually reachable over HTTP)
- `SsoRegisteredClientRepository` (`federation/oauth/`) backs Spring's
  Authorization Server with `sso_application` + `sso_oidc_saml_config` rows,
  so registered applications are real OAuth2 clients at `/oauth2/authorize`
  and `/oauth2/token`. It's read-only — `save()` is unsupported on purpose,
  since `POST /api/v1/application-registration` is the system of record.
  Relying parties are treated as **public clients** (no `client_secret`
  column exists in the schema) — PKCE (S256) is required instead.
- `LoginOrchestrationService.login()` is now invoked directly from
  `LoginController` (`authn/controller/`), which Spring's Authorization
  Server redirects to when `/oauth2/authorize` hits an unauthenticated
  request. There is **no** `AuthenticationProvider`/`AuthenticationManager`
  in this path — `LoginController` treats `LoginOrchestrationService`'s
  result as the authentication decision itself and writes the
  `SecurityContext` directly on success.
- Because `/login` runs before any SSO token exists, `LoginOrchestrationService`
  now resolves tenant from the **target application** (step 0 of the login
  flow) rather than from `TenantContext` pre-set by a bearer token — this is
  a real change from the previous version and is covered by
  `LoginOrchestrationServiceTest#deniesLoginWhenApplicationIsUnknown` /
  `...IsSuspended`. `TenantContextCleanupFilter` guarantees the ThreadLocal
  never leaks across pooled threads on this path.
- `OAuth2AuthorizeFlowIT` drives a full `/oauth2/authorize` → `/login` →
  `/oauth2/authorize` → `/oauth2/token` round trip against a **really
  registered** test application, with only the three external systems that
  don't exist in a test sandbox (LDAP, MFA factor verification, SEC policy)
  swapped for fixed-answer doubles — everything else, including
  `LoginOrchestrationService`, `SsoRegisteredClientRepository`, and Spring's
  own code issuance/signing, runs unmocked.

### Known limitations in this wiring (flagged, not hidden)
- **Signing key is ephemeral and in-process** (`AuthorizationServerConfig.jwkSource()`).
  This is what makes `/oauth2/authorize` actually runnable at all right now,
  but it is explicitly **not** production-ready — real signing material must
  come from SEC/KMS via the existing `signing_cert_ref` pattern. This is the
  one place in the codebase that isn't wired to a real key source yet.
- **CSRF is disabled on `/login`** for simplicity of the raw Thymeleaf form.
  A real deployment should add a proper CSRF-protected login template.
- **Token claims don't yet carry `tenant_id` / product scope** (LLD
  requirement under MULTI-PRODUCT REQUIREMENT). Wiring an
  `OAuth2TokenCustomizer<JwtEncodingContext>` bean that reads the
  `sso_session` created during login and adds those claims is the natural
  next step — not included here to keep this change scoped to the four
  items asked for.
- `OAuth2AuthorizeFlowIT` is `@Disabled` for the same Platform Foundation
  dependency-resolution reason as the other integration test — the flow
  itself is complete, not stubbed out.


## Local build
```
# from the texora-secops-platform repo root, once Platform Foundation exists:
mvn -pl 02-services/sso-service -am package
```
