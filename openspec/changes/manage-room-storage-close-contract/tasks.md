## 1. Deterministic post-close termination

- [ ] 1.1 Replace the `closed` boolean in `RoomFanboxCookieStorage` with a `CompletableDeferred<Unit>`
      close signal, keeping `close()` idempotent and non-suspending and keeping every existing
      post-close `IllegalStateException` for non-`Flow` members.
- [ ] 1.2 Expose `cookies` as a guarded composition: fail at collection start when already closed,
      terminate a live collector with `IllegalStateException` when the signal completes, and rewrite
      a backend failure that arrives after the signal into the same `IllegalStateException` while
      rethrowing failures observed while open.

## 2. Verification

- [ ] 2.1 Extend `RoomFanboxCookieStorageContract` so that a collector started before close, on the
      storage's own `cookies`, terminates with `IllegalStateException` after `close()`, and assert
      that the collector observed at least one value before close so the assertion cannot pass on an
      unsubscribed Flow.
- [ ] 2.2 Extend the contract so that a collector on `Fanbox.cookies` for a `Fanbox` injected with the
      storage terminates with `IllegalStateException` when the storage is closed while that `Fanbox`
      is still open.
- [ ] 2.3 Extend the contract so that repeatedly creating and closing `Fanbox` instances over one
      injected storage leaves a pre-existing collector live and leaves the storage able to read and
      write after the last `Fanbox` closes.
- [ ] 2.4 Confirm the extended contract runs unchanged from both the Android (Robolectric) and iOS
      test entry points.

## 3. Documentation

- [ ] 3.1 Update the `RoomFanboxCookieStorage` KDoc to state the deterministic `IllegalStateException`
      termination for `Flow` values obtained before close, replacing the conditional wording.
- [ ] 3.2 Update the README Room persistence section with the same contract and with the note that
      the storage outlives repeated `Fanbox` create/close cycles.
- [ ] 3.3 Grep `README.md` and `docs/` for the previous conditional wording and for
      `RoomFanboxCookieStorage` to confirm no stale statement remains.

## 4. Validation

- [ ] 4.1 Run the full verification suite for the affected modules through the validation lease and
      record the command, result, scope, and HEAD SHA.
