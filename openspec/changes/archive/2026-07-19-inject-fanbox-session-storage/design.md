## Context

現行 `Fanbox()` は `createFanboxDependencies()` で process singleton の `FanktDatabase` を開き、`PersistentCookieStorage` と `CookieDao.getAllCookies()` を別経路で組み立てる。CSRF token は top-level `processCsrfToken` に置かれるため、すべての既定 `Fanbox` が Cookie DB と token を共有する。PixiView-KMP は `FanboxRepositoryImpl` 内でこの constructor を直接呼び、`fanbox.cookies` から `FANBOXSESSID` を逆向きに観測している。

PixiView-KMP の現在の credential source of truth は fankt の `fankt.db` である。`OldCookieDataStore` はさらに古い形式から Room へ一度だけ移す経路であり、現在の並行保存先ではない。したがって fankt の既定値を in-memory に切り替えるだけでは、アップグレード直後の既存ユーザーがログアウトする。

この change は `fankt#33` と `PixiView-KMP#109` を一つの release choreography として設計する。成果物は fankt リポジトリに置くが、PixiView-KMP の変更は sibling repository で別 commit/PR として実装する。

## Goals / Non-Goals

**Goals:**

- Cookie と CSRF token の所有権を `Fanbox` 内部 singleton から constructor injection へ移す。
- 保存 backend と request-time Cookie policy を分離し、どの backend でも同じ domain/path/secure/expiry 判定を通す。
- 既定 `Fanbox()` を Room-free・instance-local にし、複数アカウントと将来の JS target の前提を作る。
- PixiView-KMP が Android/iOS の platform secure storage を唯一の永続 credential store として使う。
- 旧 `fankt.db` と `OldCookieDataStore` の両方から、credential を失わない crash-safe migration を行う。
- migration failure と cleanup pending を credential 非依存の telemetry で検知できるようにする。

**Non-Goals:**

- Room 実装を別 artifact へ物理分離すること（`fankt#34`）。
- Ktor 型を既存の `Fanbox.cookies` / `setCookies` からすべて排除すること（`fankt#35`）。新規 storage 契約だけは Ktor 非依存にする。
- Kotlin/JS target や Zipline guest を追加すること（`fankt#39`, `PixiView-KMP#104`）。
- Web login Cookie allowlist と Cookie 値ログの是正（`PixiView-KMP#106`）。secure migration の前に実施する release dependency とする。
- 複数アカウント UI を PixiView-KMP に追加すること。storage instance を分離できる契約までを扱う。

## Decisions

### 1. Storage contract は Ktor adapter ではなく正規化済み record の backend とする

public API を次の責務へ分ける。

```kotlin
data class FanboxCookieRecord(
    val domain: String,
    val path: String,
    val name: String,
    val value: String,
    val expiresAtEpochMilliseconds: Long?,
    val secure: Boolean,
    val hostOnly: Boolean,
)

interface FanboxCookieStorage {
    val cookies: Flow<List<FanboxCookieRecord>>
    suspend fun snapshot(): List<FanboxCookieRecord>
    suspend fun upsert(cookie: FanboxCookieRecord)
    suspend fun delete(domain: String, path: String, name: String)
    suspend fun deleteExpired(nowEpochMilliseconds: Long)
    suspend fun replaceAll(cookies: List<FanboxCookieRecord>)
    suspend fun clear()
}

interface FanboxTokenStore {
    val token: Flow<String?>
    suspend fun get(): String?
    suspend fun set(token: String?)
}
```

実装時の名称は既存 package 命名に合わせてよいが、意味と field は固定する。Cookie は入力時点で domain の有無を `hostOnly` として保持してから `fillDefaults(requestUrl)` を適用し、domain の leading dot/case、path、absolute expiry を正規化して record 化する。value を含む record の `toString()` は生成せず、KDoc で credential 扱いを明示する。

内部 `FanboxCookiesStorageAdapter : CookiesStorage` が Ktor `Cookie` との変換、host-only/domain/path/secure matching、期限切れ除外と cleanup、`FANBOXSESSID` replacement を一元化する。request path は観測用 Flow の最初の emission を待たず、必ず `snapshot()` から有限時間で現在値を読む。期限切れ cleanup は `deleteExpired(nowEpochMilliseconds)` の一回の条件付き mutation とし、backend は commit 時点の現在値を同じ条件で再評価する。snapshot 後に同一 identity が refresh されても、古い snapshot の identity を無条件削除しない。cleanup の cancellation は再送出し、その他の書き込み失敗では in-memory filtering を authoritative として request 自体を継続する。これにより Android Keystore、iOS Keychain、Room、in-memory の各 backend は request URL を知らず、security policy が分岐しない。

