# T02 — ProviderAlpha ingestion

Executes `tasks.md` § T02. Depends on: T01. Branch: `feed-normalization/provider-alpha`.

## File list (all new)

```
src/main/java/com/sporty/provider/alpha/ProviderAlphaMessage.java
src/main/java/com/sporty/provider/alpha/ProviderAlphaOddsChangeRequest.java
src/main/java/com/sporty/provider/alpha/ProviderAlphaOdds.java
src/main/java/com/sporty/provider/alpha/ProviderAlphaSettlementRequest.java
src/main/java/com/sporty/provider/alpha/ProviderAlphaMapper.java
src/main/java/com/sporty/provider/alpha/ProviderAlphaController.java
src/test/java/com/sporty/provider/alpha/ProviderAlphaMapperTest.java
src/test/java/com/sporty/provider/alpha/ProviderAlphaControllerTest.java
```

Visibility, per `design.md`'s encapsulation call-out: everything package-private except `ProviderAlphaController` (must be `public` for Spring to route to it). Tests live in the same package (`src/test/java/com/sporty/provider/alpha`) so they can reference the package-private types directly.

## Concrete class content

**`ProviderAlphaMessage.java`**
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "msg_type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProviderAlphaOddsChangeRequest.class, name = "odds_update"),
  @JsonSubTypes.Type(value = ProviderAlphaSettlementRequest.class, name = "settlement")
})
sealed interface ProviderAlphaMessage permits ProviderAlphaOddsChangeRequest, ProviderAlphaSettlementRequest {}
```

**`ProviderAlphaOddsChangeRequest.java`**
```java
record ProviderAlphaOddsChangeRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotNull @Valid @JsonProperty("values") ProviderAlphaOdds values
) implements ProviderAlphaMessage {}
```

**`ProviderAlphaOdds.java`**
```java
record ProviderAlphaOdds(
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("1") BigDecimal one,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("X") BigDecimal draw,
    @NotNull @DecimalMin(value = "1.0", inclusive = false) @JsonProperty("2") BigDecimal two
) {}
```

**`ProviderAlphaSettlementRequest.java`** — `@NotBlank` added alongside `@Pattern`: Bean Validation's `@Pattern` alone treats `null` as *valid*, so without `@NotBlank` a missing `outcome` would pass validation and throw an unhandled exception in the mapper (`500`) instead of the `400` that `requirements.md`'s validation ACs require.
```java
record ProviderAlphaSettlementRequest(
    @NotBlank @JsonProperty("event_id") String eventId,
    @NotBlank @Pattern(regexp = "^(1|X|2)$") @JsonProperty("outcome") String outcome
) implements ProviderAlphaMessage {}
```

**`ProviderAlphaMapper.java`**
```java
@Component
class ProviderAlphaMapper implements FeedProvider<ProviderAlphaMessage> {
  @Override
  public StandardMessage standardize(ProviderAlphaMessage raw) {
    return switch (raw) {
      case ProviderAlphaOddsChangeRequest req -> new StandardOddsChangeMessage(
          req.eventId(), FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2",
          Map.of(Outcome.HOME, req.values().one(), Outcome.DRAW, req.values().draw(), Outcome.AWAY, req.values().two()));
      case ProviderAlphaSettlementRequest req -> new StandardBetSettlementMessage(
          req.eventId(), FeedProviderId.PROVIDER_ALPHA, Instant.now(), "1X2", Outcome.fromCode(req.outcome()));
    };
  }
}
```
`ProviderAlphaMessage` is `sealed` with exactly these two `permits`, so the `switch` is exhaustive without a `default` branch (pattern-matching switch, Java 21+, fine under Java 25).

**`ProviderAlphaController.java`**
```java
@RestController
public class ProviderAlphaController {
  private final ProviderAlphaMapper mapper;
  private final MessagePublisher messagePublisher;

  public ProviderAlphaController(ProviderAlphaMapper mapper, MessagePublisher messagePublisher) {
    this.mapper = mapper;
    this.messagePublisher = messagePublisher;
  }

  @PostMapping("/provider-alpha/feed")
  public ResponseEntity<Void> ingest(@Valid @RequestBody ProviderAlphaMessage message) {
    StandardMessage standardMessage = mapper.standardize(message);
    messagePublisher.publish(standardMessage);
    return ResponseEntity.accepted().build();
  }
}
```

## Test wiring

`@WebMvcTest` does **not** auto-scan generic `@Component` beans — only MVC infrastructure plus the explicitly listed `controllers`. `ProviderAlphaMapper` is a plain `@Component`, so `ProviderAlphaControllerTest` must explicitly bring it in via `@Import`. Use the **real** mapper (not a mock) so the test gets true controller→mapper integration coverage while still asserting the exact published message via an `ArgumentCaptor`. Spring Boot 4.0 removed `@MockBean`/`@SpyBean` — use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito` instead.

```java
@WebMvcTest(controllers = ProviderAlphaController.class)
@Import(ProviderAlphaMapper.class)
class ProviderAlphaControllerTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean MessagePublisher messagePublisher;
  // ...
}
```
`receivedAt` (wall-clock generated) is asserted loosely — non-null, within a few seconds of `Instant.now()` — not for an exact value.

## Exact tests

**`ProviderAlphaMapperTest.java`**
- `standardize_oddsChangeRequest_mapsToStandardOddsChangeMessage()`
- `standardize_settlementRequest_mapsToStandardBetSettlementMessage()`

**`ProviderAlphaControllerTest.java`** — one test per DoD bullet; each `400` case also asserts `verifyNoInteractions(messagePublisher)` per the requirement's explicit "shall not publish a message":
- `oddsChangeRequest_returnsAcceptedAndPublishesStandardOddsChangeMessage()`
- `settlementRequest_returnsAcceptedAndPublishesStandardBetSettlementMessage()`
- `missingEventId_returnsBadRequestAndDoesNotPublish()`
- `oddsValueNotGreaterThanOne_returnsBadRequestAndDoesNotPublish()`
- `outcomeOutsideAllowedValues_returnsBadRequestAndDoesNotPublish()`
- `unrecognizedMsgType_returnsBadRequestAndDoesNotPublish()`
- `malformedJsonBody_returnsBadRequestAndDoesNotPublish()`
- `duplicateSettlementForSameEventId_bothReturnAcceptedAndBothPublish()` — posts the same `settlement` payload twice, asserts both responses are `202` and `verify(messagePublisher, times(2)).publish(any())`

## Refs

AC: `requirements.md` § ProviderAlpha ingestion (all 3), § Publishing (both, scoped to this endpoint), § Validation / error handling (all 4, scoped to ProviderAlpha's field names/allowed values), § Statelessness (all 3, scoped to ProviderAlpha).
Design: `design.md` § Provider request DTOs and parsing (ProviderAlpha block), § Validation rules (ProviderAlpha rows), § Component integration (`provider/alpha/*`).

## Definition of Done (from `tasks.md`, unchanged)

- `mvn clean verify` passes.
- All tests listed above pass.

## Open questions

None outstanding for this step (the `@NotBlank` addition above is a correction to `design.md`'s literal snippet, required to satisfy the already-agreed validation ACs — not a new decision).
