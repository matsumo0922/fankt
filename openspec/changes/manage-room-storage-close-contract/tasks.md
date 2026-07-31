## 1. Deterministic post-close termination

- [ ] 1.1 Replace the `closed` boolean in `RoomFanboxCookieStorage` with a `CompletableDeferred<Unit>`
      close signal whose completion is the close linearization point. Keep `close()` idempotent and
      non-suspending, complete the signal before closing the database, and keep every existing
      post-close `IllegalStateException` for non-`Flow` members.
- [ ] 1.2 Expose `cookies` as a guarded composition: fail at collection start when already closed,
      terminate a live collection with `IllegalStateException` when the signal completes, rewrite a
      backend failure arriving after the signal into the same `IllegalStateException`, and rethrow a
      backend failure observed while the signal is incomplete. Do not intercept the collecting job's
      cancellation or a downstream collector failure.

## 2. Verification

- [ ] 2.1 Extend `RoomFanboxCookieStorageContract` so that a collection started before close, on the
      storage's own `cookies`, terminates with `IllegalStateException` after `close()`. Gate the close
      on a `CompletableDeferred` the collection completes after its first value, and assert that a
      value written after collection started was also observed, so the assertion cannot pass on an
      unsubscribed or stale Flow.
- [ ] 2.2 Extend the contract so that a `Flow` captured from the storage before close and first
      collected after close fails with `IllegalStateException`.
- [ ] 2.3 Extend the contract so that a collection of `Fanbox.cookies` for a `Fanbox` injected with
      the storage terminates with `IllegalStateException` when the storage is closed while that
      `Fanbox` is still open.
- [ ] 2.4 Extend the contract so that repeatedly creating and closing `Fanbox` instances over one
      injected storage leaves a collection established before those cycles live, and leaves the
      storage able to read and write after the last `Fanbox` closes.
- [ ] 2.5 Structure every new collection assertion so a thrown `IllegalStateException` cannot cancel
      the enclosing `runBlocking` scope before it is asserted: collect in a child that captures its
      outcome as a value, use `CompletableDeferred` rather than polling as the start barrier, and
      bound each wait with a timeout so a hung collection fails instead of hanging the suite.
- [ ] 2.6 Confirm the extended contract runs unchanged from both the Android (Robolectric) and iOS
      test entry points.

## 3. Documentation

- [ ] 3.1 Update the `RoomFanboxCookieStorage` KDoc to state the `IllegalStateException` termination
      for `Flow` values obtained before close, its first-terminal-event condition, and that close must
      not run on a context that cannot progress concurrently with the storage's query dispatcher.
- [ ] 3.2 Update the README Room persistence section with the same contract, the close-context
      restriction, and the note that the storage outlives repeated `Fanbox` create/close cycles.
- [ ] 3.3 Grep `README.md` and `docs/` for the previous conditional wording and for
      `RoomFanboxCookieStorage` to confirm no stale statement remains.

## 4. Validation

- [ ] 4.1 Run the full verification suite for the affected modules, including
      `:fankt:fanbox:verifyPersistenceBoundary` as the evidence that the core artifact carries no
      database, through the validation lease. Record the command, result, scope, and HEAD SHA.
