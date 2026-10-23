# Control-plane uncertainty: explicit trigger definitions

VPN_TDD does not define when the control plane has "lost confidence" in an ACTIVE session. These are the definitions
implemented (all in `SessionControlService` / `SessionRevalidationScheduler`; all defaults in `application.yml`; the
combination is validated at startup by `VpnPropertiesValidator`). **They are proposals for Network Security
Engineering to confirm or tune; they are not from the TDD.**

Rule: confirmed -> session stays ACTIVE. Ambiguous / DENY / timeout / any exception -> revoke, mark REVOKED first, then tear down the tunnel.

| # | Trigger | Definition (default) | Revocation trigger code |
|---|---|---|---|
| 1 | SEC cannot confirm | Per-attempt timeout `vpn.sec.timeout-ms` = 2000 ms; up to `revalidation-attempts` = 2 attempts inside a hard `revalidation-deadline-ms` = 5000 ms. Timeout, transport error, non-200, malformed body or unknown value = cannot confirm. Only `{"decision":"ALLOW"}` confirms. | `SEC_UNAVAILABLE` (DENY -> `POLICY_DENY`) |
| 2 | Identity status stale | Identity status older than `identity-staleness-seconds` = 300 s is not trusted; it is refreshed from DC. Refresh fails = uncertain. DC says inactive/deleted = inactive. | `IDENTITY_UNCERTAIN` / `IDENTITY_INACTIVE` |
| 3 | Dependent policy changed | Policy disabled or replaced (approval of a replacement) -> every ACTIVE session on it gets `next_validation_at = now` in the same transaction (re-checked on the next 5 s tick, not the next 60 s cycle). Policy no longer APPROVED, or unassigned = revoke. | `POLICY_CHANGED` |
| 4 | Identity status change | A refresh that flips the subject to SUSPENDED marks all of that subject's ACTIVE sessions due. | `IDENTITY_INACTIVE` |
| 5 | Periodic re-validation failure | Every ACTIVE session re-validated every `revalidation-interval-seconds` = 60 s (per-tenant override 15..600). Any check that cannot be completed = revoke. | (per check) |
| 6 | Stalled re-validation (under-trigger guard) | Watchdog revokes any ACTIVE session whose `last_validated_at` is older than `max-unverified-seconds` = 300 s, independent of the re-validation loop. | `STALE_VALIDATION` |
| 7 | Inconsistent state | ACTIVE session with no dataplane binding after the establishment grace (30 s); subject record missing; policy conditions unparseable or time window invalid. | `STATE_INCONSISTENT` / `POLICY_AMBIGUOUS` |
| 8 | Unexpected error | Any exception thrown while re-validating. | `INTERNAL_ERROR` |
| 9 | Lifetime | `expires_at` reached (default 480 min, capped by policy `maxSessionMinutes`). Status EXPIRED. | `EXPIRY` |

## Balancing under- vs over-triggering

* **Over-triggering** (availability): a single transient SEC blip is absorbed by the in-deadline retry (trigger 1), and
  identity is only re-fetched every 5 minutes, not every cycle. A sustained SEC/DC outage *will* revoke sessions - that is the
  specified guarantee. Startup refuses `max-unverified-seconds < 2 x interval + claim-lease` so the watchdog cannot kill healthy sessions.
  Alerts: `VpnUncertaintyRevocationSpike`, `VpnRevocationsByTrigger` (`deploy/prometheus-rules.yaml`).
* **Under-triggering** (stale access): three independent mechanisms (event-driven, periodic, watchdog) plus teardown retry.
  Startup refuses `max-unverified-seconds > 6 x interval`. Alerts: `VpnSessionsNotBeingRevalidated`,
  `VpnWatchdogRevokedStaleSessions`, `VpnTeardownBacklog`.

## Known gap that code cannot close
If the **control plane itself is down**, nothing can revoke. The real dataplane adapter should enforce a short lease/TTL
on sessions so tunnels die on their own; this is a requirement to raise with the dataplane vendor (ADR-D5).
