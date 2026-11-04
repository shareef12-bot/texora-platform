package com.texora.secops.ftp.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guard rails for the non-negotiable rules. Cheap, dependency-free, and fails the build the moment
 * someone breaks one.
 */
class ArchitectureRulesTest {

    private static final Path MAIN = Paths.get("src/main/java");
    private static final Path TEST = Paths.get("src/test/java");

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void noLombokAnywhere() throws IOException {
        for (Path p : javaFiles(MAIN)) {
            assertThat(read(p).toLowerCase()).as(p.toString()).doesNotContain("lombok");
        }
        for (Path p : javaFiles(TEST)) {
            if (!p.getFileName().toString().equals("ArchitectureRulesTest.java")) {
                assertThat(read(p).toLowerCase()).as(p.toString()).doesNotContain("lombok");
            }
        }
        assertThat(read(Paths.get("pom.xml")).replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?s)<bannedDependencies>.*?</bannedDependencies>", "").toLowerCase()).doesNotContain("lombok");
    }

    @Test
    void onlyTheAdapterImportsTheSftpAndFtpsLibraries() throws IOException {
        for (Path p : javaFiles(MAIN)) {
            if (p.getFileName().toString().equals("SecureTransferEngineAdapter.java")) {
                continue;
            }
            String src = read(p);
            assertThat(src).as(p + " must not import the SFTP library").doesNotContain("import org.apache.sshd");
            assertThat(src).as(p + " must not import the FTPS library").doesNotContain("import org.apache.commons.net");
        }
    }

    @Test
    void noFileOrContentExecutionAnywhereInMainCode() throws IOException {
        String[] forbidden = {"ProcessBuilder", "Runtime.getRuntime", "ScriptEngine", "defineClass", "URLClassLoader",
                "Class.forName", "System.load", "GroovyShell"};
        for (Path p : javaFiles(MAIN)) {
            String src = read(p).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
            for (String f : forbidden) {
                assertThat(src).as(p + " must not use " + f).doesNotContain(f);
            }
        }
    }

    @Test
    void noLocalCopiesOfSharedLibraryClasses() throws IOException {
        List<String> banned = List.of("TenantContext.java", "BaseEntity.java", "PlatformException.java", "TokenValidator.java",
                "TokenClaims.java", "AuditingAspect.java", "TenantFilteredRepository.java");
        for (Path p : javaFiles(MAIN)) {
            assertThat(banned).as(p + " duplicates a shared-library class").doesNotContain(p.getFileName().toString());
        }
        // The service's own handler must have a unique bean name (PITFALLS #3)
        String handler = read(MAIN.resolve("com/texora/secops/ftp/exception/GlobalExceptionHandler.java"));
        assertThat(handler).contains("@Component(\"ftpGlobalExceptionHandler\")");
    }

    @Test
    void everyApiHandlerDeclaresARole() throws IOException {
        for (Path p : javaFiles(MAIN)) {
            if (!p.getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            String src = read(p);
            long mappings = java.util.regex.Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping").matcher(src).results().count();
            long roles = java.util.regex.Pattern.compile("@RequiresRole\\(").matcher(src).results().count();
            assertThat(roles).as(p + ": every mapped handler needs @RequiresRole").isEqualTo(mappings);
        }
    }

    @Test
    void migrationsGiveEveryTableATenantId() throws IOException {
        String sql = read(Paths.get("src/main/resources/db/migration/V1__ftp_schema.sql"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("CREATE TABLE ftp\\.(\\w+) \\((.*?)\\n\\);", java.util.regex.Pattern.DOTALL).matcher(sql);
        int tables = 0;
        while (m.find()) {
            tables++;
            assertThat(m.group(2)).as(m.group(1)).contains("tenant_id");
            assertThat(m.group(2)).as(m.group(1)).containsPattern("tenant_id\\s+UUID\\s+NOT NULL");
        }
        assertThat(tables).isGreaterThanOrEqualTo(8);
    }
}
