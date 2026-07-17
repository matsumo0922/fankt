## Why

`getFanktDatabase()` は呼び出すたびに同じファイルへ新しい Room database を構築するため、`Fanbox` の生成数に応じて接続と invalidation tracker が増え、同一プロセス内の DAO 間で変更通知を共有できない。Issue #23 の Phase 1 止血として、各 platform で database lifecycle をプロセス内の単一インスタンスへ統一する。

## What Changes

- Android と iOS の `getFanktDatabase()` は、最初の呼び出しで構築した `FanktDatabase` を以後の呼び出しへ再利用する。
- 複数の `Fanbox` dependency graph も同じ database instance から Cookie DAO と token DAO を取得する。
- production の database accessor を複数回呼ぶ platform test で参照同一性を証明する。

## Capabilities

### New Capabilities

- `room-database-lifecycle`: FANBOX persistence が platform ごとにプロセス内で共有する Room database の lifecycle を定義する。

### Modified Capabilities

なし。

## Impact

- `fankt/fanbox` の Android / iOS database builder と platform test を変更し、Android local test にのみ `androidx.test:core` と Robolectric を追加する。
- `FanboxDependencies` は既に 1 回の accessor 呼び出しから両 DAO を取得しており、public API の signature と database schema は変更しない。

## Decision Attribution

- （ユーザー確認済み）`getFanktDatabase()` をプロセス内の lazy singleton とし、同じ database から両 DAO を取得することは Issue #23 の契約である。
- （agent 仮決め）各 platform actual に Kotlin の `lazy` delegate を置き、既定の同期モードで最初の構築を直列化する。
- （agent 仮決め）Android は Robolectric local test、iOS は simulator test から production accessor を直接複数回呼び、成功した呼び出しの参照同一性を検証する。
