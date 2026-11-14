package com.incidentpilot.kafka.producer;

import com.incidentpilot.util.TransactionUtils;
import event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.document-ingestion}")
    private String topic;

    /**
     * Publishes once the surrounding transaction has committed, so a rolled-back incident never
     * leaves an orphan event in Kafka. Outside a transaction it publishes immediately.
     */
    public void publishAfterCommit(BaseEvent event) {
        TransactionUtils.afterCommit(() -> publish(event));
    }

    /**
     * Sends and waits for the broker's acknowledgement (used by the outbox relay, which needs to know
     * whether delivery succeeded). The key keeps all events of one aggregate on one partition, in order.
     */
    public void publishSync(String key, BaseEvent event) throws Exception {
        kafkaTemplate.send(topic, key, event).get(5, java.util.concurrent.TimeUnit.SECONDS);
        log.info("Published {} event {} to topic {}", event.getEventType(), key, topic);
    }

    public void publish(BaseEvent event) {
        kafkaTemplate.send(topic, event.getEventId(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Failed to publish event: {}", event.getEventId(), exception);
                    } else {
                        log.info("Published {} event to topic {}", event.getEventType(), topic);
                    }
                });
    }
}
