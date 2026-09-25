# SEC Service — Build Notes

## ⚠️ FOLLOW-UP REQUIRED: Rewire SSO → Real SEC

SSO (build order #1) currently calls `texora-sec-stub` for all policy decisions.
Now that the real SEC is built, SSO must be rewired **before the integration phase**.

### Steps to rewire SSO

1. **Update SSO config** to point at real SEC endpoint:
   ```yaml
   # sso-service/src/main/resources/application.yml
   sec:
     base-url: http://sec-service.texora-secops.svc.cluster.local:8087
   ```

2. **Run SEC contract tests** from SSO:
   ```bash
   # Must pass against REAL SEC, not the stub (B.8 — gating item)
   mvn test -pl 01-services/sso-service -Dtest=SecContractTest
   ```

3. **Run all consuming service contract tests** against real SEC:
   - `dc-service` SecContractTest
   - `ldap-service` SecContractTest
   - `ftp-service` SecContractTest
   - `cgi-service` SecContractTest
   - `siem-service` SecContractTest
   - `vpn-service` SecContractTest

4. **Decommission `texora-sec-stub`** only after all seven pass.

5. **Verify default-deny** in SSO integration tests:
   - Force a policy miss → confirm SSO blocks the login (DENY)
   - Force SEC to return EVALUATION_ERROR → confirm SSO blocks (DENY)

This is a **gating item** before integration phase per B.8.

---

## What Was Built

### Package structure
```
com.texora.secops.sec
├── Application.java
├── policy/
│   ├── controller/  PolicyController, PolicyLifecycleController
│   ├── service/     PolicyEvaluationService (default deny), PolicyLifecycleService
│   ├── repository/  SecPolicyRepository, SecPolicyVersionRepository,
│   │                SecPolicyEvaluationLogRepository
│   ├── domain/      SecPolicy, SecPolicyVersion, SecPolicyEvaluationLog
│   └── dto/         PolicyEvaluationRequest, PolicyEvaluationResponse
├── secrets/
│   ├── controller/  SecretReferenceController, KeyRotationController
│   ├── service/     SecretReferenceService, KeyRotationService
│   ├── adapter/     VaultKmsAdapter  ← ONLY class that contacts vault/KMS
│   ├── repository/  SecSecretReferenceRepository, SecKeyRotationRecordRepository
│   ├── domain/      SecSecretReference, SecKeyRotationRecord
│   └── dto/         SecretReferenceResponse
├── approval/
│   ├── controller/  PrivilegedApprovalController
│   ├── service/     PrivilegedApprovalService (dual control, self-approval rejected)
│   ├── repository/  SecPrivilegedRequestRepository, SecApprovalDecisionRepository
│   └── domain/      SecPrivilegedRequest, SecApprovalDecision
├── audit/
│   ├── controller/  AuditSearchController
│   ├── service/     AuditChainService (hash chain + scheduled verification)
│   ├── aspect/      AuditingAspect (cross-cutting audit emission)
│   ├── repository/  SecAuditEventRepository
│   └── domain/      SecAuditEvent
├── security/
│   ├── RbacInterceptor        (SSO JWT → TenantContext)
│   ├── ServiceAuthInterceptor (mTLS CN verification)
│   └── TenantContext          (thread-local tenant, never from request params)
└── config/
    ├── SecurityConfig         (Spring Security, OAuth2 resource server)
    ├── WebMvcConfig           (interceptor registration)
    ├── GlobalExceptionHandler (standard error body per B.7)
    ├── SecConfigEntry         (domain entity for /api/v1/config)
    ├── SecConfigRepository
    └── ConfigController
```

### Key invariants enforced
| Invariant | Where enforced |
|---|---|
| Default deny (4 of 5 reason codes → DENY) | `PolicyEvaluationService.evaluate()` outer try/catch |
| DENY always wins over ALLOW | `PolicyEvaluationService.determineDecision()` |
| No secret material in SEC | `SecretReferenceService` returns paths only, `VaultKmsAdapter` never returns material |
| Owner-scope for secret references | `SecretReferenceService.resolveReference()` → 403 if not owner |
| Self-approval rejected | `PrivilegedApprovalService.recordDecision()` → 403 if approver==requester |
| Requests expire (never indefinitely actionable) | `PrivilegedApprovalService.expireStaleRequests()` scheduled job |
| Tenant always from token/cert, never request param | `RbacInterceptor` + `ServiceAuthInterceptor` |
| Audit rows never updated/deleted | DB grant comments in V1 migration; AuditChainService is append-only |
| No Lombok anywhere | Maven Enforcer plugin bans the dependency |
