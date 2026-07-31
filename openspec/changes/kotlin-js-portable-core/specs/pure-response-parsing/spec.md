## MODIFIED Requirements

### Requirement: Successful responses are parsed without transport types
The FANBOX core SHALL provide endpoint-specific pure functions that decode a raw JSON response string through the production JSON configuration and existing entity-to-domain mapping rules. Homepage metadata SHALL be decoded from the raw metadata JSON string extracted from the homepage document, through a pure function that requires no HTML parser. Extracting that string from an HTML document SHALL be performed outside the portable core by an implementation the core receives. Response parsing MUST NOT require `HttpResponse`, `HttpRequest`, or another Ktor type.

#### Scenario: JSON response becomes the existing model
- **WHEN** a supported endpoint parser receives a successful golden JSON fixture
- **THEN** it returns the same domain model that the existing generated API and mapper path returns

#### Scenario: Homepage HTML becomes metadata
- **WHEN** the homepage metadata extractor receives HTML containing the FANBOX metadata element and passes the extracted content to the core decoder
- **THEN** the result is the same metadata model as the existing homepage repository path

#### Scenario: Extracted metadata JSON is decoded by the core
- **WHEN** the core metadata decoder receives the metadata JSON string from a homepage golden fixture
- **THEN** it returns the same metadata model without using an HTML parser

#### Scenario: Missing metadata element is classified
- **WHEN** a homepage document contains no FANBOX metadata element
- **THEN** the caller receives the existing `FanboxException.SchemaMismatch` semantics with the homepage endpoint and sanitized bounded fragment

#### Scenario: Invalid successful response is classified
- **WHEN** a successful HTTP response body cannot be decoded for its endpoint
- **THEN** the caller receives the existing `FanboxException.SchemaMismatch` semantics with the endpoint ID and sanitized bounded response fragment

### Requirement: Response interpretation is platform independent
Response-body parsing, cursor and host extraction used by mappers, mismatch sanitization, metadata JSON decoding, and HTTP failure classification from primitive status/header/body values SHALL reside in a source boundary with no imports from `io.ktor`, `androidx.room`, `io.github.aakira.napier`, or an HTML parser, and SHALL compile for every declared target including Kotlin/JS.

#### Scenario: Response import boundary is inspected
- **WHEN** the response interpretation source boundary is scanned during verification
- **THEN** no Ktor, Room, Napier, or Ksoup import is present

#### Scenario: Encoded cursor query is parsed once
- **WHEN** a response contains a pagination URL with encoded query separators or values
- **THEN** the pure URL query parser extracts the same cursor value as the current behavior without double decoding

#### Scenario: Absolute pagination URL is not executed directly
- **WHEN** a response contains an absolute `nextUrl`
- **THEN** the parser extracts only its cursor/page/offset query values and the next request is rebuilt with the original known endpoint ID and relative path rather than executing the supplied URL

#### Scenario: Response parsing is verified on Kotlin/JS
- **WHEN** the response parsing tests are executed on the Kotlin/JS target
- **THEN** they pass with the same expectations as on the existing targets
