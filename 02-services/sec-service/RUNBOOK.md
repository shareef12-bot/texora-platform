# SEC Service — Runbook (D-008)

## Overview
The Security Server (SEC) is the **central security control plane** for Texora SecOps.
Every other service calls it synchronously for policy decisions. A SEC outage
degrades into a **platform-wide denial of service** (all seven consuming services
fail closed).

**Owner:** Security Platform Engineering
**Namespace:** `texora-secops`
**Port:** 8087
**Min replicas:** 3 (HPA hard minimum — do not reduce)

---

## Critical Operational Invariants

1. **Default deny is not optional.** Any evaluation returning an error is a DENY.
   Never restart the service in a way that leaves it in a state where exceptions
   bypass evaluation.

2. **No secret material transits this service.** If you ever see what looks like
   a raw secret in a SEC log, escalate immediately as a P0 security incident.

3. **Audit chain must not be broken.** Do not `DELETE` or `UPDATE` rows in
   `sec.sec_audit_event`. Archive only by copying to cold storage — never by
   deleting from the live table.

---

## Health Checks

```bash
# Liveness
curl http://sec-service.texora-secops.svc.cluster.local:8087/actuator/health/liveness

# Readiness
curl http://sec-service.texora-secops.svc.cluster.local:8087/actuator/health/readiness

# Full health (requires internal network)
curl http://sec-service.texora-secops.svc.cluster.local:8087/actuator/health
```

**Expected:** `{"status":"UP"}`

---

## Key Metrics & Alerts

| Metric | Alert threshold | Meaning |
|---|---|---|
| `sec.policy.evaluation.deny{reason=EVALUATION_ERROR}` | > 0 for 5 min | Internal error causing deny — investigate immediately |
| `sec.policy.evaluation.deny{reason=NO_MATCHING_POLICY}` | Rising trend | Policy gaps — coverage review needed |
| `sec.audit.chain.broken` | == 1 | **P0: audit chain tampered** — incident response |
| `http.server.requests{uri=/api/v1/policies/evaluate,quantile=0.99}` | > 200ms | Latency SLA breach |
| HPA `currentReplicas` | < 3 | Pod crash loop — immediate action |

---

## Runbook: Audit Chain Break Detected (`sec.audit.chain.broken == 1`)

**Severity: P0 — Security Incident**

1. **Do not delete, update, or archive** any rows in `sec.sec_audit_event`.
2. Immediately notify the Security Incident Response team.
3. Identify the first broken sequence:
   ```sql
   -- Find the sequence_no where the break occurred from logs
   -- (AuditChainService logs the exact sequence_no on break detection)
   SELECT id, sequence_no, event_hash, prev_hash, occurred_at
   FROM sec.sec_audit_event
   WHERE sequence_no >= <break_sequence>
   ORDER BY sequence_no ASC
   LIMIT 20;
   ```
4. Preserve a forensic snapshot of the full table before any remediation.
5. The chain cannot be repaired without adding new events — coordinate with
   forensics before any action.

---

## Runbook: Policy Evaluation Latency Spike

1. Check p99 latency per calling service:
   ```sql
   SELECT caller_service, AVG(latency_ms), MAX(latency_ms), COUNT(*)
   FROM sec.sec_policy_evaluation_log
   WHERE evaluated_at > NOW() - INTERVAL '15 minutes'
   GROUP BY caller_service
   ORDER BY AVG(latency_ms) DESC;
   ```
2. Check for slow queries on `sec_policy_version`:
   ```sql
   EXPLAIN ANALYZE
   SELECT * FROM sec.sec_policy_version
   WHERE tenant_id = '<tenant>' AND status = 'ACTIVE';
   ```
3. Check index health: `idx_sec_policy_version_tenant_status`
4. If DB is healthy, check HPA — may need to scale up replicas.

---

## Runbook: Rising Default-Deny Rate (NO_MATCHING_POLICY)

A rising NO_MATCHING_POLICY rate means services are requesting evaluations
for resources/actions with no active policy.

1. Query the top uncovered resource/action pairs:
   ```sql
   SELECT resource, action, caller_service, COUNT(*) as deny_count
   FROM sec.sec_policy_evaluation_log
   WHERE reason = 'NO_MATCHING_POLICY'
     AND evaluated_at > NOW() - INTERVAL '1 hour'
   GROUP BY resource, action, caller_service
   ORDER BY deny_count DESC
   LIMIT 20;
   ```
2. Work with the owning team to create the missing policies.
3. New policies require dual-control approval before activation.

---

## Runbook: Privileged Request Queue Backup

If the approval queue depth metric is high:

1. List all pending requests:
   ```sql
   SELECT id, request_type, target_ref, requested_by, created_at, expires_at
   FROM sec.sec_privileged_request
   WHERE status = 'PENDING'
   ORDER BY created_at ASC;
   ```
2. Check for requests approaching expiry and notify approvers.
3. Requests expire automatically after the configured window (default 48h).
   Expired requests transition to EXPIRED — the operation must be re-requested.

---

## Database Maintenance

### What is safe
- `SELECT` — always safe
- `INSERT` into `sec_audit_event` — safe (append-only by design)
- `UPDATE`/`DELETE` on non-audit tables — coordinate with team

### What is NEVER safe
- `UPDATE` or `DELETE` on `sec.sec_audit_event` — this breaks the hash chain
- Truncating `sec.sec_policy_version` — version history must be preserved forever
- Dropping indexes without checking the evaluation latency impact first

### Archive strategy
To archive old audit events:
1. Copy rows to cold storage (e.g. S3 + Parquet) preserving all fields including hashes
2. Verify the archive is queryable and the chain is intact in the archive
3. Do NOT delete from the live table until forensic retention period expires
4. Never delete — mark as archived with a status column added via migration if needed

---

## Rewiring SSO to Use Real SEC (Follow-Up — Build Order)

**ACTION REQUIRED after SEC deployment:**

SSO service currently calls `texora-sec-stub` for policy decisions.
Once SEC is deployed and passing contract tests, SSO must be rewired:

1. Update `sso-service/src/main/resources/application.yml`:
   ```yaml
   sec:
     endpoint: http://sec-service.texora-secops.svc.cluster.local:8087
   ```
2. Run the SEC contract test suite against the real SEC:
   ```bash
   mvn test -pl sso-service -Dtest=SecContractTest -Dsec.real=true
   ```
3. All consuming service contract tests must pass against real SEC before
   the integration phase (B.8 — gating item).
4. Remove the `texora-sec-stub` deployment after all seven services pass
   their contract tests.

---

## Emergency: SEC Fully Unavailable

If SEC is completely unreachable, **all seven platform services fail closed**
(they are designed to DENY on SEC unavailability, not to permit).

Priority order:
1. Check pod status: `kubectl get pods -n texora-secops -l app=sec-service`
2. Check recent logs: `kubectl logs -n texora-secops -l app=sec-service --tail=200`
3. Check HPA: `kubectl get hpa -n texora-secops sec-service-hpa`
4. Check DB connectivity from pods
5. If DB is unavailable — SEC cannot start; fix DB first
6. Force rollout if image is bad:
   `kubectl rollout undo deployment/sec-service -n texora-secops`
7. Alert all service team leads: platform is in full-deny mode