`Fanbox.setCookies()` が domain なしの Ktor `Cookie` を受け取ると、その record は `url` の origin host を所有者とする `hostOnly = true` になる。たとえば既定 `url` の `www.fanbox.cc` で作った host-only Cookie は `api.fanbox.cc` へ送らない。FANBOX subdomain 間で共有する Cookie は呼び出し側が `domain = ".fanbox.cc"` を指定し、`FANBOXSESSID` は domain-scoped record を作る `setFanboxSessionId()` を使用する。この strict behavior は backend によらず共通 adapter が適用する。

`replaceAll()` は backend ごとに単一 atomic commit とし、`Fanbox.setCookies(reset = true)` は clear + N upserts ではなく正規化済み全 records の `replaceAll()` を一度だけ呼ぶ。commit に失敗した場合は置換前 snapshot を維持する。`cookies` は UI/consumer 観測用で、collector ごとに現在 snapshot を少なくとも一度 emit する hot/current-value contract とする。request correctness 自体は Flow emission のタイミングに依存せず `snapshot()` を使う。

`FanboxCookieStorage : CookiesStorage` とする案は、ホスト実装ごとに matching を再実装させ、後続 `fankt#35` でも public Ktor dependency を残すため採用しない。session ID だけの store とする案も、`cf_clearance` 等を含む既存 Cookie contract を欠落させるため採用しない。

### 2. `Fanbox` は old positional arguments を維持し、storage を後置 default parameter で受け取る

public constructor は既存 `logLevel`, `ioDispatcher` の順序を維持し、その後に `cookieStorage = InMemoryFanboxCookieStorage()` と `tokenStore = InMemoryFanboxTokenStore()` を追加する。既存 positional/named source call を壊さず、各 constructor call が新しい store instance を得るよう default expression で生成する。default-argument synthetic signature は変わるため binary compatibility は保証せず、consumer の clean rebuild を v0.1.0 upgrade 条件とする。

引数なし constructor の「挙動が変わらない」は source compatibility と同一 process 内の API semantics を意味すると定義する。再起動後の暗黙 persistence と sibling `Fanbox` 間の暗黙 state sharing は、Phase 3/v0.1.0 の意図どおり breaking change として README、KDoc、release note に記載する。「default = in-memory」と再起動 persistence を同時に満たすことはできないため、Room を暗黙 default に残す案は採用しない。

注入 store の lifetime はホスト所有とし、`Fanbox.close()` は内部 HttpClient と adapter を閉じるが backing store を close/clear しない。同じ store instance を明示的に複数 `Fanbox` へ渡した場合だけ state を共有する。

### 3. CSRF state は Cookie store と対で注入するが、永続化しない

CSRF は短命なので PixiView-KMP も `InMemoryFanboxTokenStore` を Koin singleton として保持し、secure storage へ保存しない。`FanboxTokenStore` を public にするのは test double、multi-account isolation、将来の host/guest bridge を可能にするためであり、永続化を推奨するためではない。

`updateCsrfToken()`、session replacement、reset の順序は既存 spec を維持し、すべて同じ injected store に対して linearizable に行う。process-global top-level flow は削除する。

### 4. Room は明示的な移行 source / opt-in backend として一時的に残す

`PersistentCookieStorage` は record backend を実装する。既存 Room schema は host-only 区別を不可逆に失っているため schema v3 を変更せず、legacy bridge が読む全 record の `hostOnly` は `false` とする。legacy fallback 中の書き込みも `hostOnly` を `false` に coerce し、現行 domain-match 挙動と rollback compatibility を維持する。完全な host-only semantics は in-memory/secure backend に切り替わった後に適用する。

`createLegacyRoomCookieStorage(ioDispatcher)` のような public transitional factory から、Android の `getDatabasePath("fankt.db")` または iOS の `NSDocumentDirectory/fankt.db` を明示的に開けるようにする。この bridge は process-wide `getFanktDatabase()` singleton を再利用せず、bridge ごとに専用 `FanktDatabase` を build/own し、close 後の再取得では新しい instance を返す。これにより clear → close → DB/sidecar delete と cleanup retry を成立させる。

factory と cleanup API は experimental/deprecated-for-removal とし、削除条件を「PixiView-KMP のサポート対象最古 version が secure migration 導入版以降になり、`fankt#34` が legacy bridge 不要を確認したとき」と KDoc に記載する。日付だけの猶予は採用しない。

PixiView-KMP が file path や Room schema を複製して直接 SQLite を読む案は、fankt の migration/schema ownership を破るため採用しない。逆に Room を default のまま残す案は、host injection を採用しても JS/secure-storage 境界が曖昧なままになるため採用しない。

