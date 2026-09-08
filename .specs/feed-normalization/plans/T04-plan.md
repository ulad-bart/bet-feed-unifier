# T04 — Packaging, README, and end-to-end verification

Executes `tasks.md` § T04. Depends on: T02, T03. Branch: `feed-normalization/packaging-readme`.

## File list

```
Dockerfile
docker-compose.yml
README.md   (rewrite — currently a one-line stub: "# bet-feed-unifier")
```

## Dockerfile (multi-stage)

```dockerfile
# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 1001 appuser
COPY --from=build /workspace/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Both base images (`maven:3.9-eclipse-temurin-25`, `eclipse-temurin:25-jre`) confirmed to exist on Docker Hub — no fallback needed. The build stage uses the image's bundled `mvn` (not `./mvnw`) — `maven:3.9-eclipse-temurin-25` already pins a known-good Maven 3.9 + JDK 25 combination, so copying/invoking the wrapper inside the container adds nothing; `./mvnw` remains the documented path for the local (non-Docker) run. Tests are skipped in the image build (`-DskipTests`) since `mvn clean verify` runs separately per the DoD, and re-running the full suite on every `docker compose up --build` would just slow down the Docker path without adding coverage.

## docker-compose.yml

```yaml
services:
  bet-feed-unifier:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
```

## README.md content outline

- Project summary (one paragraph, matching `CLAUDE.md` § About the project).
- **Prerequisites** — JDK 25 (only for the local/non-Docker path) *or* Docker + Docker Compose v2 (only for the Docker path); call out the two paths are independent, no need to install both.
- **Run — Path 1 (Docker):** `docker compose up --build`; service reachable at `http://localhost:8080`; log lines visible via `docker compose logs -f`.
- **Run — Path 2a (Maven):** `./mvnw spring-boot:run`.
- **Run — Path 2b (packaged jar):** `./mvnw clean package` then `java -jar target/bet-feed-unifier-0.0.1-SNAPSHOT.jar`.
- **Usage — curl examples**, one per message shape (exact bodies from `requirements.md`'s examples), each documented with the expected `HTTP/1.1 202 Accepted` empty-body response and a pointer to where the resulting standardized-message log line appears (console for local run, `docker compose logs -f` for Docker):
  - ProviderAlpha `odds_update`
  - ProviderAlpha `settlement`
  - ProviderBeta `ODDS`
  - ProviderBeta `SETTLEMENT`
- **One `400` example** — e.g. ProviderAlpha odds `"1": 0.5` (not `> 1.0`) — documented as `HTTP/1.1 400 Bad Request`, `Content-Type: application/problem+json`.

## Refs

Requirement: `.specs/files/bet-feed-task.pdf` § Delivery ("100% executable," README documentation).
AC: `requirements.md` § Publishing, § Validation / error handling (end-to-end confirmation, not new behavior).
Design: `design.md` § API contract, § Component integration (Maven dependencies).

## Definition of Done (from `tasks.md`, unchanged)

- `docker compose up --build` starts the service; both endpoints respond as documented.
- `./mvnw spring-boot:run` (and the packaged jar) starts the service; both endpoints respond identically.
- Each README curl example produces the exact status code and, for `202` cases, the exact standardized-message shape shown in `requirements.md` § Example request/response.
- `mvn clean verify` still passes (no regressions from packaging changes).

## Open questions

None outstanding for this step.
