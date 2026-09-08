# Bet Feed unifier — Agent Guide

## About the project

A Spring Boot 3 / Java 21 microservice that acts as the standardization layer between third-party sports-betting feed providers and the rest of the platform. It exposes one HTTP endpoint per provider, parses that provider's proprietary JSON, normalizes it into a single internal schema, and publishes the result to a mocked downstream message queue.

This is the "2 Feeds - BE Home Assignment" take-home exercise for Sporty Group; the original brief is at `.specs/files/bet-feed-task.pdf`.

## Tech stack

- Java 25 (LTS)
- Spring Boot 4.0.x
- Maven
- JUnit 5, with Spring's `MockMvc`/`@SpringBootTest` for controller-level integration tests — no Testcontainers currently, since there is no database or real message broker to containerize; revisit if either is introduced

## Project map

```
src/main/java/com/sporty/
├── domain/           # standardized message model: StandardOddsChangeMessage, StandardBetSettlementMessage
├── provider/          # inbound port + one adapter package per feed provider
│   ├── FeedNormalizer.java     # port: parse a provider's raw payload into a standardized message
│   ├── alpha/                 # ProviderAlpha: controller, request DTOs, normalizer (endpoint: /provider-alpha/feed)
│   └── beta/                  # ProviderBeta: controller, request DTOs, normalizer (endpoint: /provider-beta/feed)
├── publish/           # outbound port + adapter
│   ├── MessagePublisher.java         # port: publish(StandardMessage)
│   └── LoggingMessagePublisher.java  # mocked queue: logs the standardized message
└── BetFeedUnifierApplication.java
```

- `.specs/files/bet-feed-task.pdf` — original assignment brief.

## Architecture

- Lightweight ports-and-adapters, not full hexagonal layering: each provider integration and the outbound publishing step sit behind a small interface so normalization logic never depends on Spring or on a specific provider/queue implementation.
- Flow: `HTTP request → provider Controller → provider-specific parse+validate → provider FeedNormalizer → StandardXxxMessage → MessagePublisher.publish(...)`.
- Provider dispatch is by fixed URL path, not a runtime registry: `/provider-alpha/feed` and `/provider-beta/feed` are separate controllers, each owning its own request DTOs and mapping to the standardized schema. Adding a third provider means adding a new controller + normalizer package, without touching ProviderAlpha/ProviderBeta code.
- `MessagePublisher` is the single outbound seam. Its only implementation is `LoggingMessagePublisher` (SLF4J, logs the standardized message as JSON) — this is the "mocked message queue" the assignment calls for; a real broker client would be a second implementation behind the same interface.
- Malformed/invalid provider payloads (missing `event_id`, an outcome outside {`1`,`X`,`2`}/{`home`,`draw`,`away`}, non-positive odds) fail validation at the controller/DTO boundary and are rejected with `400 Bad Request` — they are never mapped or published.

## Functional requirements

- Expose two HTTP POST endpoints, one per provider: `/provider-alpha/feed` and `/provider-beta/feed`.
- Each endpoint accepts a single message per request — either `ODDS_CHANGE` or `BET_SETTLEMENT` — in that provider's proprietary JSON shape (see Event schema).
- Parse the incoming payload according to the originating provider's format.
- Convert every parsed message into the standardized internal format (`StandardOddsChangeMessage` / `StandardBetSettlementMessage`).
- Publish the standardized message via `MessagePublisher` (mocked — logging is sufficient, no real broker required).
- Only the 1X2 market is in scope: `1` = home win, `X` = draw, `2` = away win.
- Reject malformed or invalid provider payloads with `400 Bad Request` instead of forwarding bad data downstream.

## Event schema

Two standardized message types, both carrying a common set of fields:

| Field | Type | Notes |
|---|---|---|
| `eventId` | String | From the provider's `event_id`, required |
| `provider` | enum | `PROVIDER_ALPHA` \| `PROVIDER_BETA` — origin, metadata only |
| `receivedAt` | Instant | Stamped on ingestion; not supplied by either provider |
| `market` | String | Always `"1X2"` for now |

**`StandardOddsChangeMessage`** (messageType `ODDS_CHANGE`) adds:
- `odds`: map of canonical outcome key → decimal odds, e.g. `{ "1": 2.0, "X": 3.1, "2": 3.8 }`

**`StandardBetSettlementMessage`** (messageType `BET_SETTLEMENT`) adds:
- `outcome`: one of `"1"`, `"X"`, `"2"`

