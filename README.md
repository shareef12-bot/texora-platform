# Texora SecOps Platform — Foundation

**Build this before any of the eight microservices.**

---

## Modules

| Module | Description |
|---|---|
| `texora-secops-starter` | Shared Spring Boot auto-configuration: base entities, tenant filtering, exception handling, Jackson, OTel |
| `texora-iam-client` | OAuth2/OIDC token validation, `RbacInterceptor`, mTLS service-to-service helper |
| `texora-audit-sdk` | `@Audited` AOP annotation, async Kafka emission, queue-and-retry, durable local record |
| `texora-sec-stub` | Runnable SEC policy API stub — `POST /api/v1/policies/evaluate` |

---

## Prerequisites

- Java 21 LTS
- Maven 3.9+
- Docker (for Testcontainers in integration tests)

## Build

```bash
# Full build with all checks
mvn clean verify

# Skip tests (not recommended)
mvn clean package -DskipTests

# Build only the libraries (no runnable JAR)
mvn clean install -pl texora-secops-starter,texora-iam-client,texora-audit-sdk
```

## Run the SEC stub locally

```bash
mvn spring-boot:run -pl texora-sec-stub
# Stub listens on http://localhost:8080
# Contract: POST /api/v1/policies/evaluate
# Admin:    GET/POST/DELETE /api/v1/policies/stub/rules
```

---

## Platform Standards Summary

| Standard | Rule |
|---|---|
| **B.2** | NO Lombok anywhere — explicit getters, setters, constructors, equals, hashCode, toString |
| **B.3** | controller / service / repository / domain / dto / adapter / security / audit / config |
| **B.4** | Every external protocol behind exactly one adapter class |
| **B.5** | Every table has `tenant_id`; every query filtered by it; resolved from SSO token only |
| **B.6** | Fail closed on any ambiguity; audit every mutation; no secrets in DB/config |
| **B.7** | `application/json`, base path `/api/v1`, standard error body with `code` + `traceId` |

The Lombok prohibition is enforced in CI — the build **hard fails** if `lombok` appears in any dependency tree.

---

## CI/CD Pipeline Stages

1. **Lombok check** — hard fail on any Lombok dependency
2. **Build + unit tests** — all modules
3. **SAST** — SpotBugs (fail on HIGH+)
4. **Secret scan** — Gitleaks
5. **SCA** — OWASP Dependency-Check (fail on CVSS ≥ 7)
6. **SBOM** — CycloneDX BOM generation
7. **Container build + Trivy scan** — SEC stub image (non-root, Alpine)
8. **Publish** — to internal Nexus (main branch only)

---

## Using the Libraries in a Microservice

```xml
<!-- In your service pom.xml -->
<dependencies>
  <dependency>
    <groupId>com.texora.secops</groupId>
    <artifactId>texora-secops-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.texora.secops</groupId>
    <artifactId>texora-iam-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.texora.secops</groupId>
    <artifactId>texora-audit-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

```yaml
# application.yml minimum config
spring:
  application:
    name: my-service
  datasource:
    url: jdbc:postgresql://db:5432/mydb
  kafka:
    bootstrap-servers: kafka:9092

texora:
  iam:
    jwks-uri: https://sso.texora.internal/oauth2/jwks
    issuer-uri: https://sso.texora.internal
  sec:
    stub:
      url: http://texora-sec-stub:8080   # use stub in dev; real SEC in prod
```

```java
// Entity example — extends BaseEntity, NO LOMBOK
@Entity
@Table(name = "my_resource", schema = "myservice")
public class MyResource extends BaseEntity {

    @Id
    private UUID id;
    // ... explicit getters, setters, equals, hashCode, toString
}

// Repository — extends TenantFilteredRepository
public interface MyResourceRepository
    extends TenantFilteredRepository<MyResource, UUID> { }

// Service — use @Audited for mutations
@Service
public class MyResourceService {
    @Audited(action = "RESOURCE_CREATED", targetType = "MY_RESOURCE")
    public MyResourceDto create(CreateMyResourceRequest request) { ... }
}

// Controller — use @RequiresRole
@RestController
@RequestMapping("/api/v1/resources")
public class MyResourceController {
    @PostMapping
    @RequiresRole("RESOURCE_WRITE")
    public ResponseEntity<MyResourceDto> create(@RequestBody CreateMyResourceRequest req) { ... }
}
```
