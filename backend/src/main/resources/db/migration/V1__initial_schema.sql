CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    role ENUM('ADMIN', 'ENGINEER', 'VIEWER') NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incidents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    severity ENUM('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW') NOT NULL,
    status ENUM('TRIGGERED', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'POSTMORTEM') NOT NULL,
    assignee VARCHAR(255),
    service_name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    resolved_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timeline_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    type ENUM('STATUS_CHANGE', 'COMMENT', 'ACTION') NOT NULL,
    message TEXT NOT NULL,
    author VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_timeline_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    status ENUM('UPLOADED', 'PROCESSING', 'INDEXED', 'FAILED') NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    indexed_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);
CREATE INDEX idx_incidents_service ON incidents(service_name);
CREATE INDEX idx_timeline_incident ON timeline_events(incident_id);
