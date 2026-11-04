package com.texora.secops.cgi.arch;

import com.texora.secops.cgi.repository.SystemWide;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** B.5: tenant filtering is enforced structurally, not by developer memory. */
class RepositoryTenantScopeTest {

    private static final Set<String> WRITE_METHODS = Set.of("save", "saveAndFlush");

    @Test
    void everyRepositoryMethodIsTenantScopedOrExplicitlySystemWide() throws Exception {
        Path dir = Path.of("src/main/java/com/texora/secops/cgi/repository");
        List<String> violations = new ArrayList<>();
        int repositories = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith("Repository.java"))::iterator) {
                repositories++;
                String name = p.getFileName().toString().replace(".java", "");
                Class<?> type = Class.forName("com.texora.secops.cgi.repository." + name);
                assertThat(type.isInterface()).isTrue();
                // must NOT inherit CrudRepository/JpaRepository (their findAll/findById/deleteAll are unfiltered)
                for (Class<?> parent : type.getInterfaces()) {
                    assertThat(parent.getSimpleName()).as(name + " parent").isEqualTo("Repository");
                }
                for (Method m : type.getDeclaredMethods()) {
                    boolean ok = m.getName().contains("TenantId") || WRITE_METHODS.contains(m.getName()) || m.isAnnotationPresent(SystemWide.class);
                    if (!ok) {
                        violations.add(name + "#" + m.getName());
                    }
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertThat(repositories).isGreaterThanOrEqualTo(9);
        assertThat(violations).as("repository methods without tenant scoping").isEmpty();
    }
}
