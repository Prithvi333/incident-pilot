package event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIngestionEvent  extends BaseEvent{
    private String text;
    private String filename;
    /** Knowledge-base document id: lets the AI service delete every chunk of a deleted document. */
    private Long documentId;
}
