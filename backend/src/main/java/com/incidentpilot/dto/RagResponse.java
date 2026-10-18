package com.incidentpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {

    private List<SimilarMatch> similarMatches;
    private String resolutionSuggestion;
    private boolean aiAvailable;
}
