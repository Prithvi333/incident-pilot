package com.incidentpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single result from the vector similarity search — a past incident or document that matches. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarMatch {

    private String content;
    /** Origin identifier, e.g. "incident:42" or a document filename. */
    private String source;
    /** Cosine similarity score (0.0–1.0); higher means more relevant. */
    private double score;
}
