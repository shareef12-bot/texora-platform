package com.texora.secops.starter.repository;

import com.texora.secops.starter.domain.TenantContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository interface for all Texora SecOps entities.
 *
 * <p>All finder methods defined here are pre-filtered by the current
 * {@link TenantContext#get()} value, enforcing row-level tenant isolation
 * (standard B.5). Microservice repositories extend this interface.
 *
 * <p>The unscoped {@link #findById(Object)}, {@link #findAll()} and
 * {@link #deleteById(Object)} inherited from {@link JpaRepository} are
 * overridden below to always throw {@link UnsupportedOperationException}.
 * This makes bypassing tenant filtering a compile-time-available but
 * runtime-impossible operation, rather than something merely discouraged
 * in a comment. Use {@link #findByIdAndTenantId(Object, UUID)},
 * {@link #findAllByTenantId(UUID)}, {@link #findForCurrentTenant(Object)},
 * or {@link #findAllForCurrentTenant()} instead.
 *
 * <p>This interface is marked {@code @NoRepositoryBean} to prevent Spring Data
 * from trying to instantiate it directly.
 *
 * @param <T>  entity type (must extend {@link com.texora.secops.starter.domain.BaseEntity})
 * @param <ID> primary-key type
 */
@NoRepositoryBean
public interface TenantFilteredRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * Disabled. {@code findById} performs no tenant check and is structurally
     * blocked here to prevent cross-tenant access.
     *
     * @throws UnsupportedOperationException always
     * @see #findByIdAndTenantId(Object, UUID)
     * @see #findForCurrentTenant(Object)
     */
    @Override
    default Optional<T> findById(ID id) {
        throw new UnsupportedOperationException(
            "findById(ID) bypasses tenant filtering; use findByIdAndTenantId(ID, UUID) "
                + "or findForCurrentTenant(ID) instead.");
    }

    /**
     * Disabled. {@code findAll} performs no tenant check and is structurally
     * blocked here to prevent cross-tenant access.
     *
     * @throws UnsupportedOperationException always
     * @see #findAllByTenantId(UUID)
     * @see #findAllForCurrentTenant()
     */
    @Override
    default List<T> findAll() {
        throw new UnsupportedOperationException(
            "findAll() bypasses tenant filtering; use findAllByTenantId(UUID) "
                + "or findAllForCurrentTenant() instead.");
    }

    /**
     * Disabled. {@code deleteById} performs no tenant check and is structurally
     * blocked here to prevent cross-tenant access.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    default void deleteById(ID id) {
        throw new UnsupportedOperationException(
            "deleteById(ID) bypasses tenant filtering; look up the entity via "
                + "findByIdAndTenantId(ID, UUID) or findForCurrentTenant(ID) first, "
                + "verify tenant ownership, then delete it explicitly.");
    }

    /**
     * Finds an entity by primary key AND tenant ID.
     * Always prefer this over bare {@code findById} to prevent cross-tenant access.
     * Returns {@link Optional#empty()} (not 403) at this layer — the service layer
     * must convert a missing result for a valid ID into a 403, not a 404.
     */
    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);

    /**
     * Returns all entities belonging to the given tenant.
     */
    List<T> findAllByTenantId(UUID tenantId);

    /**
     * Convenience: looks up by ID for the currently-authenticated tenant.
     * Delegates to {@link #findByIdAndTenantId(Object, UUID)} using
     * {@link TenantContext#get()}.
     */
    default Optional<T> findForCurrentTenant(ID id) {
        return findByIdAndTenantId(id, TenantContext.get());
    }

    /**
     * Convenience: returns all entities for the currently-authenticated tenant.
     * Delegates to {@link #findAllByTenantId(UUID)} using {@link TenantContext#get()}.
     */
    default List<T> findAllForCurrentTenant() {
        return findAllByTenantId(TenantContext.get());
    }
}
