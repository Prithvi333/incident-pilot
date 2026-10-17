# IncidentPilot

AI-powered incident management platform that learns from past outages to help resolve future ones faster.

## Tech Stack

- Java 21, Spring Boot 4.1.0, Maven
- React, Tailwind CSS (frontend — later milestone)
- MySQL (production), H2 (development/testing)
- Spring AI, RAG, vector database (AI features — later milestone)
- Docker, Jenkins, Kubernetes, AWS (DevOps — later milestone)

## Architecture

Monolith-first. The backend is a single Spring Boot application with clear package boundaries
(model, dto, service, repository, controller, exception, config) that can be split later if needed.

## Coding Rules

### Dependency Injection
- Use `@RequiredArgsConstructor` with `final` fields.
- Never use `@Autowired`.

### Entities
- Annotate with `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- Never use `@Data` on entities.

### DTOs
- Annotate with `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.

### Money
- Use `BigDecimal` for any money/currency fields. Never `double` or `float`.

### Tests
- JUnit 5, Mockito, AssertJ, MockMvc.
- Use AssertJ `assertThat`. Never use `assertEquals`.

### Code Comments
- Explain important logic in simple language so the developer can confidently explain it in interviews.
- Focus on "why" not "what" — don't comment obvious code.

### General
- Show diffs, not full rewrites when editing existing files.
- Ask before deleting any file.
- Build one feature at a time, test it, explain what was built.
