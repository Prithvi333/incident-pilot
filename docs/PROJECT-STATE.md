# IncidentPilot — Project State

> Last updated: 2026-10-18

## Repository Info
- **Location**: `C:\Users\pthak\Desktop\Personal Project`
- **Branch**: `dev`
- **Remote**: `https://github.com/Prithvi333/incident-pilot.git`

## Environment
- **Java**: 20.0.1 (Oracle JDK)
- **Maven**: 3.9.1
- **Spring Boot**: 3.4.1
- **Node.js**: 22.18.0, npm 10.2.5

## Completed Work

### Milestone 1: Incident Domain & REST APIs ✅
- Incident entity with enum-based state machine
- REST APIs: POST, GET, GET/{id}, PATCH/{id}/status
- DTOs with validation, global exception handler

### Milestone 2: Incident Timeline & API Enhancements ✅
- TimelineEvent entity with @ManyToOne to Incident
- Auto-generated timeline entries on status change
- POST/GET `/api/incidents/{id}/timeline`
- Filtering by status/severity, stats endpoint

### Milestone 3: React Frontend Dashboard ✅
- React 19 + Vite 8 + Tailwind CSS 4
- 4 pages: Dashboard, IncidentList, IncidentDetail, CreateIncident
- 4 components: Navbar, StatusBadge, SeverityBadge, StatsCard
- Centralized Axios API layer with Vite dev proxy

### Milestone 4: Spring Security & JWT Authentication ✅
- User entity with BCrypt password, Role enum (ADMIN, ENGINEER, VIEWER)
- JWT authentication with jjwt 0.12.6 (JwtService, JwtAuthenticationFilter)
- SecurityFilterChain: stateless sessions, CSRF disabled, role-based access
- POST /api/auth/register and /api/auth/login endpoints
- Frontend: AuthContext, ProtectedRoute, LoginPage, RegisterPage
- Axios interceptor adds Bearer token, 401 redirects to login

### Milestone 5: AI-Powered Similar Incident Search (RAG) ✅
- Spring AI 1.0.0 with Ollama (local LLM + embeddings)
- Qdrant vector store for semantic similarity search
- Auto-embed incidents on creation into vector store
- Document upload API (postmortems/runbooks) with chunking + embedding
- GET /api/incidents/{id}/similar — RAG-powered similar incident search + AI resolution suggestion
- Frontend: SimilarIncidents panel on incident detail, Knowledge Base page for doc uploads
- Resilience4j circuit breaker on all AI calls with graceful fallback
- 8 new tests (3 DocumentController, 5 RagService)

### Milestone 6: Production Readiness ✅
- MySQL production profile (`application-prod.yml`) with env-var configuration
- Flyway schema migrations (V1 initial schema, V2 audit logs)
- Testcontainers integration test against real MySQL (requires Docker)
- Tightened input validation: size limits, email format, username pattern
- AuditLog entity + AuditService — logs all incident creates and status transitions
- GET `/api/incidents/{id}/audit` endpoint for audit trail
- SLA tracking with `@Scheduled` breach detection (P1: 15m ack / 4h resolve, etc.)
- Bucket4j rate limiting on AI endpoints (20 req/min, 100 req/hr per user)
- Catch-all exception handler (no stack trace leaks)
- Surefire/Failsafe split: `mvn test` = unit tests, `mvn verify` = integration tests

## Test Results (verified 2026-10-18)
```
AuthControllerTest:            5 tests — PASS
DocumentControllerTest:        3 tests — PASS
IncidentControllerTest:        8 tests — PASS
TimelineEventControllerTest:   4 tests — PASS
IncidentStatusTest:            5 tests — PASS
JwtServiceTest:                5 tests — PASS
IncidentServiceTest:          12 tests — PASS
RagServiceTest:                5 tests — PASS
TimelineEventServiceTest:      5 tests — PASS
AuditServiceTest:              3 tests — PASS
SlaServiceTest:                4 tests — PASS
─────────────────────────────────────────────
Total:                        59 tests — BUILD SUCCESS
```
(+ 4 integration tests available when Docker is running)

## File Inventory
```
Backend production code:  37 files (model, dto, service, repo, controller, security, config, exception)
Backend test code:        12 files (9 unit + 1 integration + 2 new service tests)
Frontend source:          15 files (pages, components, context, api)
Config/build:              6 files (pom.xml, application.properties, application-prod.properties, .gitignore, vite.config.js, V1/V2 migrations)
Documentation:             4 files (README.md, docs/*)
```

## Next Task
**Milestone 7: Docker & CI/CD** — Dockerfiles, Docker Compose, Jenkins pipeline, health checks.
