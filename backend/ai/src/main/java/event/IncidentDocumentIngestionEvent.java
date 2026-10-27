package event;


import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDocumentIngestionEvent extends BaseEvent {
    private Long id;
    private String title;
    private String serviceName;
    private String severityName;
    private String description;
}