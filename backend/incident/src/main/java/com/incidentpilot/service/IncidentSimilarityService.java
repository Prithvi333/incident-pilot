package com.incidentpilot.service;

import com.incidentpilot.dto.RelatedIncidentResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.repository.IncidentRepository;
import com.incidentpilot.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds similar past incidents using only the database (shared service + overlapping words in the
 * title/description), so the incident page never depends on the AI service being up.
 *
 * <p>Score = 0.7 × word overlap (Jaccard) + 0.3 if the same service. Only active incidents of the
 * caller's organization are considered, so deleted incidents and other tenants never appear.
 */
@Service
@RequiredArgsConstructor
public class IncidentSimilarityService {

    private static final int MAX_CANDIDATES = 500;
    private static final int MAX_RESULTS = 5;
    private static final double MIN_SCORE = 0.15;
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "are", "was", "has", "not", "but",
            "all", "can", "its", "out", "when", "into", "any", "have", "been", "were", "will");

    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public List<RelatedIncidentResponse> findRelated(Long incidentId) {
        Long orgId = CurrentUser.require().getOrganizationId();
        Incident base = incidentRepository.findByIdAndOrganizationId(incidentId, orgId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        Set<String> baseWords = words(base);
        return incidentRepository.findByOrganizationId(orgId).stream()
                .filter(candidate -> !candidate.getId().equals(base.getId()))
                .limit(MAX_CANDIDATES)
                .map(candidate -> {
                    double score = 0.7 * jaccard(baseWords, words(candidate))
                            + (candidate.getServiceName().equalsIgnoreCase(base.getServiceName()) ? 0.3 : 0);
                    return RelatedIncidentResponse.from(candidate, Math.round(score * 100) / 100.0);
                })
                .filter(r -> r.getScore() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(RelatedIncidentResponse::getScore).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    private Set<String> words(Incident incident) {
        String text = incident.getTitle() + " " + (incident.getDescription() == null ? "" : incident.getDescription());
        Set<String> result = new HashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                result.add(word);
            }
        }
        return result;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / (a.size() + b.size() - intersection.size());
    }
}
