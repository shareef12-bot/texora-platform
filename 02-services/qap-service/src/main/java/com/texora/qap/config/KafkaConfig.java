package com.texora.qap.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String QAP_AUDIT_TOPIC = "qap.audit.events";

    @Bean
    public NewTopic qapAuditTopic() {
        return TopicBuilder.name(QAP_AUDIT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
