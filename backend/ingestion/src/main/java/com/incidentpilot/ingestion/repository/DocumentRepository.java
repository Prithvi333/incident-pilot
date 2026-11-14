package com.incidentpilot.ingestion.repository;

import com.incidentpilot.ingestion.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Reads are always scoped by organization; a document id from another tenant resolves to "not found". */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** Active (not deleted) documents of the organization, newest first. */
    @Query("SELECT d FROM Document d WHERE d.organizationId = :orgId AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC, d.id DESC")
    List<Document> findByOrganizationIdOrderByUploadedAtDescIdDesc(@Param("orgId") Long organizationId);

    /** Active document of the organization; deleted or foreign documents behave as missing. */
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.organizationId = :orgId AND d.deletedAt IS NULL")
    Optional<Document> findByIdAndOrganizationId(@Param("id") Long id, @Param("orgId") Long organizationId);
}
