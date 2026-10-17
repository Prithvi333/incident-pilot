package com.incidentpilot.repository;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findByServiceName(String serviceName);
}
