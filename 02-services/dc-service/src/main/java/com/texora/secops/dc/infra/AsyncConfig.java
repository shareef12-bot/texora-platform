package com.texora.secops.dc.infra;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

/** Executor for after-commit Kafka publication (keeps Kafka latency/outages off the request thread). */
@Configuration
public class AsyncConfig {

    public static final String DC_PUBLISH_EXECUTOR = "dcPublishExecutor";

    @Bean(name = DC_PUBLISH_EXECUTOR)
    public Executor dcPublishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("dc-publish-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "dcTxTemplate")
    @Qualifier("dcTxTemplate")
    public TransactionTemplate dcTxTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean(name = "dcNewTxTemplate")
    @Qualifier("dcNewTxTemplate")
    public TransactionTemplate dcNewTxTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
