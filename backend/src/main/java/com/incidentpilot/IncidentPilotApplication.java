package com.incidentpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IncidentPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentPilotApplication.class, args);
    }
}
