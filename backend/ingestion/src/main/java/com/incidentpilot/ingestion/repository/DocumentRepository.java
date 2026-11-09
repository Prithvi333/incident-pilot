package com.incidentpilot.ingestion.repository;

import com.incidentpilot.ingestion.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Reads are always scoped by organization; a document id from another tenant resolves to "not found". */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOrganizationIdOrderByUploadedAtDescIdDesc(Long organizationId);

    Optional<Document> findByIdAndOrganizationId(Long id, Long organizationId);
}
