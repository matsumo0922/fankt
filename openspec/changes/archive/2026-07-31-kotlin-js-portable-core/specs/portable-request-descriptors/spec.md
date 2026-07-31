## MODIFIED Requirements

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
