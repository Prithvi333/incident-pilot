# IncidentPilot

AI-powered incident management platform that learns from past outages to help resolve future ones faster.

## Problem

When production goes down, on-call engineers waste 30–60 minutes searching through scattered runbooks, old Slack threads, and past postmortems. IncidentPilot builds an AI-powered knowledge layer over your incident history so that when a new incident hits, it surfaces similar past incidents and resolution steps instantly.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 20, Spring Boot 3.4.1, Maven |
| Database | H2 (dev), MySQL (production — planned) |
| Frontend | React 19, Vite 8, Tailwind CSS 4, React Router, Axios, Recharts |
| AI | Spring AI, RAG, vector database (planned) |
| DevOps | Docker, Jenkins, Kubernetes, AWS (planned) |

## Getting Started

### Prerequisites

- Java 20+
- Maven 3.9+
- Node.js 22+ and npm 10+

### Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

### Run the frontend

```bash
cd frontend
npm install
npm run dev
```

The UI starts at `http://localhost:5173`. API calls are proxied to the backend automatically.

### Run tests

```bash
cd backend
mvn test
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/incidents` | Create a new incident |
| GET | `/api/incidents` | List all incidents (supports `?status=` and `?severity=` filters) |
| GET | `/api/incidents/stats` | Aggregate stats (totals, MTTR, breakdowns) |
| GET | `/api/incidents/{id}` | Get incident by ID |
| PATCH | `/api/incidents/{id}/status` | Transition incident status |
| POST | `/api/incidents/{id}/timeline` | Add a timeline event (comment/action) |
| GET | `/api/incidents/{id}/timeline` | Get incident timeline |

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
│   └── config/         # CORS and app configuration
└── src/test/java/      # Unit and integration tests

frontend/
├── src/
│   ├── api/            # Axios API client
│   ├── components/     # Navbar, StatusBadge, SeverityBadge, StatsCard
│   ├── pages/          # Dashboard, IncidentList, IncidentDetail, CreateIncident
│   ├── App.jsx         # Router setup
│   └── main.jsx        # Entry point
├── vite.config.js      # Vite + Tailwind + API proxy
└── package.json
```

## Roadmap

- [x] Incident domain with state machine
- [x] Incident timeline (event log)
- [x] API filtering and statistics
- [x] React frontend dashboard
- [ ] Spring Security & JWT authentication
- [ ] AI-powered similar incident search (RAG)
- [ ] Docker + CI/CD pipeline
- [ ] Kubernetes & AWS deployment
