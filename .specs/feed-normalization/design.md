# Design — Feed Normalization

Implements `requirements.md` in this folder. Package root: `com.sporty` (per `CLAUDE.md` § Project map).

## API contract

| Endpoint | Method | Consumes | Success | Failure |
|---|---|---|---|---|
| `/provider-alpha/feed` | POST | `application/json` | `202 Accepted`, empty body | `400 Bad Request`, `application/problem+json` |
| `/provider-beta/feed` | POST | `application/json` | `202 Accepted`, empty body | `400 Bad Request`, `application/problem+json` |

No authentication (per `requirements.md` § Access). No request/response versioning — out of scope.

Failure body uses Spring's built-in `ProblemDetail` (RFC 7807): `type`, `title`, `status`, `detail`, `instance` fields, with `detail` carrying a human-readable summary of what failed (e.g. which field, or "unrecognized message shape").

## Standardized schema (`domain/`)

```java
public sealed interface StandardMessage
    permits StandardOddsChangeMessage, StandardBetSettlementMessage {
  String eventId();
  FeedProviderId provider();
  Instant receivedAt();
  String market(); // always "1X2"
}

public record StandardOddsChangeMessage(
    String eventId, FeedProviderId provider, Instant receivedAt, String market,
    @JsonSerialize(keyUsing = OutcomeKeySerializer.class) Map<Outcome, BigDecimal> odds
) implements StandardMessage {}

public record StandardBetSettlementMessage(
    String eventId, FeedProviderId provider, Instant receivedAt, String market,
    Outcome outcome
) implements StandardMessage {}

public enum Outcome {
  HOME("1"), DRAW("X"), AWAY("2");
  @JsonValue public String code() { ... } // serializes back to "1"/"X"/"2"
  public static Outcome fromCode(String code) { ... } // used by each provider's FeedNormalizer
}

// Jackson does not honor @JsonValue for enum Map *keys* (only plain values) — see
// https://github.com/FasterXML/jackson-databind/issues/1535 and related open issues.
// Without this, `odds` would serialize with enum-name keys ("HOME") instead of "1"/"X"/"2".
public final class OutcomeKeySerializer extends ValueSerializer<Outcome> {
  @Override
  public void serialize(Outcome value, JsonGenerator gen, SerializationContext context) {
    gen.writeName(value.code());
  }
}

public enum FeedProviderId { PROVIDER_ALPHA, PROVIDER_BETA }
```

`StandardMessage` carries `@JsonTypeInfo(use = NAME, include = PROPERTY, property = "messageType")` with `@JsonSubTypes` mapping `StandardOddsChangeMessage → "ODDS_CHANGE"` and `StandardBetSettlementMessage → "BET_SETTLEMENT"`, so the serialized JSON matches the `messageType` field already shown in `requirements.md`'s examples without a redundant field on each record.

`outcome` (a plain enum value, not a map key) serializes through `Outcome`'s `@JsonValue` automatically. `odds` map keys do **not** — Jackson only honors `@JsonValue` for plain enum values, not enum-typed `Map` keys, so `StandardOddsChangeMessage.odds` is explicitly annotated with `@JsonSerialize(keyUsing = OutcomeKeySerializer.class)` above. With that annotation, both fields produce exactly the `{ "1": ..., "X": ..., "2": ... }` / `"1"` shapes from `requirements.md` and `CLAUDE.md` § Event schema — the enum plus key serializer is an internal type-safety improvement, not a schema change. No `KeyDeserializer` is needed since nothing in this service deserializes a `StandardMessage` back from JSON.

**Jackson generation note (confirmed while implementing T01):** Spring Boot 4.0.3's default Jackson stack is **Jackson 3** (`tools.jackson.core`/`tools.jackson.databind`), not classic Jackson 2 — `ObjectMapper`, `ValueSerializer` (renamed from `JsonSerializer`), `SerializationContext` (renamed from `SerializerProvider`), `JsonGenerator`, and `@JsonSerialize` all live under `tools.jackson.*`, and `JsonGenerator.writeFieldName(...)` is renamed to `writeName(...)`. Jackson's exceptions (`JacksonException`) are now unchecked (`RuntimeException`), so no `throws`/try-catch is needed around `ObjectMapper.writeValueAsString(...)`. The annotations-only module (`@JsonValue`, `@JsonProperty`, `@JsonTypeInfo`, `@JsonSubTypes`) is unaffected — it stays on `com.fasterxml.jackson.annotation` (version 2.20) for Jackson 2/3 interop, so every annotation used in this document and in the provider DTOs below is correct as written.

