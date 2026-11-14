
package com.incidentpilot.ai.kafka.consumer;

import event.BaseEvent;
import event.DocumentIngestionEvent;
import event.IncidentDocumentIngestionEvent;
import com.incidentpilot.ai.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionConsumer {

    private final RagService ragService;

    @KafkaListener(
            topics = "${app.kafka.topics.document-ingestion}",
            containerFactory = "${app.kafka.consumer.container-factory}"
    )
    public void consume(BaseEvent event) {

        if (event == null || event.getEventType() == null) {
            log.warn("Received invalid ingestion event");
            return;
        }

        log.info("Processing event: {}, ID: {}",
                event.getEventType(), event.getEventId());

        switch (event.getEventType()) {

            case DOCUMENT -> {
                if (event instanceof DocumentIngestionEvent documentEvent) {
                    ragService.embedDocument(
                            documentEvent.getText(),
                            documentEvent.getFilename(),
                            documentEvent.getOrganizationId(),
                            documentEvent.getEventId(),
                            documentEvent.getDocumentId()
                    );
                } else {
                    log.warn("Invalid payload class for DOCUMENT event");
                }
            }

            case INCIDENT -> {
                if (event instanceof IncidentDocumentIngestionEvent incidentEvent) {
                    ragService.embedIncident(incidentEvent);
                } else {
                    log.warn("Invalid payload class for INCIDENT event");
                }
            }

            case INCIDENT_DELETE -> {
                if (event instanceof IncidentDocumentIngestionEvent incidentEvent) {
                    ragService.deleteIncident(incidentEvent.getOrganizationId(), incidentEvent.getId());
                } else {
                    log.warn("Invalid payload class for INCIDENT_DELETE event");
                }
            }

            case DOCUMENT_DELETE -> {
                if (event instanceof DocumentIngestionEvent documentEvent) {
                    ragService.deleteDocument(documentEvent.getOrganizationId(), documentEvent.getDocumentId());
                } else {
                    log.warn("Invalid payload class for DOCUMENT_DELETE event");
                }
            }
        }
    }
}
