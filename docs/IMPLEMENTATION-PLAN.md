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

## Milestone 3: React Frontend Dashboard ✅
**Status**: Complete

- [x] React 19 + Vite 8 + Tailwind CSS 4 scaffold in `frontend/`
- [x] Axios API layer with centralized base URL config
- [x] Dashboard page: stats cards, active incidents list
- [x] Incident list page: table with status/severity filter dropdowns
- [x] Incident detail page: info panel, status transition button, timeline view
- [x] Create incident form with client-side validation
- [x] Add timeline event form (COMMENT/ACTION)
- [x] Reusable components: StatusBadge, SeverityBadge, StatsCard
- [x] Backend: CORS `CorsConfigurationSource` bean for `localhost:5173`

**Acceptance**: Full create → acknowledge → investigate → resolve → view timeline
flow works through the UI. `npm run build` succeeds.

---

## Milestone 4: Spring Security & JWT Authentication ✅
**Status**: Complete

- [x] `spring-boot-starter-security` + `jjwt` 0.12.6 dependencies
- [x] User entity with BCrypt password, Role enum (ADMIN, ENGINEER, VIEWER)
- [x] JWT token generation and validation (JwtService)
- [x] POST /api/auth/register, POST /api/auth/login endpoints
- [x] SecurityFilterChain: stateless, CSRF disabled, JWT filter
- [x] Role-based access: VIEWER=read-only, ENGINEER=read+write, ADMIN=all
- [x] Custom AuthenticationEntryPoint returning JSON 401/403
- [x] Frontend: login/register pages, AuthContext, ProtectedRoute, Axios interceptor
- [x] 10 new tests (5 JwtService, 5 AuthController), existing tests updated with @WithMockUser

**Acceptance**: Unauthenticated requests return 401. Role restrictions enforced. 44 tests pass.

---

## Milestone 5: AI-Powered Similar Incident Search (RAG) ✅
**Status**: Complete

- [x] Spring AI 1.0.0 + Ollama for local dev (free, no API keys)
- [x] Document upload API (POST /api/documents, stored on local filesystem)
- [x] Auto-embed incidents on creation into Qdrant vector store
- [x] Qdrant vector store for semantic similarity search
- [x] RAG retrieval: GET /api/incidents/{id}/similar finds similar past incidents
- [x] LLM-generated resolution suggestions based on similar incidents
- [x] Frontend: SimilarIncidents panel on incident detail, Knowledge Base page
- [x] Resilience4j circuit breaker on all AI calls with graceful fallback
- [x] 8 new tests (3 DocumentController, 5 RagService), 52 total pass

**Acceptance**: Upload postmortems via Knowledge Base. Create incident, view similar matches + AI suggestion. AI down → fallback message shown.

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
