package com.texora.secops.starter.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Platform-standard Jackson / serialisation configuration.
 *
 * <ul>
 *   <li>Dates as ISO-8601 strings, never as timestamps</li>
 *   <li>Null fields excluded from responses</li>
 *   <li>Unknown JSON properties tolerated (forward-compatible)</li>
 *   <li>JavaTimeModule registered for {@link java.time.Instant}, etc.</li>
 * </ul>
 *
 * Annotated {@link ConditionalOnMissingBean} so individual services can
 * override by declaring their own {@code ObjectMapper} bean.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8+ date/time support
        mapper.registerModule(new JavaTimeModule());

        // Write Instants as ISO-8601, not as unix epoch numbers
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Do not fail on unknown JSON fields — forward compatibility
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Omit null fields from responses
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper;
    }
}
