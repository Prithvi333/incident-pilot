# IncidentPilot

AI-powered incident management platform that learns from past outages to help resolve future ones faster.

## Actual Tech Stack (verified)

- **Backend**: Java 20, Spring Boot 3.4.1, Maven
- **Database**: H2 (dev/test), MySQL (production — not yet configured)
- **Frontend**: React 18, Vite, Tailwind CSS (planned — Milestone 3)
- **AI**: Spring AI, RAG, vector database (planned — Milestone 5)
- **DevOps**: Docker, Jenkins, Kubernetes, AWS (planned — Milestone 6+)

> **Note**: Machine has Java 20 installed. CLAUDE.md says 21 / Spring Boot 4.1.0 but
> pom.xml uses `<java.version>20</java.version>` and Spring Boot 3.4.1. Use actual values.

## Architecture

Monolith-first. Single Spring Boot application with clear package boundaries:
`model/`, `dto/`, `service/`, `repository/`, `controller/`, `exception/`, `config/`.
Do not introduce microservices.

## Coding Rules

### Dependency Injection
- Use `@RequiredArgsConstructor` with `final` fields.
- Never use `@Autowired`.

### Entities
- Annotate with `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- Never use `@Data` on entities (causes JPA issues with equals/hashCode).

### DTOs
- Annotate with `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.

### Money
- Use `BigDecimal` for any money/currency fields. Never `double` or `float`.

### Tests
- JUnit 5, Mockito, AssertJ, MockMvc.
- Use AssertJ `assertThat`. Never use `assertEquals`.
- Use `@MockitoBean` (not deprecated `@MockBean`) in Spring Boot 3.4+.

### Code Comments
- Explain important logic in simple language for interview confidence.
- Focus on "why" not "what" — don't comment obvious code.

### General
- Show diffs, not full rewrites when editing existing files.
- Ask before deleting any file.
- Build one feature at a time, test it, explain what was built.
- Run `mvn test` from `backend/` to verify before committing.

## Context-Reset Rules

When starting a new session, read these files in order:
1. `CLAUDE.md` (this file) — conventions and constraints
2. `docs/PROJECT-STATE.md` — what is built, what's next
3. `docs/IMPLEMENTATION-PLAN.md` — milestone details

Do not re-derive decisions already documented. Do not change architecture
or tech stack without discussing with the developer first.
