## ADDED Requirements

### Requirement: Raw clients are shared and owned by Fanbox
`Fanbox` SHALL return a stable, `Fanbox`-owned raw `HttpClient` for each supported content-negotiation configuration. This requirement traces to issue #22 acceptance criterion "`getHttpClient()` を複数回呼んでも同一インスタンスが返ること" while preserving the existing configuration parameter.

#### Scenario: Repeated default raw-client access
- **WHEN** `getHttpClient()` is called multiple times on the same open `Fanbox` instance
- **THEN** every call returns the same `HttpClient` instance

#### Scenario: Repeated non-negotiating raw-client access
- **WHEN** `getHttpClient(isEnableContentNegotiation = false)` is called multiple times on the same open `Fanbox` instance
- **THEN** every call returns the same non-negotiating `HttpClient` instance

#### Scenario: Configuration remains effective
- **WHEN** callers request both supported values of `isEnableContentNegotiation`
- **THEN** `Fanbox` returns one stable client per configuration and each client retains the requested response-conversion behavior

#### Scenario: Caller does not own the raw client
- **WHEN** a caller receives a client from `getHttpClient()`
- **THEN** the caller leaves that client open because `Fanbox.close()` owns its release

### Requirement: Fanbox releases its HTTP resources
`Fanbox` SHALL implement `AutoCloseable`, SHALL release every `HttpClient` it owns when `close()` is called, and SHALL make repeated close calls safe. This requirement traces to issue #22 acceptance criterion "`close()` 後にリソースが解放されること".

#### Scenario: Close releases generated and raw clients
- **WHEN** `close()` is called on an open `Fanbox` instance
- **THEN** all generated-API clients and both configured raw clients owned by that instance are closed before `close()` returns

#### Scenario: Repeated close
- **WHEN** `close()` is called more than once
- **THEN** calls after the first completed close have no effect and do not fail

#### Scenario: Construction fails after allocating a client
- **WHEN** `Fanbox` construction fails after one or more owned clients have been created
- **THEN** every client created by that failed construction attempt is closed before the failure is propagated

#### Scenario: API access after close
- **WHEN** a public operation is started after `close()` returns
- **THEN** the operation fails with `IllegalStateException` before starting storage or network work

#### Scenario: Previously returned raw client after close
- **WHEN** a caller retains a raw client returned before `Fanbox.close()` and then closes the `Fanbox`
- **THEN** the retained client is closed and cannot start another request
