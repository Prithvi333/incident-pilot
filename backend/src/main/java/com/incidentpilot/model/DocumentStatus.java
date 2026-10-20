package com.incidentpilot.model;

/**
 * Tracks a document's indexing lifecycle.
 *
 * <p>UPLOADED → file stored in S3 but not yet embedded.
 * PROCESSING → embedding in progress.
 * INDEXED → successfully embedded in vector store, searchable by RAG.
 * FAILED → embedding failed; file is still in S3 but not searchable.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    INDEXED,
    FAILED
}
