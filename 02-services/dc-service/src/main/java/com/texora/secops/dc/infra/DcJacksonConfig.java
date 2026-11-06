package com.texora.secops.dc.infra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DC's own ObjectMapper. The shared starter's mapper tolerates unknown JSON fields. DC must NOT: HRMS-owned
 * fields in a request body must give 422 HRMS_OWNED_FIELD and any other unknown field 400 MALFORMED_REQUEST
 * (see DcGlobalExceptionHandler). All other settings are copied from the shared JacksonConfig.
 */
@Configuration
public class DcJacksonConfig {

    @Bean(name = "dcObjectMapper")
    public ObjectMapper dcObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}