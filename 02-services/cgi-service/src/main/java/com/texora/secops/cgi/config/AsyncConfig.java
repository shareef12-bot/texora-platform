package com.texora.secops.cgi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounded executor for async Kafka forwarding. A full queue drops the task; the outbox retry job covers it. */
@Configuration
public class AsyncConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "auditForwardExecutor")
    public ThreadPoolTaskExecutor auditForwardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("cgi-audit-fwd-");
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler((task, pool) ->
                LOGGER.warn("Audit forward queue full - event stays in the local outbox for scheduled retry"));
        return executor;
    }
}
