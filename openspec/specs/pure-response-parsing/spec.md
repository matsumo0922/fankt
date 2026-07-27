# pure-response-parsing Specification

## Purpose
TBD - created by archiving change replace-ktorfit-with-request-descriptors. Update Purpose after archive.
## Requirements
### Requirement: Successful responses are parsed without transport types
The FANBOX core SHALL provide endpoint-specific pure functions that decode a raw JSON response string through the production JSON configuration and existing entity-to-domain mapping rules. Homepage metadata SHALL be parsed from a raw HTML string through the production metadata parser. Response parsing MUST NOT require `HttpResponse`, `HttpRequest`, or another Ktor type.

#### Scenario: JSON response becomes the existing model
- **WHEN** a supported endpoint parser receives a successful golden JSON fixture
- **THEN** it returns the same domain model that the existing generated API and mapper path returns

#### Scenario: Homepage HTML becomes metadata
- **WHEN** the homepage parser receives HTML containing the FANBOX metadata element
- **THEN** it returns the same metadata model as the existing homepage repository path

#### Scenario: Invalid successful response is classified
- **WHEN** a successful HTTP response body cannot be decoded for its endpoint
- **THEN** the caller receives the existing `FanboxException.SchemaMismatch` semantics with the endpoint ID and sanitized bounded response fragment

### Requirement: Tolerant decoding reports through an injected sink
Per-item tolerant list decoding SHALL preserve valid items and mismatch index paths while reporting diagnostics through a caller-provided sink. The pure response path SHALL provide a no-op default and MUST NOT call Napier directly.

#### Scenario: One invalid list item is skipped
- **WHEN** a tolerant parser receives a list containing valid items and one schema-invalid item
- **THEN** it returns the valid mapped items and reports one mismatch with the existing endpoint and index path through the injected sink

#### Scenario: No diagnostic sink is supplied
- **WHEN** a tolerant parser is used without a diagnostic sink
- **THEN** it returns the same partial result without requiring a logging framework

#### Scenario: Supporting plans preserve explicit decode mode
- **WHEN** the no-callback supported-plans operation parses `plan.listSupporting`
- **THEN** it uses the strict parser and fails the whole response on one invalid item

#### Scenario: Supporting plans tolerant overload preserves valid items
- **WHEN** the callback supported-plans operation parses the same `plan.listSupporting` descriptor response
- **THEN** it uses the tolerant parser, returns valid items, and reports each invalid item through the sink

### Requirement: Response interpretation is platform independent
Response-body parsing, cursor and host extraction used by mappers, mismatch sanitization, and HTTP failure classification from primitive status/header/body values SHALL reside in a source boundary with no imports from `io.ktor`, `androidx.room`, or `io.github.aakira.napier`.

#### Scenario: Response import boundary is inspected
- **WHEN** the response interpretation source boundary is scanned during verification
- **THEN** no Ktor, Room, or Napier import is present

#### Scenario: Encoded cursor query is parsed once
- **WHEN** a response contains a pagination URL with encoded query separators or values
- **THEN** the pure URL query parser extracts the same cursor value as the current behavior without double decoding

#### Scenario: Absolute pagination URL is not executed directly
- **WHEN** a response contains an absolute `nextUrl`
- **THEN** the parser extracts only its cursor/page/offset query values and the next request is rebuilt with the original known endpoint ID and relative path rather than executing the supplied URL
