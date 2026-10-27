package event;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {

    private String eventId;
    private EventType eventType;
    private LocalDateTime occurredAt;

    public void initializeEvent() {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }

        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}