package com.texora.secops.cgi.integration;

import com.texora.secops.cgi.security.PolicyDecision;
import com.texora.secops.cgi.security.PolicyDecisionClient;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.iam.token.TokenValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Needs Docker (Testcontainers) - run by failsafe, not surefire (KNOWN-INTEGRATION-PITFALLS #9: a "Could not find a
 * valid Docker environment" error locally is not a code bug). Real PostgreSQL + Flyway migrations; the SSO token
 * validator and SEC are replaced with mocks so identities and policy decisions are controlled by each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("texora").withUsername("cgi_test").withPassword("cgi_test_pw");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("cgi.audit.retry-interval-ms", () -> "3600000");
    }

    static final UUID TENANT_A = UUID.randomUUID();
    static final UUID TENANT_B = UUID.randomUUID();
    static final UUID ALICE = UUID.randomUUID();   // tenant A
    static final UUID BOB = UUID.randomUUID();     // tenant A
    static final UUID EVE = UUID.randomUUID();     // tenant B

    static final String ALL_ROLES_ALICE = "Bearer alice";
    static final String ALL_ROLES_BOB = "Bearer bob";
    static final String ALL_ROLES_EVE = "Bearer eve";
    static final String NO_ROLES_CAROL = "Bearer carol";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    TokenValidator tokenValidator;
    @MockitoBean
    PolicyDecisionClient policyClient;

    static final String[] ROLES = {"cgi.route.read", "cgi.route.write", "cgi.route.approve", "cgi.worker.read", "cgi.worker.write",
            "cgi.worker.approve", "cgi.timeout.read", "cgi.timeout.write", "cgi.config.read", "cgi.config.write", "cgi.policy.read", "cgi.audit.read"};

    void identities() {
        TokenClaims alice = claims(TENANT_A, ALICE, ROLES);
        TokenClaims bob = claims(TENANT_A, BOB, ROLES);
        TokenClaims eve = claims(TENANT_B, EVE, ROLES);
        TokenClaims carol = claims(TENANT_A, UUID.randomUUID());
        when(tokenValidator.validate(ALL_ROLES_ALICE)).thenReturn(alice);
        when(tokenValidator.validate(ALL_ROLES_BOB)).thenReturn(bob);
        when(tokenValidator.validate(ALL_ROLES_EVE)).thenReturn(eve);
        when(tokenValidator.validate(NO_ROLES_CAROL)).thenReturn(carol);
        when(policyClient.evaluate(any(), any(), any())).thenReturn(PolicyDecision.allow());
    }

    static TokenClaims claims(UUID tenant, UUID subject, String... roles) {
        TokenClaims c = mock(TokenClaims.class);
        when(c.getTenantId()).thenReturn(tenant);
        when(c.getSubject()).thenReturn(subject);
        when(c.getRoles()).thenReturn(Set.of(roles));
        return c;
    }
}
