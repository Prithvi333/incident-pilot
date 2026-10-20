# IncidentPilot — Project Requirements

## Project Name
**IncidentPilot** — AI-powered incident management platform that learns from past outages to help resolve future ones faster.

## Problem Statement
When production goes down, on-call engineers waste 30–60 minutes searching through scattered runbooks, old Slack threads, and past postmortems. Institutional knowledge is trapped in people's heads. Every incident feels like starting from scratch.

IncidentPilot solves this by building an AI-powered knowledge layer on top of incident management. When a new incident is created, the system surfaces similar past incidents, relevant runbook steps, and suggested resolution actions — all sourced from historical data.

## Target Users
- **On-call engineers** — contextual help during live incidents
- **Engineering managers** — track MTTR, incident trends, recurring problems
- **SRE / Platform teams** — searchable knowledge base from postmortems

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, Tailwind CSS, React Router, Axios, Recharts |
| Backend | Java 20, Spring Boot 3.4.1, Maven, Spring Security, Spring AI |
| Database | H2 (dev), MySQL 8 (prod) |
| Vector Store | PGVector or ChromaDB |
| AI | Spring AI with OpenAI or Ollama (local dev) |
| DevOps | Docker, Docker Compose, Jenkins |
| Cloud | AWS (EKS/EC2, RDS, S3, ECR) |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers |

## Core Features

### 1. Incident Lifecycle Management
- Create incidents with title, description, severity (P1-P4), assignee, service name
- State machine: TRIGGERED → ACKNOWLEDGED → INVESTIGATING → RESOLVED → POSTMORTEM
- Invalid transitions rejected with clear error messages

### 2. Incident Timeline
- Append-only event log per incident (STATUS_CHANGE, COMMENT, ACTION)
- Auto-generated entries on status transitions
- Manual entries from engineers during outages

### 3. Filtering & Statistics
- Filter incidents by status, severity
- Dashboard stats: total, active, resolved, MTTR, breakdowns by severity/status

### 4. React Frontend Dashboard
- Real-time dashboard with stats cards and active incident list
- Incident list with filter dropdowns
- Incident detail with timeline and status transition buttons
- Create incident form with validation

### 5. AI-Powered Similar Incident Search (RAG)
- Upload runbooks and postmortem documents
- Chunk and embed documents into a vector store
- When a new incident is created, RAG retrieves similar past incidents
- LLM generates suggested resolution steps from context

### 6. AI Resolution Suggestions
- Step-by-step resolution plan based on incident description + similar past incidents
- Circuit breaker for graceful degradation when AI service is unavailable

### 7. SLA Tracking & Alerts
- SLA thresholds per severity level
- Automated escalation notifications when SLA is at risk
- Scheduled checks via Spring `@Scheduled`

### 8. AI Postmortem Generator
- Draft postmortem from incident timeline
- Engineer can edit and finalize

### 9. Analytics & Trends
- MTTR over time
- Incidents by service/team
- Recurring issue detection

### 10. Webhook Integration
- Accept alerts from external monitoring tools to auto-create incidents

## Architecture Principles
- **Monolith-first**: Single Spring Boot application with clear module boundaries
- **No microservices** until there's a proven scaling need
- **Layered architecture**: Controller → Service → Repository
- **State machine pattern** for incident lifecycle
- **Append-only pattern** for timeline (audit trail)
- **Circuit breaker** on AI calls (Resilience4j)
- **Async document processing** for uploads

## Security Requirements
- JWT-based authentication with Spring Security
- Role-based access control: Admin, Engineer, Viewer
- Method-level security with `@PreAuthorize`
- Input validation on all endpoints
- Rate limiting on AI endpoints

## Cost Constraints
- **AI API**: Ollama locally during dev (free). Cheap model for demos (~$5/month)
- **Vector DB**: ChromaDB or PGVector (free)
- **AWS**: Free tier where possible. Tear down when not demoing (~$10-15/month)
- **Total**: $5–20/month during active development, $0 when paused

## Interview Topics This Project Covers
1. State machine pattern and incident lifecycle design
2. RAG implementation (chunking, embedding, retrieval, prompt construction)
3. Real-time updates (WebSocket, STOMP, SockJS)
4. CI/CD pipeline design and deployment strategy
5. Security: JWT auth, RBAC, IDOR prevention, input validation
6. JPA relationships, LAZY vs EAGER fetch, N+1 prevention
7. Circuit breaker and graceful degradation
8. API design (REST, filtering, pagination, error responses)
9. Testing strategy (unit, integration, MockMvc, Testcontainers)
10. Docker multi-stage builds, Kubernetes health probes
