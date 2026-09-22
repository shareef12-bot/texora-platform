# texora-secops-starter

Shared Spring Boot auto-configuration library for all Texora SecOps microservices.

## What it provides

| Component | Class | Purpose |
|---|---|---|
| Base entity | `BaseEntity` | JPA superclass with `tenant_id`, `created_at`, `updated_at` |
| Tenant context | `TenantContext` | Thread-local store for the resolved tenant UUID |
| Tenant repository | `TenantFilteredRepository<T,ID>` | Base Spring Data interface; every query pre-filtered by tenant |
| Exception hierarchy | `PlatformException.*` | Typed exceptions mapping to 400/401/403/404/409/422/504 |
| Error handler | `GlobalExceptionHandler` | `@RestControllerAdvice` producing the standard `ApiError` body |
| Error body | `ApiError` | Standard response: `timestamp, status, error, code, message, traceId` |
| Jackson config | `JacksonConfig` | ISO-8601 dates, null exclusion, unknown property tolerance |
| JPA auditing | `JpaAuditingConfig` | Enables `@CreatedDate` / `@LastModifiedDate` |
| Observability | `ObservabilityConfig` | OpenTelemetry + Actuator auto-configuration |

## Dependency

```xml
<dependency>
  <groupId>com.texora.secops</groupId>
  <artifactId>texora-secops-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Auto-configuration activates automatically via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. No `@Import` needed.

## Entity pattern (NO LOMBOK)

```java
@Entity
@Table(name = "my_resource", schema = "myservice")
public class MyResource extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    protected MyResource() { /* JPA */ }

    public MyResource(UUID id, UUID tenantId, String name) {
        super(tenantId);   // sets tenant_id via BaseEntity
        this.id   = id;
        this.name = name;
    }

    public UUID getId()      { return id; }
    public String getName()  { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public boolean equals(Object other) {
        if (!sameClassAs(other)) return false;
        return nullSafeEquals(id, ((MyResource) other).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return "MyResource{id=" + id + "}"; }
}
```

## Repository pattern

```java
// Extend TenantFilteredRepository — never bare JpaRepository
@Repository
public interface MyResourceRepository
        extends TenantFilteredRepository<MyResource, UUID> {

    // Custom queries — always include tenantId param
    Optional<MyResource> findByNameAndTenantId(String name, UUID tenantId);
}
```

```java
// In service code — use convenience methods
Optional<MyResource> resource = repository.findForCurrentTenant(id);
List<MyResource> all = repository.findAllForCurrentTenant();
```

## TenantContext lifecycle

The IAM client (`texora-iam-client`) sets and clears `TenantContext` automatically for every authenticated request. If you are writing tests that call repository methods directly, set it manually:

```java
TenantContext.set(tenantId);
try {
    // ... call repository ...
} finally {
    TenantContext.clear();
}
```

## Exception usage

```java
// In service layer
throw PlatformException.ForbiddenException.rbacDenied("Caller lacks ADMIN role");
throw PlatformException.NotFoundException.forResource("User", userId);
throw new PlatformException.ConflictException("DUPLICATE_EMAIL", "Email already registered");
throw new PlatformException.UnprocessableException("TWO_PERSON_RULE", "Submitter cannot be approver");
```
