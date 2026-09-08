# Requirements — Feed Normalization

## Problem

Sporty Group ingests real-time sports-betting data from third-party feed providers, each using its own proprietary message format. This service is the standardization layer: it exposes one HTTP POST endpoint per provider, parses `ODDS_CHANGE` and `BET_SETTLEMENT` messages in that provider's own shape, converts them into a single standardized internal format, and forwards the result to a message queue (mocked — logging is sufficient) so the rest of the platform can consume feed data in one consistent shape regardless of which provider it came from.

Two providers are in scope for this exercise — ProviderAlpha and ProviderBeta — both currently limited to the 1X2 market (`1` = home win, `X` = draw, `2` = away win). Source brief: `.specs/files/bet-feed-task.pdf`.

## Example request / response

**ProviderAlpha — `POST /provider-alpha/feed`**

```json
// request (ODDS_CHANGE)
{ "msg_type": "odds_update", "event_id": "ev123", "values": { "1": 2.0, "X": 3.1, "2": 3.8 } }
```
```json
// standardized message published via MessagePublisher
{
  "messageType": "ODDS_CHANGE",
  "eventId": "ev123",
  "provider": "PROVIDER_ALPHA",
  "receivedAt": "2026-09-07T12:00:00Z",
  "market": "1X2",
  "odds": { "1": 2.0, "X": 3.1, "2": 3.8 }
}
```
Response: `202 Accepted`, empty body.

```json
// request (BET_SETTLEMENT)
{ "msg_type": "settlement", "event_id": "ev123", "outcome": "1" }
```
```json
// standardized message published via MessagePublisher
{
  "messageType": "BET_SETTLEMENT",
  "eventId": "ev123",
  "provider": "PROVIDER_ALPHA",
  "receivedAt": "2026-09-07T12:00:05Z",
  "market": "1X2",
  "outcome": "1"
}
```
Response: `202 Accepted`, empty body.

**ProviderBeta — `POST /provider-beta/feed`**

```json
// request (ODDS_CHANGE)
{ "type": "ODDS", "event_id": "ev456", "odds": { "home": 1.95, "draw": 3.2, "away": 4.0 } }
```
```json
// standardized message published via MessagePublisher
{
  "messageType": "ODDS_CHANGE",
  "eventId": "ev456",
  "provider": "PROVIDER_BETA",
  "receivedAt": "2026-09-07T12:00:10Z",
  "market": "1X2",
  "odds": { "1": 1.95, "X": 3.2, "2": 4.0 }
}
```
Response: `202 Accepted`, empty body.

```json
// request (BET_SETTLEMENT)
{ "type": "SETTLEMENT", "event_id": "ev456", "result": "away" }
```
```json
// standardized message published via MessagePublisher
{
  "messageType": "BET_SETTLEMENT",
  "eventId": "ev456",
  "provider": "PROVIDER_BETA",
  "receivedAt": "2026-09-07T12:00:15Z",
  "market": "1X2",
  "outcome": "2"
}
```
Response: `202 Accepted`, empty body.

**Invalid request (either endpoint):** `400 Bad Request`. Exact error body shape is a `design.md` concern, not fixed here.

## Acceptance criteria

This service has a single external persona (an upstream feed provider posting HTTP requests) and one internal capability (standardize + publish), so criteria below are grouped by capability area rather than by persona — adapted per `SPEC_WORKFLOW.md`'s "adapt to the problem at hand."

### ProviderAlpha ingestion

- When a valid `odds_update` message is POSTed to `/provider-alpha/feed`, the system shall standardize it into a `StandardOddsChangeMessage` with `provider` = `PROVIDER_ALPHA`.
- When a valid `settlement` message is POSTed to `/provider-alpha/feed`, the system shall standardize it into a `StandardBetSettlementMessage` with `provider` = `PROVIDER_ALPHA`.
- The system shall map ProviderAlpha's `values`/`outcome` keys (`1`, `X`, `2`) to the standardized schema's canonical outcome keys unchanged.

### ProviderBeta ingestion

- When a valid `ODDS` message is POSTed to `/provider-beta/feed`, the system shall standardize it into a `StandardOddsChangeMessage` with `provider` = `PROVIDER_BETA`.
- When a valid `SETTLEMENT` message is POSTed to `/provider-beta/feed`, the system shall standardize it into a `StandardBetSettlementMessage` with `provider` = `PROVIDER_BETA`.
- The system shall map ProviderBeta's `home`/`draw`/`away` keys to the canonical outcome keys `1`/`X`/`2` respectively.

### Standardized schema

- The system shall produce exactly one of two standardized message types for every accepted request: `StandardOddsChangeMessage` or `StandardBetSettlementMessage`.
- The system shall stamp every standardized message with `eventId`, `provider`, `receivedAt`, and `market` = `"1X2"`.
- The system shall represent all odds and outcome values using the canonical keys `"1"`, `"X"`, `"2"`, regardless of which provider's own key names the source message used.

### Publishing

- When a message has been successfully standardized, the system shall publish it via `MessagePublisher` before responding to the request.
- When publishing succeeds, the system shall respond `202 Accepted` with an empty body.

### Validation / error handling

- If a request to either endpoint is missing `event_id`, then the system shall respond `400 Bad Request` and shall not publish a message.
- If a settlement message's outcome value is not one of the provider's own allowed values (`1`/`X`/`2` for ProviderAlpha, `home`/`draw`/`away` for ProviderBeta), then the system shall respond `400 Bad Request` and shall not publish a message.
- If an odds-change message contains an odds value that is not a decimal greater than `1.0`, then the system shall respond `400 Bad Request` and shall not publish a message.
- If the request body is not valid JSON, or does not match the receiving endpoint's known provider message shape, then the system shall respond `400 Bad Request` and shall not publish a message.

### Statelessness (duplicates and ordering)

- The system shall treat every incoming message independently and shall not retain state about prior messages for the same `event_id`.
- When more than one `BET_SETTLEMENT` is received for the same `event_id`, the system shall standardize and publish each one as received, without rejecting or deduplicating.
- When an `ODDS_CHANGE` is received for an `event_id` a prior `BET_SETTLEMENT` already settled, the system shall standardize and publish it like any other odds update, without special-casing the ordering.

### Access

- The system shall accept requests to both provider endpoints without requiring authentication or authorization.

## Out of scope

- A real message broker integration (Kafka, RabbitMQ, etc.) — only a mocked/logging `MessagePublisher`.
- Persistence of received or standardized messages — no database, no message history, no replay.
- Deduplication, conflict resolution, or cross-message ordering guarantees for the same event.
- Authentication/authorization on the feed endpoints.
- Markets other than 1X2.
- Rate limiting, retries, backpressure, or delivery guarantees for the mocked queue.
- A consumer/read API for standardized messages — this service only produces them.

## Open questions

None — all decisions needed to proceed to `design.md` were resolved during clarification (folder naming, duplicate-settlement handling, message-ordering handling, authentication, and the success-response contract).
