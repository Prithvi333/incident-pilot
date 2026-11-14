package com.incidentpilot.ai.config;

import event.IncidentDocumentIngestionEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    @Value("${app.kafka.consumer.trusted-packages}")
    private String trustedPackages;

    @Bean
    public ConsumerFactory<String, IncidentDocumentIngestionEvent>
    incidentDocumentConsumerFactory() {

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        JsonDeserializer<IncidentDocumentIngestionEvent> deserializer =
                new JsonDeserializer<>(IncidentDocumentIngestionEvent.class);

        deserializer.addTrustedPackages(trustedPackages);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,
            IncidentDocumentIngestionEvent>
    incidentDocumentKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String,
                IncidentDocumentIngestionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(incidentDocumentConsumerFactory());

        // A failed Qdrant/LLM call is retried every 5s for about a minute before the record is skipped
        // (and logged), so a short outage does not lose index updates. Poison records that cannot be
        // deserialized are not retried (ErrorHandlingDeserializer).
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(5000L, 12L)));

        return factory;
    }
}