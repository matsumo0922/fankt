## Why

`Fanbox` が Cookie を内部 Room DB、CSRF token を process-global state に固定しているため、保存先の暗号化、アカウントごとの分離、Room を利用できない Kotlin/JS への展開をホストが制御できない。`fankt` の注入契約と PixiView-KMP の secure store・既存 Room DB 移行を同時に定義し、ライブラリ境界を変えても既存ユーザーをログアウトさせない移行経路を先に確定する。

## What Changes

- `FanboxCookieStorage` と `FanboxTokenStore` を public な suspend/Flow ベースの契約として追加し、request 用 snapshot と原子的な全 Cookie 置換を含めて `Fanbox` の生成時にインスタンス単位で注入できるようにする。
- Room を必要としない instance-local in-memory 実装を既定値として提供し、注入した storage だけで全 FANBOX API を実行可能にする。
- 現行 `PersistentCookieStorage` を同じ Cookie storage 契約の legacy Room 実装として残し、PixiView-KMP の移行完了までは既存 DB を読み出せる、DB instance を bridge 自身が所有する明示的な入口を提供する。
- **BREAKING**: 引数なし `Fanbox()` は source-compatible のまま、Cookie の再起動後永続化を暗黙には行わない。永続化が必要なホストは storage を明示注入する。
- **BREAKING**: process-global CSRF token を廃止し、token state を注入した `FanboxTokenStore` の所有範囲へ分離する。
- **BREAKING**: `Fanbox.setCookies()` に domain なしで渡した Cookie は `url` の origin host に限定し、兄弟 host へ送らない。FANBOX subdomain 間で共有する Cookie は `domain = ".fanbox.cc"` を指定し、session ID は `setFanboxSessionId()` で設定する。
- **BREAKING**: Android/iOS の secure credential は端末間 backup/transfer の対象外とし、機種変更または別端末への backup 復元後は再ログインを要求する。
- PixiView-KMP に Android Keystore-backed storage と iOS Keychain-backed storage を実装し、Koin から単一の storage instance を `FanboxRepositoryImpl` と `Fanbox` に注入する。
- PixiView-KMP の初回起動で legacy Room Cookie を secure store へ copy → completion marker と同時 commit → read-back verify → routing 切替 → legacy clear/close/delete の順に移す、crash-safe で再試行可能な移行を追加する。
- Android の secure payload と旧 `fankt.db` を cloud backup/device transfer から除外し、復元不能な暗号文は credential corruption と一時的な platform error を区別して再ログイン可能な空 store へ回復させる。
- 移行の開始・成功・再試行・失敗を Cookie 値なしで観測し、secure store が確定する前に legacy credential を削除しない。

## Capabilities

### New Capabilities

- `injectable-auth-storage`: Cookie と CSRF token の storage 契約、snapshot/atomic replacement、in-memory 既定実装、instance ownership、legacy Room 実装との接続を定義する。
- `pixiview-secure-session-migration`: PixiView-KMP の platform secure store、起動時の原子的・再試行可能な legacy Room 移行、ログアウト・観測契約を定義する。

### Modified Capabilities

- `in-memory-csrf-token`: process-global token sharing を、注入された token store 単位の所有と観測へ変更する。
- `request-time-csrf`: リクエスト送信時の token source を process-global state から注入 store へ変更する。
- `room-database-lifecycle`: 全 `Fanbox` が暗黙に共通 DAO を取得する要件を、明示的な legacy Room storage のみが DB を所有する要件へ変更する。

## Impact

- `fankt/fanbox`: `Fanbox` constructor、`FanboxDependencies`、Cookie/CSRF 公開 API、in-memory storage、legacy Room storage、mock-engine/ownership/migration-bridge tests、README/KDoc。
- `../PixiView-KMP`: `core/datastore` の secure credential storage と移行 state、platform 実装、`core/repository`/Koin の注入、起動順序、logout、telemetry、Android/iOS migration tests。
- 関連 Issue: `fankt#33`, `fankt#34`, `PixiView-KMP#106`, `PixiView-KMP#109`, `PixiView-KMP#104`。
- 実装・release は `fankt` の interface/legacy bridge 公開 → PixiView-KMP の secure migration 採用 → 移行猶予後の Room artifact 分離、の順で行う。

## Implementation Stages

- **This change / fankt#33**: `injectable-auth-storage` と既存 fankt capabilities の変更を実装し、Room-free default、storage injection、legacy bridge、tests、docs をこの PR で完了する。
- **Follow-up / PixiView-KMP#109**: `pixiview-secure-session-migration` は同時に確定した downstream contract だが、PixiView source implementation は sibling repository の独立 OpenSpec change と PR で行う。この PR は PixiView source を変更しない。
- **Follow-up / fankt#34**: PixiView の移行導入・観測後に Room artifact separation と transitional bridge removal を独立 change で行う。
