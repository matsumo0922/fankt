## Why

`Fanbox` owns several `HttpClient` instances but exposes no way to release them, while `getHttpClient()` creates another client on every call. Long-lived consumers therefore cannot deterministically release the internal engines, and download-heavy consumers can accumulate an unbounded number of connection and thread pools.

## What Changes

- Make `Fanbox` explicitly closeable and close every `HttpClient` owned by that instance.
- Return stable, `Fanbox`-owned raw clients from `getHttpClient()` instead of allocating per call.
- Reject API access after `close()` completes and make repeated `close()` calls safe.
- Document the ownership and lifecycle contract in KDoc and README usage guidance.
- **BREAKING**: callers must no longer close a client returned by `getHttpClient()` because it is shared and owned by `Fanbox`.

## Capabilities

### New Capabilities

- `fanbox-client-lifecycle`: Defines shared raw-client identity, ownership, deterministic release, and post-close behavior for a `Fanbox` instance. This traces to issue #22.

### Modified Capabilities

None.

## Impact

- `fankt/fanbox` client construction and repository graph ownership.
- Public `Fanbox` lifecycle and `getHttpClient()` behavioral contract.
- Common tests using `FanboxHttpClientFactory` and `MockEngine`.
- README and generated API KDoc guidance for consumers such as PixiView-KMP.
- The repository's sample `FanboxViewModel` lifecycle wiring.
- No database schema, token policy, cookie policy, or Phase 2 download API redesign.
