package com.incidentpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IncidentPilot — AI-powered incident management platform.
 *
 * <p>{@code @EnableScheduling} activates cron-style SLA breach detection
 * that runs on a fixed interval to flag overdue incidents.
 */
@SpringBootApplication
@EnableScheduling
public class IncidentPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentPilotApplication.class, args);
    }
}
