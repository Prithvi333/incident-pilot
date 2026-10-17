# IncidentPilot — Implementation Plan

## MVP Sequence

Build the core incident-management workflow first (Milestones 1–4), then layer on
AI, security, and DevOps. Each milestone produces a testable, demoable increment.

---

## Milestone 1: Incident Domain & REST APIs ✅
**Status**: Complete

- [x] Spring Boot project scaffold (Java 20, Spring Boot 3.4.1, Maven)
- [x] Incident entity with `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`
- [x] Severity enum (P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW)
- [x] IncidentStatus enum with state machine transitions
- [x] IncidentRepository, IncidentService, IncidentController
- [x] CreateIncidentRequest, UpdateStatusRequest, IncidentResponse DTOs
- [x] GlobalExceptionHandler (400/404 JSON responses)
- [x] Unit tests (state machine), service tests (Mockito), controller tests (MockMvc)

**Acceptance**: `mvn test` passes, invalid state transitions return 400.

---

## Milestone 2: Incident Timeline & API Enhancements ✅
**Status**: Complete (uncommitted on `dev` branch)

- [x] TimelineEvent entity with `@ManyToOne` to Incident
- [x] TimelineEventType enum (STATUS_CHANGE, COMMENT, ACTION)
- [x] Auto-generated timeline entries on status change
- [x] POST/GET `/api/incidents/{id}/timeline` endpoints
- [x] Incident list filtering by status and severity (`?status=&severity=`)
- [x] GET `/api/incidents/stats` endpoint (totals, MTTR, breakdowns)
- [x] `@OneToMany` on Incident entity with LAZY fetch
- [x] Tests for all new features (16 new tests)

**Acceptance**: `mvn test` passes (34 tests), timeline auto-populates on status change.

---

## Milestone 3: React Frontend Dashboard
**Status**: Not started
**Depends on**: Milestones 1–2, plus a CORS config bean in backend

- [ ] React 18 + Vite + Tailwind CSS scaffold in `frontend/`
- [ ] Axios API layer with centralized base URL config
- [ ] Dashboard page: stats cards, active incidents list
- [ ] Incident list page: table with status/severity filter dropdowns
- [ ] Incident detail page: info panel, status transition button, timeline view
- [ ] Create incident form with client-side validation
- [ ] Add timeline event form (COMMENT/ACTION)
- [ ] Reusable components: StatusBadge, SeverityBadge, StatsCard
- [ ] Backend: CORS `WebMvcConfigurer` bean for `localhost:5173`

**Acceptance**: Full create → acknowledge → investigate → resolve → view timeline
flow works through the UI. `npm run build` succeeds.

---

## Milestone 4: Spring Security & JWT Authentication
**Status**: Not started
**Depends on**: Milestone 3 (so the frontend can test auth flows)

- [ ] `spring-boot-starter-security` dependency
- [ ] User entity, UserRepository
- [ ] JWT token generation and validation (jjwt library)
- [ ] Login/register endpoints
- [ ] SecurityFilterChain with JWT filter
- [ ] Role-based access: ADMIN, ENGINEER, VIEWER
- [ ] `@PreAuthorize` on controller methods
- [ ] Frontend: login page, token storage, Axios interceptor for Authorization header
- [ ] Tests with `@WithMockUser`

**Acceptance**: Unauthenticated requests return 401. Role restrictions enforced.

---

## Milestone 5: AI-Powered Similar Incident Search (RAG)
**Status**: Not started
**Depends on**: Milestones 1–2 (backend), Milestone 3 (UI to show results)

- [ ] Spring AI dependency + Ollama for local dev (free)
- [ ] Document upload API (store files in local filesystem or S3)
- [ ] Document chunking and embedding pipeline
- [ ] Vector store integration (ChromaDB or PGVector)
- [ ] RAG retrieval: on incident create/view, find similar past incidents
- [ ] Resolution suggestions panel in frontend incident detail page
- [ ] Circuit breaker (Resilience4j) on AI calls with fallback
- [ ] Tests for embedding service, retrieval, graceful degradation

**Acceptance**: Upload 3-5 sample postmortems. Create a new incident with similar
description. Similar incidents appear in the UI. AI down → fallback message shown.

---

## Milestone 6: Production Readiness
**Status**: Not started
**Depends on**: Milestones 1–5

- [ ] Switch from H2 to MySQL (application-prod.yml profile)
- [ ] Testcontainers for integration tests against real MySQL
- [ ] Comprehensive error handling and input sanitization
- [ ] Audit logging for all state transitions
- [ ] SLA tracking with `@Scheduled` checks
- [ ] API rate limiting on AI endpoints
- [ ] 70%+ test coverage

**Acceptance**: App runs against MySQL. Tests use Testcontainers. No unhandled exceptions.

---

## Milestone 7: Docker & CI/CD
**Status**: Not started
**Depends on**: Milestone 6

- [ ] Multi-stage Dockerfile for backend
- [ ] Dockerfile for frontend (nginx)
- [ ] Docker Compose: backend + frontend + MySQL + ChromaDB
- [ ] Jenkins pipeline: build → test → Docker image → push
- [ ] Health check endpoints (`/actuator/health`)
- [ ] Environment-based configuration (Spring profiles)

**Acceptance**: `docker compose up` starts the full stack. Jenkins pipeline runs green.

---

## Milestone 8: Kubernetes & AWS Deployment
**Status**: Not started
**Depends on**: Milestone 7

- [ ] Kubernetes manifests: Deployment, Service, ConfigMap, Secrets
- [ ] Liveness and readiness probes
- [ ] Deploy to Minikube locally (verify)
- [ ] AWS setup: ECR, EKS or EC2, RDS, S3
- [ ] Rolling update strategy
- [ ] Tear-down script to avoid costs

**Acceptance**: App accessible via K8s service URL. Rolling update with zero downtime.

---

## Advanced Features (Post-MVP, optional)
- WebSocket real-time dashboard updates
- AI postmortem generator
- Webhook endpoint for external alert ingestion
- Analytics trends (MTTR over time, recurring issues)
- Pagination on list endpoints
