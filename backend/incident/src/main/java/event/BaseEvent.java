package event;


import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private EventType eventType;
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();

}