# IncidentPilot

AI-powered incident management platform that learns from past outages to help resolve future ones faster.

## Problem

When production goes down, on-call engineers waste 30–60 minutes searching through scattered runbooks, old Slack threads, and past postmortems. IncidentPilot builds an AI-powered knowledge layer over your incident history so that when a new incident hits, it surfaces similar past incidents and resolution steps instantly.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 20, Spring Boot 3.4, Maven |
| Database | H2 (dev), MySQL (production) |
| Frontend | React, Tailwind CSS (planned) |
| AI | Spring AI, RAG, vector database (planned) |
| DevOps | Docker, Jenkins, Kubernetes, AWS (planned) |

## Getting Started

### Prerequisites

- Java 20+
- Maven 3.9+

### Run the application

```bash
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

### Run tests

```bash
cd backend
mvn test
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/incidents` | Create a new incident |
| GET | `/api/incidents` | List all incidents |
| GET | `/api/incidents/{id}` | Get incident by ID |
| PATCH | `/api/incidents/{id}/status` | Transition incident status |

### Incident Lifecycle

```
TRIGGERED → ACKNOWLEDGED → INVESTIGATING → RESOLVED → POSTMORTEM
```

Each transition is enforced by a state machine — invalid jumps (e.g. TRIGGERED → RESOLVED) are rejected with a 400 error.

## Project Structure

```
backend/
├── src/main/java/com/incidentpilot/
│   ├── model/          # Entities and enums
│   ├── dto/            # Request/response objects
│   ├── repository/     # JPA repositories
│   ├── service/        # Business logic
│   ├── controller/     # REST APIs
│   ├── exception/      # Error handling
│   └── config/         # Configuration classes
└── src/test/java/      # Unit and integration tests
```

## Roadmap

- [x] Incident domain with state machine
- [ ] Incident timeline (event log)
- [ ] API filtering and statistics
- [ ] React frontend dashboard
- [ ] AI-powered similar incident search (RAG)
- [ ] Docker + CI/CD pipeline
- [ ] Kubernetes deployment
