package com.texora.secops.cgi.config;

import com.texora.secops.cgi.config.props.SecClientProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** RestClient for the Security Server. mTLS via a named Spring SSL bundle (service-to-service rule, B.6). */
@Configuration
public class SecClientConfig {

    @Bean("secRestClient")
    public RestClient secRestClient(SecClientProperties props, ObjectProvider<SslBundles> sslBundles) {
        HttpClient.Builder http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
        String bundleName = props.getSslBundle();
        if (bundleName != null && !bundleName.isBlank()) {
            // A configured-but-missing bundle fails startup rather than silently downgrading to plain HTTP.
            http.sslContext(sslBundles.getObject().getBundle(bundleName).createSslContext());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return RestClient.builder().baseUrl(props.getBaseUrl()).requestFactory(factory).build();
    }
}
