# SOC/SIEM Log Server - Runbook

## Service summary

Platform-wide audit event consumer: ingests from 12 Kafka topics + HTTP/Syslog,
normalizes, correlates against approved detection rules, and raises alerts.
Namespace: `secops`. Port `8086`. Health: `/actuator/health`. Metrics:
`/actuator/prometheus`.

## Key metrics and alert thresholds

| Metric | What it means | Suggested page threshold |
|---|---|---|
| `siem_ingestion_dlq_depth` | Rows that exhausted retries and could not be processed | > 0 for 15+ min: investigate; > 100: page |
| `siem_ingestion_lag` | Time from receipt to successful processing | p99 > 5 min: investigate |
| `siem_correlation_rule_failures_total` | Rule evaluations that threw/timed out | Any sustained increase: a specific rule is likely broken - see below |
| `siem_ingestion_processed_total` vs incoming Kafka consumer lag | Whether ingestion is keeping up | Consumer lag growing steadily: scale ingestion or investigate backpressure |

## Runbook: DLQ depth alert

1. Query `siem_ingestion_log` where `status = 'DLQ'`, grouped by `ingestion_source_id`
   and `error_summary`, to find the dominant failure pattern.
2. Common causes:
   - A normalization profile mismatch (upstream service changed its audit-event
     shape) - fix the relevant `NormalizationProfile` and redeploy.
   - The event corpus store is down/unreachable - check `EventCorpusSearchAdapter`
     logs and the store's own health.
   - A malformed/corrupt payload from one specific source - check with that
     service's on-call.
3. DLQ rows are **never auto-deleted**. Once the root cause is fixed, they must be
   explicitly replayed (there is no built-in "replay DLQ" endpoint in this build -
   add one, or replay by re-publishing the row's `raw_payload` to the source topic,
   before relying on this in production for a source with a low error tolerance).
4. Because retention on the event corpus inherits the highest sensitivity/longest
   compliance window of any source it contains, do not delete DLQ rows as a
   "cleanup" step - they may be the only record of an event that never made it
   into the corpus.

## Runbook: correlation rule misbehaving

1. Check `siem_correlation_rule_failures_total` broken down by rule (add a
   `rule_id` tag if your metrics backend supports high-cardinality labels, or
   correlate via the `RuleExecutionSandbox` error logs, which include the rule id).
2. A single bad rule cannot affect other rules' evaluation or the ingestion
   pipeline (Requirement 2) - so this is never an "all hands" incident, but the
   specific detection covered by the broken rule is silently not firing while
   broken.
3. To disable it immediately: submit a disable request
   (`POST /api/v1/correlation-rules` with `targetRuleId` set) and have a second
   analyst approve it via `POST /api/v1/correlation-rules/{id}/decision`. This is
   two-person-approved by design, even for an emergency disable - loop in a second
   analyst rather than bypassing the control.

## Runbook: rule approval questions

- A rule (new or disable-request) sits in `PENDING_APPROVAL` until a **different**
  user calls the decision endpoint. The submitter's own attempt to approve is
  rejected with 403 and is itself recorded to `siem_rule_change_audit` with
  `change_type = REJECTED` - "submitter tried to self-approve" is visible in that
  audit trail, which is worth alerting on if it happens repeatedly (possible
  process confusion or a compromised account trying to force through a bad rule).
- Every decision - approve or reject - is audited regardless of outcome. To find
  who approved/rejected a specific detection change, query
  `GET /api/v1/correlation-rules/{id}/history`.

## Runbook: analyst reports "search returns nothing" / cross-tenant 403

- If the response has `degraded: true`, the event corpus backend is impaired -
  this is an availability incident, not a permissions one. Check
  `EventCorpusSearchAdapter`'s target (`siem.search.backend.base-url`) health.
- If the response is `403 POLICY_DENIED`, SEC's live policy check denied the
  query - check SEC's own health/logs; SIEM fails closed on any SEC error, so a
  SEC outage will present as search denials for everyone, not silently permit
  queries.
- If the response is `403 RBAC_DENIED` with a `tenantScope` mismatch in the
  request, this is expected behavior (Requirement 3) - the analyst is trying to
  query outside their tenant, whether by mistake or otherwise. Repeated
  occurrences from the same account are worth escalating to SOC leadership.

## Scaling

- Ingestion (Kafka consumers) and search are designed to scale independently
  (non-functional requirement) - the Kubernetes HPA in `helm/siem-service` scales
  the whole pod on CPU by default; if ingestion and search load diverge
  significantly in practice, split them into separate deployments sharing the
  same JAR with different Spring profiles/`@KafkaListener` enablement, rather than
  scaling the combined deployment for one side's load.
- The correlation engine's sliding-window state (`CorrelationWindowStore`) is
  **per-instance, in-memory**. Scaling ingestion beyond one replica without also
  addressing this means a rule's count could split across replicas and
  under-trigger. This must be fixed (see README assumption 6) before running more
  than one replica in a way that depends on multi-event correlation rules being
  accurate.

## Deploy

```bash
helm upgrade --install siem-service ./helm/siem-service -n secops --create-namespace
```

Requires `siem-db-credentials` (keys `username`, `password`, `jdbc-url`) and
optionally `siem-search-credentials` secrets to exist in the `secops` namespace
before install - these are intentionally not created by this chart (B.6.6: no
secrets in config; retrieve references from SEC / the platform's secret manager).

## Rollback

Standard Helm rollback (`helm rollback siem-service`); Flyway migrations in this
service are additive-only in V1 - no destructive migration exists yet, so a
rollback of the application version does not require a corresponding DB rollback.
Confirm this remains true before merging any future migration that drops or
alters a column.

## Build/integration notes (platform reactor)

- This module must be listed in the root `pom.xml` (`<module>02-services/siem-service</module>`)
  and builds against `texora-secops-starter`, `texora-audit-sdk`, `texora-iam-client`.
- Authentication: every admin/analyst endpoint validates the bearer token through
  `texora-iam-client`'s `TokenValidator`. Ingestion endpoints (`/api/v1/ingest/**`) are mTLS, not SSO.
- Startup `BeanDefinitionOverrideException`: a same-named shared bean exists - rename the local
  one with a `siem` prefix (as already done for the exception handler and audit beans).
- `NoSuchMethodError` at runtime from springdoc: check its version against Spring Boot 3.5.x.
- "Could not find a valid Docker environment" in `SiemIntegrationTest`: start Docker Desktop.
