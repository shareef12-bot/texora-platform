package com.texora.secops.sso.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** Shared RestTemplate for the LDAP/DC/SEC adapters. mTLS is configured at the HttpClient level per environment. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
