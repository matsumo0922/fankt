## REMOVED Requirements

### Requirement: Raw clients are shared and owned by Fanbox
**Reason**: Returning Ktor `HttpClient` exposes the implementation dependency and lets callers bypass the supported authentication and destination boundaries.

**Migration**: Use generated `Fanbox` API operations, use `Fanbox.download` for allowlisted media, or create and own a separate host HTTP client for unrelated networking.

## MODIFIED Requirements

### Requirement: Fanbox releases its HTTP resources
`Fanbox` SHALL implement `AutoCloseable`, SHALL invoke `close()` on every internal HTTP client it owns when `Fanbox.close()` is called, and SHALL make sequential repeated close calls safe. Internal engine shutdown completes according to the HTTP implementation's asynchronous close contract.

#### Scenario: Close releases generated and download clients
- **WHEN** `close()` is called on an open `Fanbox` instance
- **THEN** close is invoked on all generated-API and download clients before `Fanbox.close()` returns, and none accepts a new request afterward

#### Scenario: Repeated close
- **WHEN** `close()` is called more than once
- **THEN** calls after the first completed close have no effect and do not fail

#### Scenario: Construction fails after allocating a client
- **WHEN** `Fanbox` construction fails after one or more client-factory calls have successfully returned clients
- **THEN** close is invoked on every returned client before the construction failure is propagated

#### Scenario: API access after close
- **WHEN** a public operation is started after `close()` returns
- **THEN** the operation fails with `IllegalStateException` before starting storage or network work
