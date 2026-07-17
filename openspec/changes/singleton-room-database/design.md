## Context

`getFanktDatabase()` is an internal expect/actual accessor used by `createFanboxDependencies()`. Both platform actuals currently call `Room.databaseBuilder(...).build()` on every access. The current dependency factory already stores one accessor result and derives both DAOs from it, but constructing another `Fanbox` still opens another Room instance for the same `fankt.db` file.

The database is internal, has no public close API, and lives for the application process. Android obtains its path from the `FanktInitializer` application context; iOS obtains it from the documents directory. The database schema, migrations, drivers, and query dispatcher are unchanged.

## Goals / Non-Goals

**Goals:**

- After successful initialization, return one `FanktDatabase` reference from every successful `getFanktDatabase()` call within a platform process.
- Serialize first initialization so concurrent first callers cannot construct multiple Room instances.
- Keep Cookie and token DAOs on the same database instance across all `Fanbox` dependency graphs.
- Exercise the production accessor itself in platform tests.

**Non-Goals:**

- Add database shutdown or test reset APIs.
- Change the Room schema, migration policy, database path, driver, or query dispatcher.
- Change public `Fanbox` APIs or make Room an optional artifact; that remains Phase 3 work in the parent epic.

## Decisions

### 1. Keep one platform-local lazy database

（ユーザー確認済み）Each Android and iOS actual owns a private top-level `Lazy<FanktDatabase>`. `getFanktDatabase()` returns that lazy value. The builder chain remains inside the lazy initializer, so no database or connection exists before first use.

（agent 仮決め）Use `lazy(LazyThreadSafetyMode.SYNCHRONIZED)` explicitly. The default is synchronized today, but spelling out the mode makes the concurrent-first-call invariant visible and prevents a later refactor from selecting publication or no synchronization. A manually synchronized nullable field was rejected because it duplicates the standard library's publication and memory-visibility behavior.

### 2. Preserve platform ownership and configuration

（agent 仮決め）Do not move Room construction into common code. Android and iOS retain their current path resolution, SQLite driver, migration registration, downgrade behavior, and IO query context; only the lifecycle wrapper changes. A common singleton abstraction was rejected because it would broaden the diff without improving the issue contract and could obscure platform construction failures.

### 3. Verify reference identity through production accessors

（agent 仮決め）Android and iOS tests call the real `getFanktDatabase()` concurrently and repeatedly, then assert reference identity across successful calls. The Android test is a Robolectric local test and uses `androidx.test:core` to initialize the existing `FanktInitializer` with an application context before accessing the database. These test-only dependencies do not enter the published artifact. The iOS test runs on the simulator and uses the production documents-directory path. These tests prove the issue's observable contract through the same accessor used by `createFanboxDependencies()`, rather than testing a hand-built lazy helper.

The current `createFanboxDependencies()` implementation is also kept as an explicit production-path invariant: it assigns `val database = getFanktDatabase()` once and obtains both DAOs from that value.

## Risks / Trade-offs

- [A failed first build is observed differently by concurrent callers] → Kotlin `lazy` propagates the initializer exception to that caller and does not cache it; a later caller retries. The contract covers successful calls only, so it does not claim that a failed caller receives a reference, and no partially initialized database is published.
- [A process-lifetime database cannot be explicitly closed] → The database is internal and already has no close lifecycle exposed. Process lifetime is the intended ownership boundary in Issue #23.
- [Concurrent access overlaps database construction] → `LazyThreadSafetyMode.SYNCHRONIZED` permits one initializer execution at a time and publishes only one successful value. If an execution throws, a later caller may retry serially.
- [Platform behavior drifts] → Keep symmetric Android and iOS implementations and direct platform identity tests; final validation compiles both platform source sets.

## Migration Plan

No data migration or rollout sequencing is required. Existing `fankt.db` files open with the same builder configuration. Rollback restores per-call construction without changing stored data.

## Open Questions

なし。残る agent 仮決めは lazy synchronization mode、successful-call failure semantics、Robolectric / iOS simulator test setup であり、PR の人間確認事項へ転記する。
