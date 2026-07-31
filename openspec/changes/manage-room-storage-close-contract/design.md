## Context

`RoomFanboxCookieStorage` owns one `FanktDatabase` and exposes `cookies` as the DAO's Room-backed
`Flow`. Every other member fails with `IllegalStateException` after `close()`, but a `Flow` that was
already obtained escapes that guard: it keeps its own subscription to Room's invalidation tracker.
The current KDoc admits this as "can terminate with an underlying database exception", which leaves
the host without a cause it can match on and leaves the timing unverified. Worse, an idle Room-backed
`Flow` with no pending query is not guaranteed to surface anything at all when the database closes, so
a host observing Cookie state can be left suspended indefinitely rather than failing.

Issue #53 also asks whether repeated `Fanbox` creation accumulates `RoomDatabase` instances. It
cannot: the core `fanbox` artifact has no Room dependency, and only the optional artifact's factory
builds a database. That is a property of the dependency graph, and the repository already enforces it
with `:fankt:fanbox:verifyPersistenceBoundary`, which fails the build when Room, androidx.sqlite,
androidx.startup, or the optional artifact appears on the published Android, iOS, or JS classpath
(`fankt/fanbox/build.gradle.kts`).

## Goals / Non-Goals

Goals:

- A named, matchable cause for a collection of a storage `Flow` when close is the first terminal
  event, and the same cause when a captured `Flow` is first collected after close.
- Explicit precedence when close races another terminal event, so the contract is implementable.
- Verification on Android and iOS through the shared contract test, including the production
  `Fanbox.cookies` path.
- A host-observable assertion that repeated `Fanbox` create/close cycles do not close or disturb the
  injected storage.

Non-Goals:

- Making `Fanbox.close()` close injected storage. Storage ownership stays with the host; changing it
  would break hosts that share one storage across several `Fanbox` instances.
- A process-singleton database, a database registry, or reference counting. The single-owner model
  is already specified in `room-persistence-artifact` and is not in question here.
- Proving "exactly one open `RoomDatabase`" at runtime. No such observation exists without exporting
  internals; the dependency boundary check is the evidence for that property.
- Draining or gracefully completing in-flight collections. Termination is a failure, not a normal
  completion, because the data source is gone.
- Supporting close from a context that cannot progress concurrently with the storage's query
  dispatcher. See the corresponding decision below.

## Decisions

### Terminate with `IllegalStateException` rather than a dedicated exception type

Every other post-close member of this storage already fails with `IllegalStateException` from
`check()`. Using the same type lets a host write one catch for "this storage is closed" instead of
two, and keeps the optional artifact's public surface unchanged. A dedicated exception type would be
more precisely matchable, but it adds a public type to an optional artifact for a condition the host
handles as terminal either way, and it would have to be introduced as new public API in a release
that otherwise changes no signature.

Normal completion was rejected: a collector that receives completion without a cause cannot
distinguish "the storage closed" from "the upstream finished", and a Room-backed `Flow` never
finishes on its own, so a host would silently stop observing Cookie state.

The failure is distinguishable from a genuine database failure because database failures surface as
their own driver-specific types and, per the contract below, are propagated unchanged while the
storage is open.

Attribution: agent 仮決め — 人間確認事項（`IllegalStateException` の一貫性を、専用例外型の識別性より
優先する選好）。

### Close is linearized at one point and the first terminal event wins

`close()` completes a `CompletableDeferred<Unit>` close signal and then closes the database. That
completion is the linearization point: before it the storage is open for every observer, after it
closed.

The contract only claims the cause when close is the first terminal event for that collection. It
does not claim it when:

- the collecting coroutine is cancelled independently — `CancellationException` must propagate for
  structured concurrency to work, and `Flow.catch` does not intercept the collecting job's own
  cancellation;
- the collector itself throws — that exception belongs to the caller and `Flow.catch` rethrows it
  rather than passing it to the catch action;
- a database failure is observed while the signal is still incomplete — that is a genuine failure and
  is propagated unchanged.

This is what makes the requirement implementable rather than aspirational.

Attribution: agent 仮決め.

### `CompletableDeferred` close signal instead of the `closed` boolean

`close()` currently flips a plain `var closed: Boolean` read from every member. A collection runs
concurrently with `close()` by construction, so close state must be observable across coroutines and
usable as a suspension point. `CompletableDeferred<Unit>` provides both: `complete()` is atomic and
returns false when already completed, which keeps `close()` idempotent without a separate flag, and
`await()` gives the termination branch something to suspend on.

