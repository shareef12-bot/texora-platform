package com.texora.secops.siem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated executors so a slow or wedged correlation rule cannot starve ingestion, and vice
 * versa - this is the concurrency half of Requirement 2 (rule isolation); the catch/timeout
 * half lives in {@code RuleExecutionSandbox}.
 */
@Configuration
public class AsyncConfig {

    /** One thread per in-flight rule evaluation, bounded, with its own queue - isolated from
     *  the ingestion consumer threads and from the HTTP request-handling threads. */
    @Bean
    public ExecutorService ruleExecutionExecutor(@Value("${siem.correlation.thread-pool-size:8}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "rule-sandbox-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public ThreadPoolTaskExecutor ingestionRetryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ingestion-retry-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("siem-scheduler-");
        return scheduler;
    }
}
