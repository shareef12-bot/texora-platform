package com.texora.secops.vpn.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background jobs can be switched off (vpn.scheduling.enabled=false) so tests drive them deterministically. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "vpn.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService vpnRevalidationExecutor(VpnProperties properties) {
        return Executors.newFixedThreadPool(Math.max(1, properties.getSession().getWorkerThreads()));
    }
}