**Provider → standard mapping** (unchanged from `CLAUDE.md` § Event schema): ProviderAlpha's `1`/`X`/`2` keys map to `Outcome.HOME/DRAW/AWAY` directly; ProviderBeta's `home`/`draw`/`away` map to the same enum constants.

## Provider request DTOs and parsing (`provider/alpha/`, `provider/beta/`)

Both message types share one endpoint per provider, distinguished by that provider's own discriminator field. Modeled as sealed interfaces with Jackson polymorphic deserialization, resolved directly at the `@RequestBody` boundary — no manual branching:

```java
// provider/alpha/ProviderAlphaMessage.java
@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "msg_type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProviderAlphaOddsChangeRequest.class, name = "odds_update"),
  @JsonSubTypes.Type(value = ProviderAlphaSettlementRequest.class, name = "settlement")
})
public sealed interface ProviderAlphaMessage
    permits ProviderAlphaOddsChangeRequest, ProviderAlphaSettlementRequest {}

public record ProviderAlphaOddsChangeRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotNull @Valid @JsonProperty("values") ProviderAlphaOdds values
) implements ProviderAlphaMessage {}

public record ProviderAlphaOdds(
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("1") BigDecimal one,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("X") BigDecimal draw,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("2") BigDecimal two
) {}

public record ProviderAlphaSettlementRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @Pattern(regexp = "^(1|X|2)$") @JsonProperty("outcome") String outcome
) implements ProviderAlphaMessage {}
```

`provider/beta/` mirrors this shape: `@JsonTypeInfo` keyed on `"type"` with values `"ODDS"`/`"SETTLEMENT"`, fields `event_id`, `odds.{home,draw,away}`, and `result` with `@Pattern(regexp = "^(home|draw|away)$")`.

An unrecognized `msg_type`/`type` value fails polymorphic type resolution (Jackson `InvalidTypeIdException`, wrapped in `HttpMessageNotReadableException` during `@RequestBody` binding) — handled the same way as any other malformed body (see Validation rules).

## Validation rules

| Field | Rule | Violation → |
|---|---|---|
| `event_id` (both providers, both message types) | `@NotBlank` | `400`, field-level detail |
| ProviderAlpha `values.{1,X,2}` | `@NotNull @DecimalMin("1.0", inclusive=false)` | `400`, field-level detail |
| ProviderAlpha `outcome` | `@Pattern("^(1\|X\|2)$")` | `400`, field-level detail |
| ProviderBeta `odds.{home,draw,away}` | `@NotNull @DecimalMin("1.0", inclusive=false)` | `400`, field-level detail |
| ProviderBeta `result` | `@Pattern("^(home\|draw\|away)$")` | `400`, field-level detail |
| `msg_type` / `type` (discriminator) | Must match a known subtype | `400`, "unrecognized message shape" |
| Request body | Must be valid JSON matching the receiving endpoint's provider shape | `400`, "malformed request body" |

Enforcement: Jakarta Bean Validation annotations on the records above (`spring-boot-starter-validation`), triggered via `@Valid @RequestBody` on each controller method. A single `@RestControllerAdvice` (`error/FeedErrorHandler`, extending `ResponseEntityExceptionHandler` for explicit, version-independent control rather than relying on default `ProblemDetail` auto-enablement) translates:
- `MethodArgumentNotValidException` → `400` `ProblemDetail`, `detail` lists the violated field(s).
- `HttpMessageNotReadableException` (malformed JSON, unresolvable discriminator) → `400` `ProblemDetail`, generic `detail`.

No cross-field or stateful validation exists — every request is validated independently, consistent with `requirements.md` § Statelessness.

## Component integration (package map)