### 5. PixiView-KMP は routing storage と単一 secure payload を使う

`core/datastore` に以下の役割を置く。

- common `SecureFanboxCookieStorage`: `FanboxCookieStorage` 実装。全 Cookie records と `migrationVersion/status` を一つの payload として読み書きし、復号済み snapshot を process memory に保持する。request read はこの cache、mutation は cache と暗号化 payload の atomic commit を同じ mutex で更新する。初期化未完了または retryable failure 中は `snapshot()` と observation Flow が空 records を返し、書き込みだけを retryable initialization failure として拒否する。
- platform `SecureCredentialBackend`: Android は user authentication を要求しない Android Keystore の non-exportable AES-256-GCM key と `AtomicFile` による version/nonce/ciphertext の atomic payload を使う。iOS は `kSecAttrSynchronizable=false` かつ `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` の Keychain generic-password item を使う。
- common `MigratingFanboxCookieStorage`: legacy Room source と secure destination を持ち、`Mutex` 下で initialization と active backend switching を直列化する。
- `SessionMigrationReporter`: `started`, `succeeded`, `fallback_used`, `cleanup_pending`, `failed(stage, category)` だけを送る。Cookie name/value、payload、hash は送らない。

secure payload に records と completion marker を同居させる。別 DataStore に marker だけ置く案は、marker と Keychain/暗号文の commit 間に crash window を作るため採用しない。

Android の secure payload directory（quarantine file を含む）と `databases/fankt.db`（WAL/SHM を含む）は `backup_rules.xml` と `data_extraction_rules.xml` の cloud backup/device transfer から除外する。Keystore key は端末間復元されないため、暗号文だけを復元させない。復号時は次を区別する。

- key alias 不在、AEAD tag 不一致、payload format/version 破損は恒久的 credential corruption とし、legacy source の有無にかかわらず壊れた payload を先に quarantine/削除する。利用可能な legacy source があればそれを active にして新しい destination への migration を再試行し、なければ空の secure store を初期化して再ログインを許可する。復号可能な records-without-marker conflict は payload を処分しない別分岐とする。
- Keystore/Keychain の一時的 unavailable、iOS protected-data unavailable、I/O failure は上書きせず typed initialization failure として retry する。

migration initialization gate は secure/legacy/empty の routing 結果、または retryable failure の分類が確定するまで待つ。retryable failure と判定した時点では gate を解放して UI と非認証 path を進めてよい。この状態の request が認証失敗しても logged-out 表示に使うだけで、credential payload の削除・上書き・logout migration を発動しない。

Koin は `MigratingFanboxCookieStorage` と `InMemoryFanboxTokenStore` を singleton として生成し、`FanboxRepositoryImpl` constructor に渡す。repository 自身が `Fanbox()` を hidden new する構造をやめる。`FanboxRepository.sessionId` は同じ injected Cookie Flow から導出するため、ライブラリ DB を逆向きに読む依存も消える。

### 6. Migration は「destination commit を原子単位」にした idempotent state machine とする

起動時、最初の FANBOX request より前に次の順で行う。routing storage の read/write はこの判定が終わるまで同じ mutex で待つ。

1. secure payload が `complete` なら secure を active にし、残存 legacy cleanup だけ再試行する。
2. 未完了なら explicit legacy Room bridge から snapshot を読む。
3. snapshot を secure payload へ records + `complete` marker の単一値として commit する。
4. destination を読み戻し、`domain`, `path`, `name`, `value`, `expiresAtEpochMilliseconds`, `secure`, `hostOnly` と marker を完全一致で照合する。
5. routing target を secure に切り替える。
6. legacy Room rows を clear し、DB を close して `fankt.db` と SQLite sidecar を platform cleanup API で削除する。削除失敗は `cleanup_pending` とし、secure 利用を継続して次回再試行する。

3 または 4 が失敗した場合は legacy を active のまま維持し、source を変更せず `fallback_used` を記録する。destination commit 後に crash しても marker と records は同時に存在するため、次回は source から上書きせず cleanup へ進む。migration 中の login/logout/write は mutex 後の active backend にだけ適用される。

空の legacy snapshot も `complete` として commit し、新規 install が毎回 migration path を通らないようにする。復号可能な secure payload に Cookie があるのに marker だけない異常状態では自動上書きも削除もせず conflict failure とし、既存 destination を保持する。これは復号不能 payload を空 store へ回復させる分岐とは別である。

### 7. `OldCookieDataStore` は secure routing 初期化後の二段目 migration とする

