package com.incidentpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from the RAG (Retrieval-Augmented Generation) similarity search.
 *
 * <p>{@code aiAvailable} tells the frontend whether the LLM was reachable —
 * if false, the resolution suggestion is a static fallback message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {

    private List<SimilarMatch> similarMatches;
    private String resolutionSuggestion;
    private boolean aiAvailable;
}
