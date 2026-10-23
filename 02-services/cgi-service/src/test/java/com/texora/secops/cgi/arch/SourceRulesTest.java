package com.texora.secops.cgi.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural rules from the Shared Standards / pitfalls, checked on every build. */
class SourceRulesTest {

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> s = Files.walk(Path.of("src/main/java"))) {
            return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    @Test
    void noLombokAnywhere() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path p : mainSources()) {
            if (Files.readString(p).toLowerCase().contains("lombok")) {
                hits.add(p.toString());
            }
        }
        assertThat(Files.readString(Path.of("pom.xml")).toLowerCase()).doesNotContain("<artifactid>lombok</artifactid>");
        assertThat(hits).isEmpty();
    }

    @Test
    void onlyTheWorkerIsolationAdapterPackageMayStartProcessesOrContainers() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path p : mainSources()) {
            String path = p.toString().replace('\\', '/');
            if (path.contains("/execution/adapter/")) {
                continue;
            }
            String text = Files.readString(p);
            if (text.contains("ProcessBuilder") || text.contains("Runtime.getRuntime") || text.contains(".exec(")
                    || text.contains("com.github.dockerjava") || text.contains("io.fabric8")) {
                hits.add(path);
            }
        }
        assertThat(hits).as("files spawning processes outside the adapter").isEmpty();
    }

    @Test
    void noLocalStandInsForSharedLibraryClasses() throws IOException {
        Set<String> forbidden = Set.of("TenantContext.java", "BaseEntity.java", "PlatformException.java", "TokenValidator.java",
                "AuditingAspect.java", "TokenClaims.java");
        List<String> hits = mainSources().stream().map(p -> p.getFileName().toString()).filter(forbidden::contains).collect(Collectors.toList());
        assertThat(hits).isEmpty();
    }

    @Test
    void noSecretsOrCredentialDefaultsInConfig() throws IOException {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yml).doesNotContainPattern("(?i)password:\\s*[A-Za-z0-9]+\\s*$");
        assertThat(yml).contains("${CGI_DB_PASSWORD}");
    }

    @Test
    void globalExceptionHandlerHasUniqueBeanNameAndLogsInCatchAll() throws IOException {
        String text = Files.readString(Path.of("src/main/java/com/texora/secops/cgi/common/GlobalExceptionHandler.java"));
        assertThat(text).contains("@Component(\"cgiGlobalExceptionHandler\")");
        assertThat(text).contains("LOGGER.error(\"Unhandled exception");
    }
}