Canonical outcome keys are always `"1"` / `"X"` / `"2"` (the 1X2 notation the brief itself defines), regardless of which provider a message came from.

**Provider → standard mapping**

| Provider payload | Maps to |
|---|---|
| ProviderAlpha `msg_type: "odds_update"`, `values.{1,X,2}` | `StandardOddsChangeMessage.odds` — keys pass through unchanged |
| ProviderAlpha `msg_type: "settlement"`, `outcome` | `StandardBetSettlementMessage.outcome` — passes through unchanged |
| ProviderBeta `type: "ODDS"`, `odds.{home,draw,away}` | `StandardOddsChangeMessage.odds` — `home→"1"`, `draw→"X"`, `away→"2"` |
| ProviderBeta `type: "SETTLEMENT"`, `result` | `StandardBetSettlementMessage.outcome` — `home→"1"`, `draw→"X"`, `away→"2"` |

## Functional invariants

- Every standardized message carries a non-blank `eventId` and a `provider`.
- Odds values, when present, are decimal odds `> 1.0`; anything else is invalid.
- `outcome`/`result` always normalizes to exactly one of `"1"`, `"X"`, `"2"`; unrecognized values are invalid, never passed through as-is.
- Each provider's raw payload is parsed only against that provider's own format — ProviderAlpha payloads are never parsed as ProviderBeta shapes or vice versa.
- The standardized schema is provider-agnostic: nothing downstream of `MessagePublisher` should need to know which provider a message came from to interpret `market`, `outcome`, or `odds` — `provider` is metadata only.
- `market` is always `"1X2"` — no other markets are in scope for this exercise.

## Build health invariants (must hold before every commit)

- `mvn clean verify` completes successfully — the project compiles and every unit test passes.
- No test is disabled/skipped without a comment explaining why.
- No unused imports, dead code, or commented-out code left behind.
- No use of methods annotated `@Deprecated` in the project's dependencies (e.g. `JsonNode.asText()` in Jackson 3.x — use `asString()` instead). Verify against the actual library version in use before assuming a method is deprecated; IDE hints can be stale or wrong.

## PR invariants (must hold for every pull request)

- Any PR that adds or changes a provider parser/mapper, or the standardized schema, includes or updates unit tests covering that parser and its mapping to the standard schema.
- The README stays accurate for any change to endpoints, request/response formats, or run/usage instructions.
- The build is green (see Build health invariants) before the PR is opened for review.
- Branch names and commit messages use short, descriptive names — never internal task/step identifiers (e.g. `T01`, `t01`). Those identifiers exist only inside `.specs/<problem>/tasks.md` and its `plans/` folder for internal tracking; they never leak into git history.

## Clarifying questions (before any non-trivial work)

Before drafting anything substantial — code, specs, plans, designs, docs, migrations, configuration, non-trivial refactors — ask 5–7 short clarifying questions about the decisions you would otherwise make by default. One decision = one question. If you have no real doubt about a particular choice, do not invent a question for it. Do not start writing until the user answers.

Scope:

- **Applies to:** new features, new files, new specs/designs/plans, schema or migration changes, architectural choices, behavior changes, anything where a reasonable person might choose differently than your default.
- **Does not apply to:** trivial edits the user has already specified (typo fixes, literal rename, an explicit one-line change), formatting, or carrying out an instruction the user has already pinned down.

When in doubt, ask. Cost of asking is low; cost of writing the wrong thing is high.

## Working with specifications

New problems are planned under `.specs/<problem-name>/` before any code is written. The workflow — folder layout and the `requirements.md` → `design.md` → `tasks.md` progression — lives in [`.specs/SPEC_WORKFLOW.md`](.specs/SPEC_WORKFLOW.md). Read it before drafting or editing any file under `.specs/`. The 5–7 clarifying-questions ritual (see section above) applies before every spec document.

## Working with PLAN.md

When an agent executes work tracked in `PLAN.md`, append a short result of execution at the end of every step. Keep it to 1–3 lines describing what was done, the outcome (pass/fail), and any follow-up needed. This keeps the plan self-documenting so the next agent can pick up cold.

## Negative invariants (guiding principles)

- Do no harm
- Do no lie
- Do not take what does not belong
- Do not accumulate more than necessary
- Conserve energy

## Positive invariants (guiding principles)

- Cleanliness of code and context
- Sufficiency of the solution
- Verification discipline
- Self-learning
- Serving something greater than the task
