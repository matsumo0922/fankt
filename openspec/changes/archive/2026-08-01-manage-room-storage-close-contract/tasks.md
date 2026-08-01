## 1. Deterministic post-close termination

- [x] 1.1 Replace the `closed` boolean in `RoomFanboxCookieStorage` with a `CompletableDeferred<Unit>`
      close signal whose completion is the close linearization point. Keep `close()` idempotent and
      non-suspending, complete the signal before closing the database, and keep every existing
      post-close `IllegalStateException` for non-`Flow` members.
- [x] 1.2 Expose `cookies` as a guarded composition: fail at collection start when already closed,
      terminate a live collection with `IllegalStateException` when the signal completes, rewrite a
      backend failure arriving after the signal into the same `IllegalStateException`, and rethrow a
      backend failure observed while the signal is incomplete. Do not intercept the collecting job's
      cancellation or a downstream collector failure.
- [x] 1.3 Extract that composition as an internal function taking the upstream `Flow` and the close
      signal, so it can be driven by a substitute upstream in tests. `RoomFanboxCookieStorage` calls
      it with the backend `Flow` and its own signal, and no public signature changes.

## 2. Verification

- [x] 2.1 Extend `RoomFanboxCookieStorageContract` so that a collection started before close, on the
      storage's own `cookies`, terminates with `IllegalStateException` after `close()`. Gate the close
      on the collection having delivered its first value, and assert that a value written after
      collection started was also delivered, so the assertion cannot pass on an unsubscribed or stale
      Flow.
- [x] 2.2 Extend the contract so that a `Flow` captured from the storage before close and first
      collected after close fails with `IllegalStateException`.
- [x] 2.2a Add a unit test over the internal composition from 1.3 that drives it with a substitute
      upstream counting its subscriptions, asserting that a collection started after the signal
      completes fails with `IllegalStateException` and never subscribes to that upstream. Without
      this, 2.2 passes even when the collection-start guard is missing.
- [x] 2.3 Extend the contract so that a collection of `Fanbox.cookies` for a `Fanbox` injected with
      the storage terminates with `IllegalStateException` when the storage is closed while that
      `Fanbox` is still open.
- [x] 2.4 Extend the contract so that after repeatedly creating and closing `Fanbox` instances over
      one injected storage, a Cookie record written through that storage is observed within a timeout
      by a collection established before those cycles, and the storage still reads and writes after
      the last `Fanbox` closes. Observing the new value, rather than an active `Job`, is what
      distinguishes a live collection from one suspended forever.
- [x] 2.4a Add unit tests over the internal composition from 1.3 asserting that a failure thrown by
      the collector propagates unchanged, and that cancelling the collecting coroutine before the
      close signal completes surfaces `CancellationException` rather than `IllegalStateException`.
      Assert with the same substitute upstream that a failure delivered while the signal is
      incomplete propagates unchanged, and one delivered after it completes becomes
      `IllegalStateException`.
- [x] 2.4c Add unit tests asserting that a collection which raises its own terminal event after close
      is linearized, but before it observes the close, still determines the observed cause. Cover both
      a collector failure and a cancellation, recording the documented runtime outcome for each side
      of the unobserved-close window.
- [x] 2.4b Extract the upstream cause selection as its own internal function and test it directly for
      both signal states. Driving it through the composition cannot distinguish a rewritten upstream
      failure from one produced by the close termination branch, so without this a missing rewrite can
      go undetected.
- [x] 2.5 Structure every new collection assertion so a thrown `IllegalStateException` cannot cancel
      the enclosing `runBlocking` scope before it is asserted: collect in a child that captures its
      outcome as a value, take delivered values from a channel rather than polling a flag, and bound
      each wait with a timeout so a hung collection fails instead of hanging the suite.
- [x] 2.6 Confirm the extended contract runs unchanged from both the Android (Robolectric) and iOS
      test entry points.

## 3. Documentation

- [x] 3.1 Update the `RoomFanboxCookieStorage` KDoc to state the `IllegalStateException` termination
      for `Flow` values obtained before close, its first-terminal-event condition, and that close must
      not run on a context that cannot progress concurrently with the storage's query dispatcher.
- [x] 3.2 Update the README Room persistence section with the same contract, the close-context
      restriction, and the note that the storage outlives repeated `Fanbox` create/close cycles.
- [x] 3.3 Grep `README.md` and `docs/` for the previous conditional wording and for
      `RoomFanboxCookieStorage` to confirm no stale statement remains.

## 4. Validation

- [x] 4.1 Run the full verification suite for the affected modules, including
      `:fankt:fanbox:verifyPersistenceBoundary` as the evidence that the core artifact carries no
      database, through the validation lease. Record the command, result, scope, and HEAD SHA.
