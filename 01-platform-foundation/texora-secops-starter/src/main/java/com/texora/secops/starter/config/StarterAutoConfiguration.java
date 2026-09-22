package com.texora.secops.starter.config;

import com.texora.secops.starter.exception.GlobalExceptionHandler;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Optional;

@AutoConfiguration
@ConditionalOnWebApplication
@Import({JacksonConfig.class, ObservabilityConfig.class})
public class StarterAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler(Optional<Tracer> tracer) {
        return new GlobalExceptionHandler(tracer);
    }
}
