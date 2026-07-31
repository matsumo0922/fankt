# portable-request-descriptors Specification

## Purpose
TBD - created by archiving change replace-ktorfit-with-request-descriptors. Update Purpose after archive.
## Requirements
### Requirement: Requests are represented by a portable descriptor
The FANBOX core SHALL represent an API request as an immutable serializable `RequestDescriptor` containing a stable endpoint ID, a relative path, a transport-neutral GET or POST method, an ordered list of query name/value pairs, and an optional JSON body. The descriptor and its value types MUST NOT depend on Ktor, Room, or Napier.

#### Scenario: GET descriptor is data only
- **WHEN** a caller builds a descriptor for a GET endpoint
- **THEN** the result contains its endpoint ID, relative path, GET method, ordered query pairs, and no JSON body without performing storage or network work

#### Scenario: POST descriptor carries JSON
- **WHEN** a caller builds a descriptor for a POST endpoint
- **THEN** the result contains its endpoint ID, relative path, POST method, query pairs, and the JSON body required by that endpoint without containing a Ktor request type

### Requirement: Endpoint builders preserve the complete route contract
`FanboxEndpoints` SHALL provide a pure builder for every route supported by the existing FANBOX client and SHALL preserve each route's path, method, omitted optional query values, query value representation, and JSON field representation. Two response-decoding modes that use the same route SHALL share one request descriptor contract.

#### Scenario: Existing GET request is reproduced
- **WHEN** a descriptor is built for any existing GET operation using representative arguments
- **THEN** its path and decoded query values equal the request previously produced by the generated API

#### Scenario: Existing POST request is reproduced
- **WHEN** a descriptor is built for like, follow, comment submission, or comment deletion
- **THEN** its method and JSON value types equal the previous wire contract, including string-valued `creatorUserId` and omission of null comment parent IDs

#### Scenario: Route and operation inventories are complete
- **WHEN** the endpoint builder inventory is compared with the supported API operation inventory
- **THEN** 28 unique request routes are represented, 29 request operations are mapped to those routes, and the strict and tolerant `plan.listSupporting` operations refer to the same descriptor

### Requirement: Request assembly is transport independent
All code that defines request descriptors, endpoint IDs, endpoint builders, and request JSON serialization SHALL remain in a source boundary with no imports from `io.ktor`, `androidx.room`, `io.github.aakira.napier`, or an HTML parser, and SHALL declare no dependency on a JVM-only artifact. That boundary SHALL be the source set shared by every declared target, so that a non-portable import fails compilation for Kotlin/JS rather than only failing a verification task.

#### Scenario: Import boundary is inspected
- **WHEN** the request assembly source boundary is scanned during verification
- **THEN** no Ktor, Room, Napier, or Ksoup import is present

#### Scenario: Non-portable import fails the JS compilation
- **WHEN** a Ktor import is added to the request assembly boundary
- **THEN** the Kotlin/JS compilation of the FANBOX core fails

#### Scenario: Request assembly is verified on Kotlin/JS
- **WHEN** the endpoint builder tests are executed on the Kotlin/JS target
- **THEN** every route and operation assertion passes with the same expectations as on the existing targets

