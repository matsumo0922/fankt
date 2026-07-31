## Why

Room-backed Cookie storage documents its post-close `Flow` behavior as "a `Flow` obtained before
close **can** terminate with an underlying database exception". A conditional statement is not a
contract: a host that collects `Fanbox.cookies` cannot write correct termination handling against
it, and nothing verifies what actually happens on either platform. Issue #53 requires the close
behavior of live collectors to be defined and verified on Android and iOS.

The rest of issue #53 is already satisfied by the current code but is not proven by a test. The
`fanbox` artifact has no Room dependency at all, so repeatedly creating and closing `Fanbox` cannot
accumulate `RoomDatabase` instances. That guarantee is currently implied by the dependency graph
rather than observed.

## What Changes

- Make the post-close termination of `Flow` values obtained from `RoomFanboxCookieStorage`
  deterministic: after `close()`, a live collector terminates with `IllegalStateException`, matching
  the failure mode of every other post-close operation on the storage. This replaces the current
  conditional "may terminate with an underlying Room exception" allowance.
- Verify that termination in the shared Android/iOS storage contract test, including a collector
  observing through the production `Fanbox.cookies` path.
- Verify that repeatedly creating and closing `Fanbox` instances over one injected Room storage
  leaves exactly one database instance, which stays usable after the last `Fanbox` closes.
- Update the `RoomFanboxCookieStorage` KDoc and the README persistence section to state the
  deterministic termination contract.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `room-persistence-artifact`: the Owned lifecycle requirement changes the post-close `Flow`
  allowance from a non-deterministic "MAY terminate with an underlying Room exception" to a
  deterministic `IllegalStateException` termination, and adds the host-observable guarantee that a
  storage instance survives repeated `Fanbox` create/close cycles.

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
