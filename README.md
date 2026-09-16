# Texora SecOps — Workspace Root

Build order:
  1. 01-platform-foundation  (build first — nothing else can start without it)
  2. 02-services/sso-service
  3. 02-services/sec-service
  4. 02-services/siem-service
  5. 02-services/ftp-service
  6. 02-services/dc-service
  7. 02-services/ldap-service
  8. 02-services/vpn-service
  9. 02-services/cgi-service

Full build plan and all 9 prompts (foundation + 8 services):
  00-docs/build-plan/Texora_SecOps_Build_Plan_and_Microservice_Prompts.md

Source documents (put your uploaded HLD/LLD/workbook files here):
  00-docs/hld/
  00-docs/lld/
  00-docs/brd-tdd-fsd/

Architecture Decision Records (resolve D-1 to D-5 before coding):
  00-docs/adr/

Rules for every service:
  - NO LOMBOK. Explicit getters/setters only. Enforced by
    05-scripts/check-no-lombok.sh in CI.
  - PostgreSQL is system of record. Redis/Kafka/OpenSearch/Object Storage
    used only for their specific documented purpose.
  - Every table has tenant_id. Every endpoint requires an SSO token.
  - The API Gateway/WAF is infrastructure (03-infra/k8s/ingress-waf),
    not a service you build in Spring Boot.