### Compose the exposed `Flow` as a guarded merge

```kotlin
override val cookies: Flow<List<FanboxCookieRecord>>
    get() = requireOpen(closeAwareCookies)

private val closeAwareCookies: Flow<List<FanboxCookieRecord>> = flow {
    ensureOpen()
    emitAll(
        merge(
            backend.cookies.catch { failure -> throw closeCauseOr(failure) },
            closeTermination,
        ),
    )
}

private val closeTermination: Flow<Nothing> = flow {
    closeSignal.await()
    throw closedFailure()
}

private fun closeCauseOr(failure: Throwable): Throwable =
    if (closeSignal.isCompleted) closedFailure() else failure
```

Three parts, each covering a distinct window:

- `ensureOpen()` inside the `flow` builder runs at collection start, so a `Flow` captured before
  close but first collected after close fails before Room is subscribed at all.
- `closeTermination` covers the live-collection case. When `close()` completes the signal, this branch
  throws and `merge` propagates that cause while cancelling the backend branch.
- `catch` on the backend branch covers the race in between. Cancellation of the backend branch is
  asynchronous, so Room can surface its own failure first. A backend failure arriving after the signal
  is rewritten into the same `IllegalStateException`; one arriving while the signal is incomplete is
  rethrown unchanged. `Flow.catch` receives neither the collecting job's cancellation nor a downstream
  failure, so neither is rewritten.

Attribution: agent 仮決め.

### Do not support close from a context that cannot progress with the query dispatcher

Room's `CloseBarrier.close()` spins until every registered blocker is released, and
`InvalidationTracker` holds a blocker while synchronizing observers. If the host passes a dispatcher
with single parallelism as `ioDispatcher` — which becomes the database's `queryCoroutineContext` —
and also calls `close()` on that same dispatcher, the coroutine holding the blocker cannot resume and
`close()` cannot return. This is a property of Room's close barrier, not of this change, and it exists
today; the new concurrent-close guarantee makes it reachable more often, so it becomes a documented
restriction rather than an implicit hazard.

Keeping `close()` non-suspending is what forces this restriction to be documented instead of solved:
`close()` is `AutoCloseable.close()`, is called from `finally` blocks and from non-coroutine hosts,
and making it suspend would break those callers and could deadlock a host that closes from inside its
own collector.

Attribution: agent 仮決め — 人間確認事項（任意 dispatcher での close を保証範囲外とし、KDoc と README
の制約記載で閉じる選択）.

### Prove the repeated-client property by what it actually shows

A host-visible test cannot count open `RoomDatabase` instances, so it is not evidence for "exactly
one instance". What it does show is that `Fanbox` create/close cycles neither close the injected
storage nor terminate a collection established before them — that is, `Fanbox.close()` does not reach
into host-owned storage. The stronger property, that a `Fanbox` cannot open a database at all, is
proven by `:fankt:fanbox:verifyPersistenceBoundary` over the published Android, iOS, and JS
dependency graphs and the merged Android manifest.

Attribution: agent 仮決め.

## Risks / Trade-offs

- **Stricter runtime behavior for existing collections.** A host that today swallows the underlying
  Room exception now sees `IllegalStateException`. No signature changes, so this is a runtime-only
  tightening within the previously documented "may fail" allowance, and it is stated in the README
  and KDoc.
- **`merge` subscribes to the backend `Flow` in a child coroutine**, so Room's invalidation tracker
  registration happens one dispatch later than today. Room's generated Flow registers an observer and
  emits an initial invalidation before querying, so a late subscription still reads current state
  rather than missing an earlier write. The contract test asserts a value written after collection
  starts is still observed, so a lost emission fails the test rather than passing silently.
- **Test structure under `runBlocking`.** An `IllegalStateException` thrown by a collecting child
  would cancel the enclosing scope before the assertion runs, so the contract test collects inside a
  child that captures its own outcome as a value, uses `CompletableDeferred` rather than polling as
  the start barrier, and bounds each wait with a timeout so a hung collection fails instead of
  hanging the suite.

## Migration Plan

No migration. Schema, database path, migrations, and every public signature are unchanged. Hosts that
already treat a post-close collection failure as terminal need no code change; hosts that match on a
specific Room exception type must match `IllegalStateException` instead, which the README states.
Hosts that close storage on a single-parallelism dispatcher shared with the query context must move
that call, which the KDoc and README state.

## Open Questions

None blocking. Two decisions above are marked 人間確認事項 and are transcribed into the PR
description for review.
