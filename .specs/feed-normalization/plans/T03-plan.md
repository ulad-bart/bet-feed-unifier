# T03 — ProviderBeta ingestion

Executes `tasks.md` § T03. Depends on: T01. Branch: `feed-normalization/provider-beta`.

Mirrors `T02-plan.md`'s structure and detail level exactly, scoped to ProviderBeta's own field names and endpoint.

## File list (all new)

```
src/main/java/com/sporty/provider/beta/ProviderBetaMessage.java
src/main/java/com/sporty/provider/beta/ProviderBetaOddsChangeRequest.java
src/main/java/com/sporty/provider/beta/ProviderBetaOdds.java
src/main/java/com/sporty/provider/beta/ProviderBetaSettlementRequest.java
src/main/java/com/sporty/provider/beta/ProviderBetaMapper.java
src/main/java/com/sporty/provider/beta/ProviderBetaController.java
src/test/java/com/sporty/provider/beta/ProviderBetaMapperTest.java
src/test/java/com/sporty/provider/beta/ProviderBetaControllerTest.java
```
(`ProviderBetaOddsChangeRequest` — named for symmetry with `ProviderAlphaOddsChangeRequest`; `tasks.md` § T03 was corrected to this name.)

Visibility: everything package-private except `ProviderBetaController` (public).

## Concrete class content

**`ProviderBetaMessage.java`**
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProviderBetaOddsChangeRequest.class, name = "ODDS"),
  @JsonSubTypes.Type(value = ProviderBetaSettlementRequest.class, name = "SETTLEMENT")
})
sealed interface ProviderBetaMessage permits ProviderBetaOddsChangeRequest, ProviderBetaSettlementRequest {}
```

**`ProviderBetaOddsChangeRequest.java`**
```java
record ProviderBetaOddsChangeRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotNull @Valid @JsonProperty("odds") ProviderBetaOdds odds
) implements ProviderBetaMessage {}
```

**`ProviderBetaOdds.java`**
```java
record ProviderBetaOdds(
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("home") BigDecimal home,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("draw") BigDecimal draw,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("away") BigDecimal away
) {}
```

**`ProviderBetaSettlementRequest.java`** — same `@NotBlank`-alongside-`@Pattern` fix as ProviderAlpha's `outcome` (see `T02-plan.md`'s rationale — `@Pattern` alone allows `null`).
```java
record ProviderBetaSettlementRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotBlank @Pattern(regexp = "^(home|draw|away)$") @JsonProperty("result") String result
) implements ProviderBetaMessage {}
```

**`ProviderBetaMapper.java`** — the `home`/`draw`/`away` → `Outcome` mapping is kept **provider-local** (not added to `domain.Outcome`), preserving the "standardized schema is provider-agnostic" invariant: `Outcome` only ever knows about `"1"/"X"/"2"` codes.
```java
@Component
class ProviderBetaMapper implements FeedProvider<ProviderBetaMessage> {
  @Override
  public StandardMessage standardize(ProviderBetaMessage raw) {
    return switch (raw) {
      case ProviderBetaOddsChangeRequest req -> new StandardOddsChangeMessage(
          req.eventId(), FeedProviderId.PROVIDER_BETA, Instant.now(), "1X2",
          Map.of(Outcome.HOME, req.odds().home(), Outcome.DRAW, req.odds().draw(), Outcome.AWAY, req.odds().away()));
      case ProviderBetaSettlementRequest req -> new StandardBetSettlementMessage(
          req.eventId(), FeedProviderId.PROVIDER_BETA, Instant.now(), "1X2", mapResult(req.result()));
    };
  }

  private static Outcome mapResult(String result) {
    return switch (result) {
      case "home" -> Outcome.HOME;
      case "draw" -> Outcome.DRAW;
      case "away" -> Outcome.AWAY;
      default -> throw new IllegalArgumentException("Unknown ProviderBeta result: " + result); // unreachable post-@Pattern
    };
  }
}
```

**`ProviderBetaController.java`** — mirrors `ProviderAlphaController` exactly, `@PostMapping("/provider-beta/feed")`.
```java
@RestController
public class ProviderBetaController {
  private final ProviderBetaMapper mapper;
  private final MessagePublisher messagePublisher;

  public ProviderBetaController(ProviderBetaMapper mapper, MessagePublisher messagePublisher) {
    this.mapper = mapper;
    this.messagePublisher = messagePublisher;
  }

  @PostMapping("/provider-beta/feed")
  public ResponseEntity<Void> ingest(@Valid @RequestBody ProviderBetaMessage message) {
    StandardMessage standardMessage = mapper.standardize(message);
    messagePublisher.publish(standardMessage);
    return ResponseEntity.accepted().build();
  }
}
```

## Test wiring

Same approach as `T02-plan.md`: `@WebMvcTest(controllers = ProviderBetaController.class) @Import(ProviderBetaMapper.class)`, real mapper + `@MockitoBean MessagePublisher messagePublisher`.

## Exact tests

**`ProviderBetaMapperTest.java`**
- `standardize_oddsRequest_mapsToStandardOddsChangeMessage()`
- `standardize_settlementRequest_mapsToStandardBetSettlementMessage()`

**`ProviderBetaControllerTest.java`**
- `oddsRequest_returnsAcceptedAndPublishesStandardOddsChangeMessage()`
- `settlementRequest_returnsAcceptedAndPublishesStandardBetSettlementMessage()`
- `missingEventId_returnsBadRequestAndDoesNotPublish()`
- `oddsValueNotGreaterThanOne_returnsBadRequestAndDoesNotPublish()`
- `resultOutsideAllowedValues_returnsBadRequestAndDoesNotPublish()`
- `unrecognizedType_returnsBadRequestAndDoesNotPublish()`
- `malformedJsonBody_returnsBadRequestAndDoesNotPublish()`
- `duplicateSettlementForSameEventId_bothReturnAcceptedAndBothPublish()`

## Refs

AC: `requirements.md` § ProviderBeta ingestion (all 3), § Publishing / § Validation / § Statelessness (same shared bullets as T02, scoped to ProviderBeta).
Design: `design.md` § Provider request DTOs and parsing (ProviderBeta block), § Validation rules (ProviderBeta rows), § Component integration (`provider/beta/*`).

## Definition of Done (from `tasks.md`, unchanged)

- `mvn clean verify` passes.
- All tests listed above pass.

## Open questions

None outstanding for this step.
