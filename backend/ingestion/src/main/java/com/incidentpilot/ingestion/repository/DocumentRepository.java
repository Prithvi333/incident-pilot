package com.incidentpilot.ingestion.repository;

import com.incidentpilot.ingestion.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}

