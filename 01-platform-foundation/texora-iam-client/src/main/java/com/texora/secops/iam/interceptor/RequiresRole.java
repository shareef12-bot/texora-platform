package com.texora.secops.iam.interceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the roles required to invoke a controller handler method or class.
 *
 * <p>Enforced by {@link RbacInterceptor} <em>before</em> the handler executes.
 * Place on a method to require roles for that endpoint; place on the class to
 * apply to all methods in the controller (method-level annotation takes
 * precedence when both are present).
 *
 * <p>Semantics: the caller must hold <strong>at least one</strong> of the
 * listed roles (OR logic). For AND logic, chain multiple interceptor checks
 * in the service layer.
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/users")
 * public class UserController {
 *
 *     @GetMapping("/{id}")
 *     @RequiresRole({"USER_READ", "ADMIN"})
 *     public ResponseEntity<UserDto> getUser(@PathVariable UUID id) { ... }
 *
 *     @DeleteMapping("/{id}")
 *     @RequiresRole("ADMIN")
 *     public ResponseEntity<Void> deleteUser(@PathVariable UUID id) { ... }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRole {

    /**
     * One or more role names. The caller must hold at least one.
     * Role names are case-sensitive and must match the {@code roles}
     * claim in the SSO token exactly.
     */
    String[] value();
}
