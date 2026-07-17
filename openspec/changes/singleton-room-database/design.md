## Context

`getFanktDatabase()` is an internal expect/actual accessor used by `createFanboxDependencies()`. Both platform actuals currently call `Room.databaseBuilder(...).build()` on every access. The current dependency factory already stores one accessor result and derives both DAOs from it, but constructing another `Fanbox` still opens another Room instance for the same `fankt.db` file.

The database is internal, has no public close API, and lives for the application process. Android obtains its path from the `FanktInitializer` application context; iOS obtains it from the documents directory. The database schema, migrations, drivers, and query dispatcher are unchanged.

## Goals / Non-Goals

**Goals:**

- Return one `FanktDatabase` reference from every `getFanktDatabase()` call within a platform process.
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

（agent 仮決め）Android and iOS tests call the real `getFanktDatabase()` twice and assert reference identity. The Android local test initializes the existing `FanktInitializer` with a test context before accessing the database. These tests prove the issue's observable contract through the same accessor used by `createFanboxDependencies()`, rather than testing a hand-built lazy helper.

The current `createFanboxDependencies()` implementation is also kept as an explicit production-path invariant: it assigns `val database = getFanktDatabase()` once and obtains both DAOs from that value.

## Risks / Trade-offs

- [A failed first build is retried on a later access] → Kotlin `lazy` does not cache exceptions; retry preserves existing failure propagation and avoids retaining a partially initialized database.
- [A process-lifetime database cannot be explicitly closed] → The database is internal and already has no close lifecycle exposed. Process lifetime is the intended ownership boundary in Issue #23.
- [Concurrent first access constructs more than once] → `LazyThreadSafetyMode.SYNCHRONIZED` permits one initializer execution and safely publishes the result.
- [Platform behavior drifts] → Keep symmetric Android and iOS implementations and direct platform identity tests; final validation compiles both platform source sets.

## Migration Plan

No data migration or rollout sequencing is required. Existing `fankt.db` files open with the same builder configuration. Rollback restores per-call construction without changing stored data.

## Open Questions

なし。残る agent 仮決めは lazy synchronization mode と platform test setup であり、PR の人間確認事項へ転記する。
