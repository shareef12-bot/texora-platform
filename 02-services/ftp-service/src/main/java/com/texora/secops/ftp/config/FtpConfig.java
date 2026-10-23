package com.texora.secops.ftp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(FtpProperties.class)
public class FtpConfig {

    /**
     * Runs transfer orchestration off the request thread. If the queue is full the submit's event listener fails
     * (AbortPolicy) and the job stays SUBMITTED - it is never silently dropped or run inline.
     */
    @Bean(name = "ftpTransferExecutor")
    public Executor ftpTransferExecutor(FtpProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ftp-transfer-");
        executor.setCorePoolSize(props.getTransfer().getExecutorCoreSize());
        executor.setMaxPoolSize(props.getTransfer().getExecutorMaxSize());
        executor.setQueueCapacity(props.getTransfer().getExecutorQueueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** REST client for service-to-service calls to SEC. mTLS material comes from the JVM/SSL-bundle configuration. */
    @Bean(name = "secRestClient")
    public RestClient secRestClient(FtpProperties props) {
        Duration timeout = Duration.ofMillis(props.getSec().getTimeoutMs());
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(props.getSec().getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
