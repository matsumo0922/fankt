## Context

`RoomFanboxCookieStorage` owns one `FanktDatabase` and exposes `cookies` as the DAO's Room-backed
`Flow`. Every other member fails with `IllegalStateException` after `close()`, but a `Flow` that was
already obtained escapes that guard: it keeps its own subscription to Room's invalidation tracker.
The current KDoc admits this as "can terminate with an underlying database exception", which leaves
the host without a cause it can match on and leaves the timing unverified.

Issue #53 also asks whether repeated `Fanbox` creation accumulates `RoomDatabase` instances. It
cannot: `fanbox` has no Room dependency, and only the optional artifact's factory builds a database.
That is a property of the dependency graph rather than an observation, so this change adds the
host-visible assertion instead of new production wiring.

## Goals / Non-Goals

Goals:

- One deterministic termination cause for a `Flow` obtained from the storage, whether the collector
  is live at close or starts collecting afterwards.
- Verification on Android and iOS through the shared contract test, including the production
  `Fanbox.cookies` path.
- A host-observable assertion that repeated `Fanbox` create/close cycles leave the injected storage
  and its database untouched.

Non-Goals:

- Making `Fanbox.close()` close injected storage. Storage ownership stays with the host; changing it
  would break hosts that share one storage across several `Fanbox` instances.
- A process-singleton database, a database registry, or reference counting. The single-owner model
  is already specified in `room-persistence-artifact` and is not in question here.
- Draining or gracefully completing in-flight collectors. Termination is a failure, not a normal
  completion, because the data source is gone.

## Decisions

### Terminate with `IllegalStateException` rather than completing normally

A collector that receives `onCompletion` without a cause cannot distinguish "the storage closed" from
"the upstream finished", and a Room-backed `Flow` never finishes on its own. Normal completion would
silently stop a host's Cookie observation. `IllegalStateException` is also what every other post-close
member already throws, so a host writes one catch rather than two.

Attribution: agent 仮決め. The alternative (a dedicated exception type) would add public API surface
to the optional artifact for a case the host handles as a terminal failure either way.

### Signal close through `CompletableDeferred` instead of the `closed` boolean

`close()` currently flips a plain `var closed: Boolean` that is read from every member. A live
collector runs concurrently with `close()` by construction, so the close state must be observable
across coroutines and usable as a suspension point. `CompletableDeferred<Unit>` provides both: its
`complete()` is atomic and returns false when already completed, which keeps `close()` idempotent
without a separate flag, and `await()` gives the termination branch something to suspend on.

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
```

Three parts, each covering a distinct window:

- `ensureOpen()` inside the `flow` builder runs at collection start, so a `Flow` captured before
  close but first collected after close fails before Room is subscribed at all. Without it, the
  backend could emit a value before the termination branch wins.
- `closeTermination` covers the live-collector case. When `close()` completes the signal, this branch
  throws and `merge` propagates that cause to the collector while cancelling the backend branch.
- `catch` on the backend branch covers the race in between. `close()` completes the signal and then
  closes the database; cancellation of the backend branch is asynchronous, so Room can surface its
  own failure first. Rewriting any backend failure that arrives after the signal into the same
  `IllegalStateException` makes the observed cause independent of which side wins. A failure arriving
  while the storage is still open is rethrown unchanged, so genuine database errors stay visible.

Attribution: agent 仮決め.

### Keep `close()` non-suspending and do not wait for collectors

`close()` is `AutoCloseable.close()` and is called from `finally` blocks and from non-coroutine hosts.
Waiting for collectors to unwind would require suspension or blocking and could deadlock a host that
closes from inside its own collector. The contract therefore guarantees the cause a collector
observes, not that the collector has finished unwinding before `close()` returns.

Attribution: agent 仮決め.

### Prove the repeated-client property by observation, not by counting instances

There is no public accessor for "number of open `RoomDatabase` instances", and adding one for a test
would export internals. The equivalent host-visible property is: after repeatedly creating and
closing `Fanbox` instances over one injected storage, a collector established before those cycles is
still live, and the storage still reads and writes. If a `Fanbox` had created or closed a database,
one of the two would fail.

Attribution: agent 仮決め.

## Risks / Trade-offs

- **Stricter runtime behavior for existing collectors.** A host that today swallows the underlying
  Room exception now sees `IllegalStateException`. No signature changes, so this is a runtime-only
  tightening within the previously documented "may fail" allowance, and it is stated in the README
  and KDoc.
- **`merge` subscribes to the backend `Flow` in a child coroutine.** Room's invalidation tracker
  registration therefore happens one dispatch later than today. The contract test asserts that an
  update written after collection starts is still observed, so a lost first emission would fail the
  test rather than pass silently.
- **iOS test concurrency.** The contract test runs under `runBlocking`, which on Kotlin/Native does
  not multiplex a collector and a `close()` across threads for free. The test launches the collector
  in the same scope and yields until it has observed its first value before closing, so the
  termination assertion does not depend on thread scheduling.

## Migration Plan

No migration. Schema, database path, migrations, and every public signature are unchanged. Hosts that
already treat a post-close collector failure as terminal need no code change; hosts that match on a
specific Room exception type must match `IllegalStateException` instead, which the README states.

## Open Questions

None.
