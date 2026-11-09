# IncidentPilot — Project State

> Last updated: 2026-11-07

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
- Spring AI 1.0.0 with AWS Bedrock (Claude Haiku 4.5 via Converse API)
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


### Milestone 7: Microservices split ✅ (commit 4ba77bf)
- Backend split into `incident`, `ingestion` and `ai` services with Kafka between them; AI runs on Ollama + Qdrant.
- Details: [ARCHITECTURE.md](ARCHITECTURE.md).

### Milestone 8: Multi-tenancy, real-time updates, notifications ✅ (2026-11-07, uncommitted on `dev`)
- **Multi-tenancy**: `Organization` entity; users, incidents, audit logs, notifications and documents belong to one. JWT carries `orgId`; every query is organization-scoped; foreign ids → 404; RAG vectors tagged and filtered by organization; registration creates an organization (admin) or joins one by invite code; startup backfill into "Default Organization".
- **WebSocket (STOMP)** at `/ws` in the incident service: JWT checked on CONNECT, subscriptions limited to the user's own organization topic and private notification queue, client SEND blocked, events published after commit with unique `eventId`, automatic reconnect + resync in the frontend.
- **Notifications**: persisted per user with read/unread, unique-per-event dedupe, REST API + live push, bell/unread badge/panel in the UI.
- **Lifecycle additions**: assignment endpoint, partial incident update, escalation on severity increase, reopen (`RESOLVED → INVESTIGATING`).
- **Bug fixes**: see the list below.
- Documentation: [ARCHITECTURE.md](ARCHITECTURE.md), [API.md](API.md), [CONFIGURATION.md](CONFIGURATION.md).

#### Bugs fixed in Milestone 8
| Area | Bug | Fix |
|---|---|---|
| Security | `/actuator/health` required a token (health checks got 401); `/h2-console` permitted with no H2; frame options disabled | Health public; H2 permit and frame override removed |
| Security | Ingestion/AI services ignored the role claim (viewers could upload documents) | Role → authority; upload requires ENGINEER/ADMIN |
| Security | Timeline author taken from the request body (spoofable) | Author = authenticated user |
| Security | Document S3 key built from the raw client filename | Filename sanitized, key prefixed with the organization |
| API | Bad JSON / unknown enum / bad query param returned 500 | Mapped to 400; auth and access-denied errors mapped to 401/403 |
| API | Incident list had no ordering | `ORDER BY createdAt DESC` |
| API | Unknown document id returned 500; no server-side file-type check | 404 and 400 via a new exception handler |
| Lifecycle | Concurrent status changes could overwrite each other | `@Version` optimistic locking → 409 |
| Lifecycle | No way to reopen a resolved incident | `RESOLVED → INVESTIGATING` reopen |
| SLA | Breaches were re-logged to the audit trail every minute | One-time flags on the incident |
| SLA | Resolve SLA never applied to `TRIGGERED` incidents | Applies to all open incidents |
| SLA | One failing incident aborted the whole scheduler run | Per-incident transactions |
| Kafka | Event published before the transaction committed (ghost events on rollback) | Published after commit |
| Kafka | Producer blocked requests for 60 s when Kafka was down | `max.block.ms=2000`, failures logged |
| Kafka | A poison record could block the AI consumer forever; redelivery duplicated vectors | `ErrorHandlingDeserializer`; deterministic vector ids |
| AI/RAG | Embedded text used the service name where severity belongs | Uses the severity |
| AI/RAG | Vector search was global | Filtered by organization |
| AI/RAG | AI client had no timeouts and returned 500 when the AI service failed | Timeouts + graceful "AI unavailable" response |
| Config | `application-prod.properties` required Flyway/Bedrock (absent) with `ddl-auto=validate` | Aligned with the actual setup |
| Frontend | Documents API ignored 401 (stale tokens left the UI stuck) | Same sign-out handling as the incident API |

## Verification status (2026-11-07)
- Build: `mvn compile/package` for incident, ai, ingestion (compiled on JDK 20 with `-Djava.version=20`; ingestion via a temporary release-20 copy of its pom because the pom hardcodes `<release>21</release>`); `npm run build` succeeds, lint shows only pre-existing style warnings.
- Runtime: incident service booted from its jar on H2 and probed manually (tenant isolation, lifecycle, notifications, WebSocket security). Details and the list of what was **not** verified are in [CONFIGURATION.md](CONFIGURATION.md#testing-status).
- **Automated tests:** 36 focused JUnit 5/Mockito unit tests (tenant isolation, lifecycle, notifications, SLA, WebSocket security, RAG isolation, document scoping) all pass — see [CONFIGURATION.md](CONFIGURATION.md#testing-status). The older 59-test suite is gone from the repository; there are no database, WebSocket end-to-end or frontend tests.
- **Docker Compose:** files fixed and validated with `docker compose config` (Qdrant added, `JWT_SECRET` wired, ingestion health port fixed); stack not started (Docker daemon unavailable).

## Known issues / not done
- Unit tests only: no repository/MySQL tests, no WebSocket end-to-end test, no frontend/E2E test; the pre-existing `contextLoads` tests in `ai`/`ingestion` need live infrastructure.
- `frontend/nginx.conf` proxies to fixed EC2 addresses, so the Compose stack is not self-contained until those point at the container names.
- MySQL, Kafka, Qdrant, Ollama and S3 flows were not exercised end to end in this milestone.
- Simple in-memory STOMP broker: single backend instance only (multiple instances need a broker relay).
- WebSocket token expiry is checked on connect and on each subscribe, not continuously.
- Ingestion marks a document `INDEXED` as soon as the Kafka publish succeeds (before the AI service embeds it); documents are embedded as one vector (no chunking); very large texts can exceed Kafka's default 1 MB message limit.
- Existing Qdrant vectors have no organization tag and are not returned by tenant-filtered search until re-created.
- Incident edits do not re-embed the incident in Qdrant.
- No admin UI for roles; new members join as ENGINEER; the invite code cannot be rotated; usernames are globally unique.
- `.github/workflows/ci.yml` and the root README still describe the old single-backend layout.
- `docs/` is listed in `.gitignore`; new files here need `git add -f` to be committed.

## Next Task
Automated tests for Milestone 8 behavior, then CI/Compose updates for the three-service layout.
