# T01 — Project foundation: domain model, ports, error handling

Executes `tasks.md` § T01. Depends on: none. Branch: `feed-normalization/foundation`.

## File list (all new)

```
pom.xml
.gitignore
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
src/main/java/com/sporty/BetFeedUnifierApplication.java
src/main/java/com/sporty/domain/FeedProviderId.java
src/main/java/com/sporty/domain/Outcome.java
src/main/java/com/sporty/domain/OutcomeKeySerializer.java
src/main/java/com/sporty/domain/StandardMessage.java
src/main/java/com/sporty/domain/StandardOddsChangeMessage.java
src/main/java/com/sporty/domain/StandardBetSettlementMessage.java
src/main/java/com/sporty/provider/FeedProvider.java
src/main/java/com/sporty/publish/MessagePublisher.java
src/main/java/com/sporty/publish/LoggingMessagePublisher.java
src/main/java/com/sporty/error/FeedErrorHandler.java
src/test/java/com/sporty/domain/OutcomeTest.java
src/test/java/com/sporty/domain/StandardMessageJacksonTest.java
src/test/java/com/sporty/publish/LoggingMessagePublisherTest.java
src/test/java/com/sporty/error/FeedErrorHandlerTest.java
src/test/java/com/sporty/error/ProbeController.java
```

`mvnw`/`mvnw.cmd`/`.mvn/wrapper/...` aren't named in `tasks.md`'s scope text, but `T04`'s DoD ("`./mvnw spring-boot:run` and the packaged jar both work") depends on the wrapper existing and no other step provides it — generate it here (`mvn -N wrapper:wrapper -Dmaven=3.9.9`, or the current 3.9.x patch). A `.gitignore` (at minimum `target/`) is likewise an implied part of "Maven scaffold."

## pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
    <relativePath/>
  </parent>

  <groupId>com.sporty</groupId>
  <artifactId>bet-feed-unifier</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>bet-feed-unifier</name>
  <description>Feed normalization service — Sporty Group BE take-home assignment</description>
  <packaging>jar</packaging>

  <properties>
    <java.version>25</java.version>
  </properties>

  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
```

`4.0.3` is the latest confirmed patch on the Spring Boot `4.0.x` line (verified against `4.0.0` GA 2025-11-20, `4.0.2` 2026-01-22, `4.0.3` 2026-02-19) — confirm no newer `4.0.x` patch exists before running `mvn` for real, and use it if so. `<java.version>25</java.version>` maps to `maven.compiler.release=25` via the parent POM's property binding.

## Concrete class content

**`BetFeedUnifierApplication.java`** — standard `@SpringBootApplication` + `main` calling `SpringApplication.run(BetFeedUnifierApplication.class, args)`.

**`domain/FeedProviderId.java`**
```java
public enum FeedProviderId { PROVIDER_ALPHA, PROVIDER_BETA }
```

**`domain/Outcome.java`**
```java
public enum Outcome {
  HOME("1"), DRAW("X"), AWAY("2");

  private final String code;
  Outcome(String code) { this.code = code; }

  @JsonValue
  public String code() { return code; }

  public static Outcome fromCode(String code) {
    for (Outcome o : values()) {
      if (o.code.equals(code)) return o;
    }
    throw new IllegalArgumentException("Unknown outcome code: " + code);
  }
}
```

**`domain/OutcomeKeySerializer.java`** — required so `Map<Outcome, BigDecimal>` serializes with `"1"/"X"/"2"` keys; see `design.md` § Standardized schema for why `@JsonValue` alone doesn't cover Map keys. Uses **Jackson 3** types (`tools.jackson.*`) — Spring Boot 4.0.3's default Jackson stack, confirmed by inspecting the resolved dependency tree, not classic Jackson 2 (`com.fasterxml.jackson.databind`). `JsonSerializer`/`SerializerProvider` are renamed to `ValueSerializer`/`SerializationContext`, and `writeFieldName` is renamed to `writeName`:
```java
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public final class OutcomeKeySerializer extends ValueSerializer<Outcome> {
  @Override
  public void serialize(Outcome value, JsonGenerator gen, SerializationContext context) {
    gen.writeName(value.code());
  }
}
```
(No `throws IOException`/`JacksonException` needed — Jackson 3's `JacksonException` is unchecked.)

**`domain/StandardMessage.java`**
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "messageType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StandardOddsChangeMessage.class, name = "ODDS_CHANGE"),
  @JsonSubTypes.Type(value = StandardBetSettlementMessage.class, name = "BET_SETTLEMENT")
})
public sealed interface StandardMessage permits StandardOddsChangeMessage, StandardBetSettlementMessage {
  String eventId();
  FeedProviderId provider();
  Instant receivedAt();
  String market();
}
```

**`domain/StandardOddsChangeMessage.java`** — `@JsonSerialize` is `tools.jackson.databind.annotation.JsonSerialize` (Jackson 3), not the classic `com.fasterxml.jackson.databind.annotation` package.
```java
public record StandardOddsChangeMessage(
    String eventId,
    FeedProviderId provider,
    Instant receivedAt,
    String market,
    @JsonSerialize(keyUsing = OutcomeKeySerializer.class) Map<Outcome, BigDecimal> odds
) implements StandardMessage {}
```

