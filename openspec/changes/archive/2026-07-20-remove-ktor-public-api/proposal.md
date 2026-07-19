## Why

`fanbox` currently exposes Ktor types in its public API and publishes Ktor as API dependencies, so consumers inherit fankt's HTTP implementation choice and version constraints. The v0.1.0 breaking release is the appropriate boundary for replacing those signatures with fankt-owned contracts while preserving authenticated request and streaming-download behavior.

## What Changes

- **BREAKING** Replace public Ktor `Cookie` and `LogLevel` types with fankt-owned authentication records and logging levels; cookie scope becomes explicit record data and `setCookies` no longer accepts a competing request URL.
- **BREAKING** Remove raw `HttpClient` access from `Fanbox`; generated APIs and downloads remain the supported network entry points.
- **BREAKING** Replace the deferred Ktor `HttpStatement` download result with a suspend, chunk-streaming fankt API that keeps large media off a mandatory whole-response buffer.
- Keep URL allowlisting, redirect validation, authentication scoping, progress reporting, exception mapping, and `Fanbox` ownership of download resources intact.
- Publish Ktor client and engine dependencies as implementation details rather than API dependencies.
- Add PR and release gates for compiled ABI, declared dependency scope, and Android publication metadata so Ktor cannot return to the public boundary unnoticed.
- Document migration of logging, cookies, downloads, and host-owned platform networking.

## Capabilities

### New Capabilities

- `ktor-free-public-api`: Defines the fankt-owned public types and automated ABI/publication boundary that keep Ktor private to the implementation.

### Modified Capabilities

- `fanbox-client-lifecycle`: Removes raw-client access while retaining deterministic ownership and close behavior for internal clients.
- `authenticated-media-download`: Changes downloads from deferred Ktor statements to direct chunk streaming without weakening destination, authentication, progress, or lifecycle guarantees.
- `injectable-auth-storage`: Exposes cookie observation and mutation exclusively through the existing fankt-owned cookie record.

## Impact

- Public source and binary API in `fankt/fanbox` changes for logging, cookies, raw HTTP access, and downloads.
- `fanbox` common, Android, and iOS Ktor dependencies move from Gradle `api` to `implementation`.
- Unit and contract tests, ABI dumps, module-metadata verification, README examples, and KDoc require updates.
- Consumers must migrate Ktor `Cookie`, `LogLevel`, `HttpClient`, and `HttpStatement` usages. Cookie migration includes converting relative `maxAge` to absolute `expiresAtEpochMilliseconds`; Ktor-only fields such as `httpOnly`, extensions, and encoding are outside the fankt storage record. Consumers may select a compatible Ktor runtime version independently, but fankt does not promise compatibility with arbitrary Ktor versions in one runtime graph.
