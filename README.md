# bet-feed-unifier

Standardization layer between third-party sports-betting feed providers and the rest of the platform. It exposes one HTTP POST endpoint per provider, parses that provider's proprietary JSON, normalizes it into a single internal schema (`StandardOddsChangeMessage` / `StandardBetSettlementMessage`), and publishes the result to a mocked downstream message queue (logged, not a real broker). Only the 1X2 market is in scope (`1` = home win, `X` = draw, `2` = away win).

## Prerequisites

Pick **one** of the two run paths below — you only need the tooling for the one you use.

- **Docker path:** Docker + Docker Compose v2, with the Docker daemon running.
- **Local/Maven path:** JDK 25. No local Maven install needed — the project ships the Maven Wrapper (`./mvnw`).

All commands below must be run from the project root (the directory containing `pom.xml` and `docker-compose.yml`).

## Running the service

### Path 1 — Docker

Make sure the Docker daemon is running first, then:

```bash
docker compose up --build
```

The service listens on `http://localhost:8080`. Standardized-message log lines appear in `docker compose logs -f`.

### Path 2a — Maven

```bash
./mvnw spring-boot:run
```

### Path 2b — Packaged jar

```bash
./mvnw clean package
java -jar target/bet-feed-unifier-0.0.1-SNAPSHOT.jar
```

Both local paths listen on `http://localhost:8080`; standardized-message log lines appear on the console.

## Usage

Each endpoint accepts a single message per request — either an odds change or a bet settlement — in that provider's own JSON shape, and responds `202 Accepted` with an empty body once the standardized message has been published. The published (logged) standardized message can be seen in the running `docker compose` console (or `docker compose logs -f`) for the Docker path, or in the running application's console for the Maven/packaged-jar path.

### ProviderAlpha — odds change

```bash
curl -i -X POST http://localhost:8080/provider-alpha/feed \
  -H "Content-Type: application/json" \
  -d '{ "msg_type": "odds_update", "event_id": "ev123", "values": { "1": 2.0, "X": 3.1, "2": 3.8 } }'
```

Response: `HTTP/1.1 202 Accepted`, empty body. Published (logged) message:

```json
{
  "messageType": "ODDS_CHANGE",
  "eventId": "ev123",
  "provider": "PROVIDER_ALPHA",
  "receivedAt": "2026-09-07T12:00:00Z",
  "market": "1X2",
  "odds": { "1": 2.0, "X": 3.1, "2": 3.8 }
}
```

### ProviderAlpha — settlement

```bash
curl -i -X POST http://localhost:8080/provider-alpha/feed \
  -H "Content-Type: application/json" \
  -d '{ "msg_type": "settlement", "event_id": "ev123", "outcome": "1" }'
```

Response: `HTTP/1.1 202 Accepted`, empty body. Published (logged) message:

```json
{
  "messageType": "BET_SETTLEMENT",
  "eventId": "ev123",
  "provider": "PROVIDER_ALPHA",
  "receivedAt": "2026-09-07T12:00:05Z",
  "market": "1X2",
  "outcome": "1"
}
```

### ProviderBeta — odds change

```bash
curl -i -X POST http://localhost:8080/provider-beta/feed \
  -H "Content-Type: application/json" \
  -d '{ "type": "ODDS", "event_id": "ev456", "odds": { "home": 1.95, "draw": 3.2, "away": 4.0 } }'
```

Response: `HTTP/1.1 202 Accepted`, empty body. Published (logged) message:

```json
{
  "messageType": "ODDS_CHANGE",
  "eventId": "ev456",
  "provider": "PROVIDER_BETA",
  "receivedAt": "2026-09-07T12:00:10Z",
  "market": "1X2",
  "odds": { "1": 1.95, "X": 3.2, "2": 4.0 }
}
```

### ProviderBeta — settlement

```bash
curl -i -X POST http://localhost:8080/provider-beta/feed \
  -H "Content-Type: application/json" \
  -d '{ "type": "SETTLEMENT", "event_id": "ev456", "result": "away" }'
```

Response: `HTTP/1.1 202 Accepted`, empty body. Published (logged) message:

```json
{
  "messageType": "BET_SETTLEMENT",
  "eventId": "ev456",
  "provider": "PROVIDER_BETA",
  "receivedAt": "2026-09-07T12:00:15Z",
  "market": "1X2",
  "outcome": "2"
}
```

(`receivedAt` is stamped on ingestion, so it reflects the actual time you send the request rather than the illustrative value above.)

### Invalid request example

Odds values must be decimals greater than `1.0`. A value of `0.5` is rejected:

```bash
curl -i -X POST http://localhost:8080/provider-alpha/feed \
  -H "Content-Type: application/json" \
  -d '{ "msg_type": "odds_update", "event_id": "ev123", "values": { "1": 0.5, "X": 3.1, "2": 3.8 } }'
```

Response: `HTTP/1.1 400 Bad Request`, `Content-Type: application/problem+json`. No message is published. The same applies to any malformed/invalid payload on either endpoint — missing `event_id`, an outcome outside the provider's allowed values, or a body that isn't valid JSON.
