## Context

Issue #21 made the generated client/API/repository graph immutable, but `Fanbox` still drops the three generated `HttpClient` references after passing them to Ktorfit and creates a new raw client on every `getHttpClient()` call. `Fanbox` therefore cannot initiate release of its engines, and consumers such as PixiView-KMP create an unbounded number of raw clients while downloading. The issue's reference to cancelling an internal `CoroutineScope` is stale because #21 removed that scope.

`createFanboxDependencies()` also builds a new RoomDatabase for each `Fanbox`; it is not process-shared and has no close path. Closing it would change the lifecycle of the public Room-backed Flows and requires a separate ownership design, tracked by #53. Issue #22 explicitly scopes its close requirement to owned HttpClients and the now-removed scope, so this change preserves the existing database lifecycle rather than making a silent cross-layer change.

`getHttpClient(isEnableContentNegotiation)` currently represents two materially different configurations. Existing callers can also retain the returned client and may assume that they own it, so sharing changes an ownership contract even though the method signature remains source-compatible.

## Goals / Non-Goals

**Goals:**

- Bound each `Fanbox` instance to three generated clients and one raw client for each supported content-negotiation configuration.
- Give `Fanbox.close()` deterministic ownership of all five clients and make close idempotent.
- Reject operations started after close completes before storage or network work begins.
- Preserve request-time CSRF lookup, cookie matching, diagnostics, public method signatures, and the true/false content-negotiation behavior.
- Verify identity and release through the production `Fanbox` construction and request path.

**Non-Goals:**

- Reintroducing or cancelling a `CoroutineScope`; `Fanbox` no longer owns one.
- Redesigning the Phase 2 download API or changing `HttpStatement` execution semantics.
- Closing the per-instance Room database or invalidating a `Flow` reference obtained before close; #53 owns that lifecycle decision.
- Changing CSRF refresh, cookie persistence, logging, or error-normalization policy.

## Decisions

1. **（ユーザー確認済み）`Fanbox` owns and closes its clients.** Implement `kotlin.AutoCloseable`. KDoc states that callers close the `Fanbox`, never the raw clients it returns, and do not start or execute work after close. This is the ownership reversal explicitly requested by issue #22.

2. **（agent 仮決め）Keep one eager raw client per existing configuration.** Construct raw clients for `isEnableContentNegotiation = true` and `false` alongside the three generated clients. Repeated calls with the same argument return the same identity; the two configurations remain distinct. Eager construction fixes the owned set at five and avoids a lazy-initialization-versus-close race without adding locks or an atomic cache. Alternatives considered were one global raw client, which would silently ignore the public Boolean parameter, and lazy caching, which would need cross-platform synchronization so close cannot miss a concurrently created client.

3. **（agent 仮決め）Retain clients in the immutable resource graph.** Replace the repository-only construction result with an internal resource holder containing the repositories and all five clients. Ktorfit continues to receive the same generated clients, while `close()` can enumerate the retained identities exactly once. This extends rather than weakens issue #21's immutable graph guarantee.

4. **（agent 仮決め）Use a thread-safe `CompletableJob` only as the lifecycle state.** A standalone `Job()` provides common-code, thread-safe `isActive` and `complete()` transitions without creating a scope or adding a dependency. `close()` wins the state transition before closing clients; only the first caller performs release. Operations started after the transition fail via `check(...)` with `IllegalStateException`. An operation that races with close may either finish or fail through its closing client. Callers must serialize lifecycle shutdown: a concurrent losing `close()` call may return while the winning call is still invoking client close, whereas a sequential close after the first call completes is a no-op.

5. **（agent 仮決め）Gate public entry paths at shared access points.** Repository-backed methods resolve repository properties that enforce the open-state check. Cookie/session/token mutation and raw-client access check explicitly. Public `cookies` and `csrfToken` property access also checks; a `Flow` obtained while open is not owned or cancelled by `Fanbox`. This avoids duplicating a check in every forwarding method while preserving a single observable contract.

6. **（agent 仮決め）Test with production construction wiring.** Extend the existing injected `FanboxHttpClientFactory` fixture to record every actual client. Tests call public `getHttpClient()` for identity, execute public `Fanbox` API work before and after close, and verify each client rejects post-close requests. The factory count proves construction is bounded and the retained raw client proves the production entry point is wired to the shared graph. MockEngine is injected externally and cannot prove production engine-pool teardown; the test proves Fanbox-to-HttpClient close wiring and request rejection, while engine shutdown relies on Ktor's managed-engine contract.

7. **（agent 仮決め）Rollback partial construction.** The resource builder records each client immediately after a factory call returns. If a later client creation, Ktorfit setup, generated API construction, or repository construction throws, it invokes close on the recorded clients before rethrowing. An object allocated inside a factory that throws before returning remains the factory's responsibility because `Fanbox` cannot observe it. The per-instance database created before this builder retains its existing lifecycle and is tracked by #53.

8. **（agent 仮決め）Wire the repository sample lifecycle.** `FanboxViewModel` closes its owned `Fanbox` from `onCleared()`. PixiView-KMP is a separate repository and remains a documented downstream migration, but its current download path has been verified not to close the returned raw client, so sharing is compatible and immediately bounds allocation. The sample in this repository demonstrates deterministic owner close in executable code.

## Risks / Trade-offs

- **Two raw engines are allocated even when unused** → The fixed increase from three to five clients is bounded and eliminates synchronization complexity; the previous behavior was unbounded. Phase 2 can consolidate download transport separately.
- **A caller may close a shared raw client directly** → KDoc and README mark it as `Fanbox`-owned. Preventing `HttpClient.close()` is impossible while returning the concrete type, so this remains a behavioral compatibility risk inherent in issue #22.
- **Close can race with an in-flight request or another close** → State transition rejects new operations, then clients cancel/close their work according to Ktor semantics. Callers serialize lifecycle shutdown with their own work; `close()` does not wait for successful request completion or for a concurrent winning close call.
- **One client close could throw before later clients close** → Close all retained clients, collect the first failure, attach later failures as suppressed, and rethrow only after attempting every release. The lifecycle remains terminal and later close calls do not retry a failed client.
- **A previously obtained database Flow can outlive Fanbox** → The database is actually per-instance, but its Flow ownership and close behavior are unchanged here and tracked by #53. Only new property access is rejected.
- **Ktor engine shutdown is asynchronous** → `Fanbox.close()` invokes every client close before returning and rejects subsequent work; physical connection/thread teardown completes under Ktor's close contract rather than a new blocking wait.
- **External consumers do not yet call `Fanbox.close()`** → Sharing immediately bounds their raw-client count; deterministic final release additionally requires those repositories to adopt the documented lifecycle hook.

## Migration Plan

Consumers keep the existing constructor and `getHttpClient()` calls, stop closing returned raw clients, and close the owning `Fanbox` at their lifecycle boundary. A deferred `HttpStatement` must be executed before owner close; execution after close fails through the closed Ktor client and is not normalized as a `FanboxException`. Rollback restores per-call raw-client allocation and removes `AutoCloseable`; no persisted data or schema changes are involved.

## Open Questions

None. The one-client-per-existing-configuration interpretation is reversible and preserves the current public parameter while satisfying repeated-call identity. RoomDatabase ownership is explicitly deferred to #53.
