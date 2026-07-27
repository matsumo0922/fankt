## MODIFIED Requirements

### Requirement: Fanbox releases its HTTP resources
`Fanbox` SHALL implement `AutoCloseable`, SHALL invoke `close()` on every internal HTTP client it owns when `Fanbox.close()` is called, and SHALL make sequential repeated close calls safe. The descriptor executor and repositories SHALL reject new work after close. Internal engine shutdown completes according to the HTTP implementation's asynchronous close contract.

#### Scenario: Close releases executor and download clients
- **WHEN** `close()` is called on an open `Fanbox` instance
- **THEN** close is invoked on the executor-owned request client and download client before `Fanbox.close()` returns, and neither accepts a new request afterward

#### Scenario: Repeated close
- **WHEN** `close()` is called more than once
- **THEN** calls after the first completed close have no effect and do not fail

#### Scenario: Construction fails after allocating a client
- **WHEN** `Fanbox` construction fails after one or more client-factory calls have successfully returned clients
- **THEN** close is invoked on every returned client before the construction failure is propagated

#### Scenario: API access after close
- **WHEN** a public operation is started after `close()` returns
- **THEN** the operation fails with `IllegalStateException` before starting storage or network work
