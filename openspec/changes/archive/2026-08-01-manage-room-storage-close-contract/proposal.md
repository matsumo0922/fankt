## Why

Room-backed Cookie storage documents its post-close `Flow` behavior as "a `Flow` obtained before
close **can** terminate with an underlying database exception". A conditional statement is not a
contract: a host that collects `Fanbox.cookies` cannot write correct termination handling against
it, and nothing verifies what actually happens on either platform. Issue #53 requires the close
behavior of live collectors to be defined and verified on Android and iOS.

The rest of issue #53 is already satisfied. The core `fanbox` artifact has no Room dependency at all,
so repeatedly creating and closing `Fanbox` cannot accumulate `RoomDatabase` instances, and
`:fankt:fanbox:verifyPersistenceBoundary` already fails the build if that boundary breaks. What is
missing there is the host-visible statement that `Fanbox.close()` leaves injected host-owned storage
untouched.

## What Changes

- Name the post-close termination of `Flow` values obtained from `RoomFanboxCookieStorage`: when
  close is the first terminal event for a collection, it terminates with `IllegalStateException`,
  matching the failure mode of every other post-close operation on the storage. A collection that
  starts after close fails the same way. This replaces the current conditional "may terminate with an
  underlying Room exception" allowance.
- State the precedence when close races another terminal event: cancellation of the collecting
  coroutine, a failure thrown by the collector itself, and a database failure observed while the
  storage is still open are propagated unchanged.
- Verify both terminations in the shared Android/iOS storage contract test, including a collection
  observing through the production `Fanbox.cookies` path.
- Verify that repeatedly creating and closing `Fanbox` instances over one injected Room storage
  neither closes that storage nor disturbs a collection established before those cycles.
- Document that close must not run on a context that cannot progress concurrently with the storage's
  query dispatcher, because Room's close barrier spins until its blockers are released.
- Update the `RoomFanboxCookieStorage` KDoc and the README persistence section to state the
  termination contract and the close-context restriction.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `room-persistence-artifact`: the Owned lifecycle requirement replaces the non-deterministic "MAY
  terminate with an underlying Room exception" allowance with an `IllegalStateException` termination
  conditioned on close being the first terminal event, states the precedence of earlier terminal
  events, adds the close-context restriction, and adds the guarantee that a storage instance survives
  repeated `Fanbox` create/close cycles.

## Impact

- `fankt/fanbox-persistence-room/src/commonMain/.../RoomFanboxCookieStorage.kt`: post-close `Flow`
  termination and its KDoc.
- `fankt/fanbox-persistence-room/src/commonTest/.../RoomFanboxCookieStorageContract.kt`: shared
  Android and iOS verification.
- `README.md`: the Room persistence section's close guidance.
- No change to the `fanbox` artifact, to database schema or migrations, or to any public signature.
  `RoomFanboxCookieStorage.cookies` keeps its declared type, so the change is source- and
  binary-compatible; only the runtime termination of an already-collecting `Flow` becomes stricter
  and deterministic.
