package com.texora.secops.vpn.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean("secHttpClient")
    public HttpClient secHttpClient(MtlsHttpClientFactory factory, VpnProperties properties) {
        return factory.create(properties.getSec().getSslBundle(), properties.getSec().getTimeoutMs());
    }

    @Bean("dcHttpClient")
    public HttpClient dcHttpClient(MtlsHttpClientFactory factory, VpnProperties properties) {
        return factory.create(properties.getDc().getSslBundle(), properties.getDc().getTimeoutMs());
    }
}
