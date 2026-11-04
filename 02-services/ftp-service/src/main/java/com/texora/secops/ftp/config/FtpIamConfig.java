package com.texora.secops.ftp.config;

import com.texora.secops.iam.token.TokenValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Wires the SHARED TokenValidator (constructor takes a JwtDecoder) unless texora-iam-client already provides it. */
@Configuration
public class FtpIamConfig {

    @Bean
    @ConditionalOnMissingBean(TokenValidator.class)
    public TokenValidator tokenValidator(JwtDecoder jwtDecoder) {
        return new TokenValidator(jwtDecoder);
    }
}
