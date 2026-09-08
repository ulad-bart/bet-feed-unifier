# Tasks — Feed Normalization

Implements `design.md` in this folder. Four steps, each one PR-sized commit on branch `feed-normalization/<short-name>` (per `SPEC_WORKFLOW.md` § Document shape → `tasks.md`; branch names and commits never include the internal `T<step-id>` identifier, per `CLAUDE.md` § PR invariants).

Per-task execution plans are authored at execution time, not here — before writing code for step `T<nn>`, the executing agent drafts `.specs/feed-normalization/plans/T<nn>-plan.md` with the concrete edits, file paths, and test names for that step. Each step below gets a 1–3 line execution result appended once it lands.

**Dependency graph:** `T01 → (T02, T03 in parallel) → T04`.

---

## T01 — Project foundation: domain model, ports, error handling

**Depends on:** none (independent, first task)
**Branch:** `feed-normalization/foundation`

**Scope**
- Maven scaffold: `pom.xml` (Spring Boot 4.0.x parent, Java 25 release, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`), `com.sporty.BetFeedUnifierApplication`.
- `domain/`: `StandardMessage` (sealed interface, `@JsonTypeInfo` → `messageType`), `StandardOddsChangeMessage`, `StandardBetSettlementMessage`, `Outcome` enum (`@JsonValue` → `"1"/"X"/"2"`), `FeedProviderId` enum.
- `provider/FeedProvider.java` — port interface (`StandardMessage standardize(T raw)`).
- `publish/MessagePublisher.java` (port) and `publish/LoggingMessagePublisher.java` (SLF4J + `ObjectMapper`, logs the standardized message as JSON).
- `error/FeedErrorHandler.java` — `@RestControllerAdvice extends ResponseEntityExceptionHandler`, overrides `handleMethodArgumentNotValid` and `handleHttpMessageNotReadable` to return `400` `ProblemDetail`.

**Refs**
- AC: `requirements.md` § Standardized schema (all 3 bullets), § Publishing (bullet 1), § Validation / error handling (the `400`/`ProblemDetail` mechanism, generically — provider-specific field rules land in T02/T03).
- Design: `design.md` § Standardized schema, § Provider request DTOs and parsing (`FeedProvider` port only), § Validation rules (enforcement paragraph), § Component integration (package map, `error/`).

**Definition of Done**
- `mvn clean verify` passes.
- Unit test: `Outcome` serializes to `"1"`/`"X"`/`"2"` via Jackson.
- Unit test: `StandardOddsChangeMessage`/`StandardBetSettlementMessage` serialize `messageType` as `"ODDS_CHANGE"`/`"BET_SETTLEMENT"` respectively.
- Unit test: `LoggingMessagePublisher.publish(...)` logs valid JSON matching the standardized schema shape.
- Test: a `MethodArgumentNotValidException`/`HttpMessageNotReadableException` thrown against a minimal test-only `@RestController` yields `400` with `application/problem+json` body via `FeedErrorHandler`.
- No `/provider-alpha/feed` or `/provider-beta/feed` endpoint exists yet — expected, added in T02/T03.

**Execution result:** Done. `mvn clean verify` — BUILD SUCCESS, 10/10 tests passing (JDK 25). Discovered Spring Boot 4.0.3 ships Jackson 3 by default (`tools.jackson.*`, not classic Jackson 2) and moved `@WebMvcTest` into a new `spring-boot-starter-webmvc-test` starter; both corrected in `design.md` and `T01-plan.md`. Branch: `feed-normalization/foundation`.

---

## T02 — ProviderAlpha ingestion

**Depends on:** T01
**Branch:** `feed-normalization/provider-alpha`

**Scope**
- `provider/alpha/`: `ProviderAlphaMessage` (sealed interface, `@JsonTypeInfo` on `msg_type`), `ProviderAlphaOddsChangeRequest`, `ProviderAlphaOdds`, `ProviderAlphaSettlementRequest` (records with Bean Validation annotations), `ProviderAlphaMapper implements FeedProvider<ProviderAlphaMessage>`, `ProviderAlphaController` (`POST /provider-alpha/feed`).

**Refs**
- AC: `requirements.md` § ProviderAlpha ingestion (all 3), § Publishing (both, scoped to this endpoint), § Validation / error handling (all 4, scoped to ProviderAlpha's field names/allowed values), § Statelessness (all 3, scoped to ProviderAlpha).
- Design: `design.md` § Provider request DTOs and parsing (ProviderAlpha block), § Validation rules (ProviderAlpha rows), § Component integration (`provider/alpha/*`).

**Definition of Done**
- `mvn clean verify` passes.
- Mapper unit tests: `ProviderAlphaOddsChangeRequest` → `StandardOddsChangeMessage` (`provider=PROVIDER_ALPHA`, keys `1`/`X`/`2` unchanged); `ProviderAlphaSettlementRequest` → `StandardBetSettlementMessage`.
- `@WebMvcTest(ProviderAlphaController.class)`: valid `odds_update` → `202` + `MessagePublisher.publish` invoked with the correct standardized message (mocked publisher); valid `settlement` → same.
- `@WebMvcTest`: missing `event_id` → `400`; an odds value `≤ 1.0` → `400`; `outcome` outside `{1,X,2}` → `400`; unrecognized `msg_type` → `400`; malformed JSON body → `400`.
- `@WebMvcTest`: two `settlement` requests for the same `event_id` in sequence → both `202`, both published — no rejection, no dedup.

**Execution result:** Done. `mvn clean verify` — BUILD SUCCESS, 20/20 tests passing. One correction to `T02-plan.md`'s literal snippet: AssertJ's `isCloseTo(Instant, long)` doesn't exist — used `isCloseTo(Instant.now(), within(Duration.ofSeconds(5)))` instead. Branch: `feed-normalization/provider-alpha`.

---

## T03 — ProviderBeta ingestion

**Depends on:** T01
**Branch:** `feed-normalization/provider-beta`

**Scope**
- `provider/beta/`: mirrors T02's structure — `ProviderBetaMessage` (sealed interface, `@JsonTypeInfo` on `type`), `ProviderBetaOddsChangeRequest`, `ProviderBetaOdds`, `ProviderBetaSettlementRequest`, `ProviderBetaMapper implements FeedProvider<ProviderBetaMessage>`, `ProviderBetaController` (`POST /provider-beta/feed`).

**Refs**
- AC: `requirements.md` § ProviderBeta ingestion (all 3), § Publishing / § Validation / § Statelessness (same shared bullets as T02, scoped to ProviderBeta).
- Design: `design.md` § Provider request DTOs and parsing (ProviderBeta block), § Validation rules (ProviderBeta rows), § Component integration (`provider/beta/*`).

**Definition of Done**
- Same shape as T02's DoD, scoped to ProviderBeta's field names (`home`/`draw`/`away`, `ODDS`/`SETTLEMENT`) and endpoint. Includes the mapper unit tests, the `@WebMvcTest` happy-path/validation/duplicate-settlement tests, and `mvn clean verify` passing.

**Execution result:** Done. `mvn clean verify` — BUILD SUCCESS, 30/30 tests passing. Controller tests load payloads from `src/test/resources/provider/beta` fixtures (valid: `odds-change.json`/`settlement.json`; invalid: `missing-event-id.json`, `odds-not-greater-than-one.json`, `invalid-result.json`, `unrecognized-type.json`, `malformed.json`), matching T02's fixture-based test pattern. Branch: `feed-normalization/provider-beta`.

---

## T04 — Packaging, README, and end-to-end verification

**Depends on:** T02, T03
**Branch:** `feed-normalization/packaging-readme`

**Scope**
- `Dockerfile` (multi-stage: Maven+JDK 25 build stage, minimal JRE 25 runtime stage) and `docker-compose.yml`.
- `README.md` covering: prerequisites; **both** run paths — (1) `docker compose up --build`, and (2) local Maven (`./mvnw spring-boot:run`) or packaged jar (`./mvnw clean package` then `java -jar target/*.jar`); `curl` examples for all four message shapes (ProviderAlpha odds/settlement, ProviderBeta odds/settlement) with the expected `202` response and where to see the resulting standardized-message log line (console, or `docker compose logs -f`); one `400` example.

**Refs**
- Requirement: `.specs/files/bet-feed-task.pdf` § Delivery ("100% executable," README documentation).
- AC: `requirements.md` § Publishing, § Validation / error handling (end-to-end confirmation, not new behavior).
- Design: `design.md` § API contract, § Component integration (Maven dependencies).

**Definition of Done**
- `docker compose up --build` starts the service; both endpoints respond as documented.
- `./mvnw spring-boot:run` (and the packaged jar) starts the service; both endpoints respond identically.
- Each README `curl` example produces the exact status code and, for `202` cases, the exact standardized-message shape shown in `requirements.md` § Example request/response.
- `mvn clean verify` still passes (no regressions from packaging changes).

**Execution result:** Done, with one caveat. `mvn clean verify` — BUILD SUCCESS, 30/30 tests. `./mvnw spring-boot:run` verified end-to-end: all four README `curl` examples returned `202 Accepted` with published-message log lines exactly matching `requirements.md`'s example shapes, and the invalid-odds example returned `400 Bad Request` / `application/problem+json`. The Docker path (`docker compose up --build`) was **not** executed — the Docker daemon isn't running in this environment — but `Dockerfile`/`docker-compose.yml` content was verified against the agreed plan. Branch: `feed-normalization/packaging-readme`.