**`domain/StandardBetSettlementMessage.java`**
```java
public record StandardBetSettlementMessage(
    String eventId, FeedProviderId provider, Instant receivedAt, String market, Outcome outcome
) implements StandardMessage {}
```
(`outcome` is a plain enum value, not a Map key, so it serializes through `@JsonValue` with no extra annotation.)

**`provider/FeedProvider.java`**
```java
public interface FeedProvider<T> {
  StandardMessage standardize(T raw);
}
```

**`publish/MessagePublisher.java`**
```java
public interface MessagePublisher {
  void publish(StandardMessage message);
}
```

**`publish/LoggingMessagePublisher.java`** — `ObjectMapper` here is `tools.jackson.databind.ObjectMapper` (Jackson 3). No try/catch: `writeValueAsString` throws the now-unchecked `JacksonException`, and Jackson 3 has no `JsonProcessingException` class at all.
```java
@Component
public class LoggingMessagePublisher implements MessagePublisher {
  private static final Logger log = LoggerFactory.getLogger(LoggingMessagePublisher.class);
  private final ObjectMapper objectMapper;

  public LoggingMessagePublisher(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

  @Override
  public void publish(StandardMessage message) {
    log.info("Publishing standardized message: {}", objectMapper.writeValueAsString(message));
  }
}
```
`ObjectMapper` is auto-configured by `spring-boot-starter-web`'s Jackson auto-configuration — no extra bean needed.

**`error/FeedErrorHandler.java`** — explicit override, not relying on default `ProblemDetail` auto-enablement. Signature confirmed against current Spring Framework 7 javadoc (`HttpStatusCode status`, returns `ResponseEntity<Object>`):
```java
@RestControllerAdvice
public class FeedErrorHandler extends ResponseEntityExceptionHandler {

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String detail = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
        .collect(Collectors.joining("; "));
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Malformed request body or unrecognized message shape");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
  }
}
```

## Tests

**`OutcomeTest.java`**
- `code_returnsCanonicalString()`
- `fromCode_returnsMatchingOutcome()`
- `fromCode_unknownCode_throwsIllegalArgumentException()`

**`StandardMessageJacksonTest.java`** (`@JsonTest` or a plain `new ObjectMapper()`)
- `oddsChangeMessage_serializesMessageTypeAsOddsChange()`
- `betSettlementMessage_serializesMessageTypeAsBetSettlement()`
- `oddsChangeMessage_serializesOddsMapWithCanonicalStringKeys()` — assert via `objectMapper.readTree(json).get("odds")` has fields `"1"`, `"X"`, `"2"` (assert presence/value per key, **not** exact string equality on the whole JSON — `Map.of(...)` iteration order is unspecified, so whole-string assertions would be flaky)
- `betSettlementMessage_serializesOutcomeAsCanonicalString()`

**`LoggingMessagePublisherTest.java`**
- `publish_logsValidJsonMatchingStandardizedSchema()` — attach a Logback `ListAppender<ILoggingEvent>` to `(Logger) LoggerFactory.getLogger(LoggingMessagePublisher.class)` (available transitively via `spring-boot-starter-logging`), call `publish(...)`, extract the logged JSON substring, `objectMapper.readTree(...)` it and assert expected fields

**`FeedErrorHandlerTest.java`** (plus a sibling `ProbeController.java` in the same test package)
- `@WebMvcTest(controllers = ProbeController.class) @Import(FeedErrorHandler.class)` — `@WebMvcTest` is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (Spring Boot 4 moved it out of `spring-boot-starter-test` into the separate `spring-boot-starter-webmvc-test` starter, see pom.xml above). Explicit `@Import` rather than relying on `@WebMvcTest`'s implicit advice auto-detection.
- `ProbeController` is a **top-level** test-only class (`@RestController`, one `@PostMapping("/probe")` taking `@Valid @RequestBody ProbeRequest(@NotBlank String value)`) — a nested `static class ProbeController` inside the test class did not get registered as a handler under this Spring Boot version (`@WebMvcTest(controllers = ...)` resolved it but the endpoint 404'd, i.e. no `HandlerMapping` matched); a standalone top-level class is the reliable pattern here.
- `methodArgumentNotValid_returnsBadRequestProblemDetail()` — POST `{}` → `400`, `Content-Type: application/problem+json`, `$.detail` non-empty
- `malformedJsonBody_returnsBadRequestProblemDetail()` — POST `"{not-json"` → same

## Refs

AC: `requirements.md` § Standardized schema (all 3), § Publishing (bullet 1), § Validation / error handling (the `400`/`ProblemDetail` mechanism, generic).
Design: `design.md` § Standardized schema, § Provider request DTOs and parsing (`FeedProvider` port only), § Validation rules (enforcement paragraph), § Component integration.

## Definition of Done (from `tasks.md`, unchanged)

- `mvn clean verify` passes.
- All tests listed above pass.
- No `/provider-alpha/feed` or `/provider-beta/feed` endpoint exists yet — expected, added in T02/T03.

## Open questions

None outstanding for this step.

## Execution result

Implemented as planned, with corrections discovered during the build (Jackson 3 packages, `spring-boot-starter-webmvc-test`, top-level `ProbeController`) folded back into this plan and into `design.md`. `mvn clean verify` under JDK 25 (`JAVA_HOME=$(/usr/libexec/java_home -v 25)`): **BUILD SUCCESS**, 10/10 tests passing, on branch `feed-normalization/foundation`.
