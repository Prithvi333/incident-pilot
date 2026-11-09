package event;


import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDocumentIngestionEvent extends BaseEvent {
    private Long id;
    private String title;
    private String serviceName;
    private String severityName;
    private String description;
}