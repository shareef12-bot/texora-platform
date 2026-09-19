package com.texora.secops.sso.session.service;

import com.texora.secops.sso.domain.SsoToken;
import com.texora.secops.sso.domain.TokenType;
import com.texora.secops.sso.repository.SsoTokenRepository;
import com.texora.secops.sso.session.cache.RedisSessionCache;
import com.texora.secops.sso.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private SsoTokenRepository tokenRepository;
    @Mock private RedisSessionCache redisSessionCache;

    private final UUID tenantId = UUID.randomUUID();
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        tokenService = new TokenService(tokenRepository, redisSessionCache);
        when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void issuesAccessRefreshAndIdTokensForASession() {
        UUID sessionId = UUID.randomUUID();

        List<SsoToken> tokens = tokenService.issueTokenSet(sessionId);

        assertThat(tokens).hasSize(3);
        assertThat(tokens.stream().map(SsoToken::getTokenType))
                .containsExactlyInAnyOrder(TokenType.ACCESS, TokenType.REFRESH, TokenType.ID);
        verify(tokenRepository, times(3)).save(any());
        verify(redisSessionCache, times(3)).putToken(any(), any(), any());
    }

    @Test
    void revokeAllForSessionMarksEachUnrevokedTokenRevokedAndEvictsFromRedis() {
        UUID sessionId = UUID.randomUUID();
        SsoToken access = new SsoToken(UUID.randomUUID(), tenantId, sessionId, TokenType.ACCESS,
                Instant.now(), Instant.now().plusSeconds(900), null);
        SsoToken alreadyRevoked = new SsoToken(UUID.randomUUID(), tenantId, sessionId, TokenType.REFRESH,
                Instant.now(), Instant.now().plusSeconds(900), Instant.now());
        when(tokenRepository.findAllBySessionIdAndTenantId(sessionId, tenantId))
                .thenReturn(List.of(access, alreadyRevoked));

        int revokedCount = tokenService.revokeAllForSession(sessionId);

        assertThat(revokedCount).isEqualTo(1);
        assertThat(access.isRevoked()).isTrue();
        verify(redisSessionCache, times(1)).evictToken(access.getId());
    }
}