Room migration を先に確定し、その後で `OldCookieDataStore` を読む。`;` で分割した各要素を `trim()` してから `split("=", limit = 2)` で name/value を分離する。値が存在し、active store に `FANBOXSESSID` がない場合だけ import し、secure destination の read-back が一致してから old preference を空にする。active store に session がある場合は Room/secure の新しい値を優先し、old preference は credential conflict telemetry を出して削除する。

### 8. Logout は credential source の同期的 clear とする

PixiView の `FanboxRepositoryImpl.logout()` は detached `CoroutineScope.launch` を廃止し、呼び出し元の suspend execution で active secure store、cleanup-pending の legacy Room、`OldCookieDataStore` を clear/verify してから `_logoutTrigger` を送出する。legacy bridge は migration state が cleanup 未完了のときだけ開き、既に削除済みの `fankt.db` を logout のために再生成しない。`FANBOXSESSID=""` の upsert は logout とみなさない。WebView Cookie の削除は Main dispatcher で試みるが、その失敗で durable store cleanup を中断しない。

### 9. Release は fankt と PixiView-KMP を二段階で同期する

1. `PixiView-KMP#106` を先行し、OAuth Cookie 保存と credential logging を止血する。
2. fankt で interface、in-memory defaults、legacy migration bridge、tests、docs を実装し v0.1.0 candidate を publish する。既定変更だけを既存 PixiView production へ先に出さない。
3. PixiView-KMP で secure/routing storage、migration、DI、telemetry、Android/iOS upgrade tests を実装し、candidate dependency と組み合わせる。
4. 旧版 DB fixture から upgrade する end-to-end test と実機 smoke test が通った同一 release train で PixiView を配信する。
5. migration success/fallback を観測し、PixiView のサポート対象最古 version が migration 導入版以降になった後に `fankt#34` で Room artifact を分離する。

## Risks / Trade-offs

- [既定 constructor の persistence が変わり、未移行 consumer がログアウトする] → v0.1.0 breaking change として告知し、既知 consumer の PixiView は同じ release train で storage 注入・migration を完了する。
- [Android/iOS secure API の失敗で app が credential を読めない] → migration 前は legacy fallback。一時的 platform error は上書きせず再試行し、鍵喪失・AEAD/format corruption は壊れた payload を隔離して再ログイン可能な空 store へ回復する。
- [二つの永続 store 間で真の distributed transaction は作れない] → destination の records + marker を単一 atomic valueにし、source deletion を最後にした idempotent state machine で crash safety を得る。
- [legacy DB file cleanup が open handle や sidecar のため失敗する] → explicit bridge の close 後に削除し、失敗は cleanup-pending として次回起動前に再試行する。rows は先に clear して credential residue を抑える。
- [public record に credential value が含まれる] → `toString`/logging を避け、telemetry は stage/category のみとする。value を hash 化しても stable identifier になるため送らない。
- [store を明示共有した複数 `Fanbox` で close/clear ownership が曖昧になる] → `Fanbox.close()` は backing store を所有・消去しない。host が store lifetime を管理する契約を KDoc に固定する。
- [secure payload 全体 rewrite の write/read amplification] → Cookie 集合は小さく mutation 頻度も低い。復号済み snapshot を memory cache し、request read では platform secure API を呼ばず、mutation の単一 commit のみを直列化する。
- [Android backup が暗号文だけを別端末へ復元する] → secure payload と旧 credential DB を cloud backup/device transfer から明示除外し、鍵喪失時の回復 test を持つ。

## Migration Plan

実装は fankt PR と PixiView-KMP PR を分けるが、この OpenSpec の task 順に連続して進める。rollback 時、PixiView の新 build は legacy source を secure commit 前まで保持するため旧 buildへ戻せる。ただし secure commit 後に旧 DB を削除した端末は旧 build が secure store を読めないため、production rollout 後の binary rollback は既存ユーザーをログアウトさせる。staged store rollout を停止して新規 upgrade を抑え、既に移行済みの端末には forward fix を配信する。新しい remote-control subsystem はこの Issue の scope に追加しない。

release 前に旧 fankt v1/v2/v3 DB fixture、空 DB、新規 install、secure write failure、verify failure、鍵喪失/復号不能 payload、retryable initialization、commit 後 crash、cleanup failure、logout の各 upgrade path を Android/iOS で検証する。legacy DB schema は v3 のままなので、secure commit 前の旧 build rollback は維持される。production は staged rollout とし、release owner が migration success/fallback/failed/cleanup-pending の観測値を確認してから rollout percentage を上げる。数値 gate や新しい alert subsystem の実装はこの change の scope に含めない。

## Open Questions

設計上の未決事項はない。release 固有の rollout percentage と alert threshold は code contract ではなく、配信時の既存運用 checklist で確定する。