```
com.sporty
├── domain/
│   ├── StandardMessage.java
│   ├── StandardOddsChangeMessage.java
│   ├── StandardBetSettlementMessage.java
│   ├── Outcome.java
│   └── FeedProviderId.java
├── provider/
│   ├── FeedNormalizer.java                     # port: StandardMessage normalize(T raw)
│   ├── alpha/
│   │   ├── ProviderAlphaController.java         # @PostMapping("/provider-alpha/feed")
│   │   ├── ProviderAlphaMessage.java            # sealed interface
│   │   ├── ProviderAlphaOddsChangeRequest.java
│   │   ├── ProviderAlphaOdds.java
│   │   ├── ProviderAlphaSettlementRequest.java
│   │   └── ProviderAlphaFeedNormalizer.java     # implements FeedNormalizer<ProviderAlphaMessage>
│   └── beta/                                    # mirrors alpha/, keyed on ProviderBetaMessage
├── publish/
│   ├── MessagePublisher.java                    # port: void publish(StandardMessage)
│   └── LoggingMessagePublisher.java             # SLF4J, logs the message as JSON via ObjectMapper
├── error/
│   └── FeedErrorHandler.java                    # @RestControllerAdvice → ProblemDetail
└── BetFeedUnifierApplication.java
```

Flow: `HTTP request → Controller (@Valid @RequestBody resolves the sealed type) → FeedNormalizer.normalize(...) → MessagePublisher.publish(...) → 202 Accepted`.

**Provider isolation:** `provider/alpha/*` never imports `provider/beta/*` (or vice versa) — each package only depends on its own DTOs plus `domain/`. Each `*FeedNormalizer` is a separate Spring bean, injected only into its own provider's controller. Adding a third provider means adding a new `provider/<name>/` package and wiring it into a new controller; no existing package changes.

**Maven dependencies:** `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test` (JUnit 5 + `MockMvc`, test scope), and `spring-boot-starter-webmvc-test` (test scope — confirmed while implementing T01: Spring Boot 4 extracted `@WebMvcTest` and related web-slice test support out of `spring-boot-starter-test` into this separate starter; the annotation itself also moved package, from `org.springframework.boot.test.autoconfigure.web.servlet` to `org.springframework.boot.webmvc.test.autoconfigure`). No persistence starter, no messaging starter, no Lombok — consistent with the decisions in `requirements.md` and the clarifications above.

## `CLAUDE.md` alignment

| Invariant (`CLAUDE.md`) | How this design honors it |
|---|---|
| Every standardized message carries a non-blank `eventId` and a `provider` | Both standardized records require non-null `eventId`/`provider` in their canonical constructor; upstream `@NotBlank` on `event_id` prevents a blank value ever reaching the mapper |
| Odds `> 1.0` | `@DecimalMin(value = "1.0", inclusive = false)` on every provider odds field |
| `outcome`/`result` always normalizes to `"1"`/`"X"`/`"2"` | Provider-specific `@Pattern` restricts raw input to that provider's own allowed values; each `FeedNormalizer` performs the fixed, total mapping into `Outcome` |
| Each provider parsed only against its own format | Two disjoint sealed interfaces (`ProviderAlphaMessage`, `ProviderBetaMessage`), one per controller; no shared request type |
| Standardized schema is provider-agnostic | `publish/` and `MessagePublisher` only ever see `domain.StandardMessage`; provider-specific types never cross into `publish/` |
| `market` always `"1X2"` | Hardcoded literal set by each `FeedNormalizer`; no other value is representable in the current schema |
| `mvn clean verify` green, all tests pass | Unit tests per `FeedNormalizer` (mapping correctness, both providers) + `@WebMvcTest` slice tests per `Controller` (status codes, validation failures) cover every AC in `requirements.md` |
| PR invariant: tests for changed provider mapping + README current | `tasks.md` will scope each provider's mapper + controller as its own step with its own tests |

## Out of scope (carried from `requirements.md`, unchanged)

Real broker integration, persistence, deduplication/ordering guarantees, authentication, non-1X2 markets, rate limiting/retries, a consumer/read API.
