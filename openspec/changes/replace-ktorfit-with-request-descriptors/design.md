## Context

`Fanbox` は公開 suspend API を `FanboxPostRepository`、`FanboxCreatorRepository`、`FanboxSearchRepository`、`FanboxUserRepository` へ委譲し、全 repository が portable descriptor を構築して 1 個の shared executor から raw response を受け取り、endpoint-specific parser で domain model へ変換する。request operation は 29、unique route は 28 であり、`plan.listSupporting` だけが同じ descriptor に strict/tolerant の 2 parser mode を持つ。`Fanbox.buildResources()` は normal request 用 client/executor と streaming download 用 client を 1 個ずつ構築する。

request の path/query/body は `FanboxEndpoints` の純関数、JSON/HTML decode は `FanboxResponses`、credential-safe HTTP 実行は Ktor executor に分離されている。cursor/host 抽出、failure interpretation、tolerant list diagnostics を含む portable boundary は Ktor、Room、Napier に依存しない。download は complete media URL、streaming、redirect credential 規則が異なるため descriptor executor へ統合せず独立した client を使う。

#33〜#37 により auth storage 注入、Room 分離、公開 API の Ktor 型排除、stdlib time、domain model 純化が完了している。#38 は既存 `Fanbox` API と wire behavior を維持したまま、request/response core と Ktor executor を分離する最後の構造変更である。

## Goals / Non-Goals

**Goals:**

- 全 28 unique route を transport-neutral な descriptor と pure builder で表現する。
- raw JSON/HTML から既存 domain model または既存 typed failure を得る response path を Ktor/Room/Napier 非依存にする。
- normal FANBOX request を単一 Ktor executor に集約し、credential 解決前に endpoint、HTTPS origin、method、path、redirect を検証する。
- query、JSON value type、null omission、Cookie、request-time CSRF、default headers、tolerant decode、exception semantics を維持する。
- `:fankt:fanbox` から Ktorfit endpoint、generated API、Ktorfit processor dependency を除去する。
- 大きな変更を、各段階で build/test/rollback 可能な 5 PR に分割する。

**Non-Goals:**

- Kotlin/JS target、Zipline service/bridge、bundle 署名・配信を実装しない。
- `Fanbox.download` を descriptor executor に統合しない。download は任意の allowlisted media URL を streaming する別契約のまま保つ。
- domain model、公開 `Fanbox` operation、auth storage interface、Room schema を変更しない。
- Fantia module または Room persistence module から Ktorfit/KSP を除去しない。
- FANBOX endpoint の追加、request/response schema の機能変更、既存 failure policy の再設計を行わない。

## Decisions

### 1. （agent 仮決め）Portable contract は最小の internal データ型にする

`me.matsumo.fankt.fanbox.endpoint` に次の serializable contract を internal として追加する。Issue #38 の request assembly と将来の同一 module 内 Kotlin/JS 利用には internal で十分であり、host/guest bridge の公開契約はその bridge を設計する change で確定する。

```kotlin
@Serializable
@JvmInline
internal value class FanboxEndpointId(internal val value: String)

@Serializable
internal enum class FanboxHttpMethod { GET, POST }

@Serializable
internal data class FanboxQueryParameter(
    internal val name: String,
    internal val value: String,
)

@Serializable
internal data class RequestDescriptor(
    internal val endpointId: FanboxEndpointId,
    internal val path: String,
    internal val method: FanboxHttpMethod,
    internal val query: List<FanboxQueryParameter> = emptyList(),
    internal val jsonBody: String? = null,
)
```

endpoint ID の serialized value は既存の診断 label と同じ `post.info`、`follow.create`、`homepage` などの安定した文字列とする。query は `Map` ではなく ordered list にして、順序、同名 parameter、null omission を失わない。値は未 encode の文字列とし、executor が一度だけ percent-encode する。`jsonBody` は compact JSON text とし、number/string/null omission を endpoint test で固定する。

`path` は origin を含まない relative path とする。API path は `post.info` や `legacy/support/creator` とし、scheme、authority、leading `//`、userinfo、query、fragment、`.`/`..` segment を許さない。empty path は homepage endpoint ID に限って許可し、executor は `https://www.fanbox.cc/` の `GET /` へ明示的に正規化する。他の endpoint ID では empty path を拒否する。origin と header を descriptor に入れないことで、guest-controlled data が credential boundary を拡張できない。

**Alternative:** full URL と arbitrary headers を descriptor に含める案は OTA flexibility が高いが、bundle defect が credential exfiltration に直結するため不採用。query を `Map<String, String>` にする案は repeated parameter と順序を表現できないため不採用。

### 2. （agent 仮決め）`FanboxEndpoints` と trusted policy は意図的に分離する

`FanboxEndpoints` は domain ID や cursor を受け取り、全 endpoint の descriptor を返す pure internal object とする。GET query の optional value は null のとき pair 自体を省略する。POST body は request DTO または `buildJsonObject` を production `Json` で encode し、現行 wire type を維持する。`plan.listSupporting` の strict/tolerant response mode は同じ descriptor builder を共有する。

executor 側には internal `TrustedFanboxEndpointPolicy` を置き、endpoint ID ごとに trusted origin、allowed method、credential mode を保持する。

- 通常 API: exact origin `https://api.fanbox.cc`
- homepage: exact origin `https://www.fanbox.cc`
- method: endpoint ごとに GET または POST を固定

builder metadata をそのまま security policy として信頼せず、独立した host-owned table と照合する。これは重複ではなく、将来 untrusted guest が descriptor を返す境界で host policy を変更不能にするための security duplication である。coverage test が全 endpoint ID の builder、policy、diagnostic label を照合し、unknown ID を拒否する。

この設計では path/query/body の OTA 変更は host policy を変えずに表現できるが、origin または method の変更には host 更新が必要になる。credential safety を endpoint flexibility より優先する。

**Alternative:** descriptor と同じ catalog から policy を生成する案は改変された guest が policy も変更できるため不採用。path pattern まで host に固定する案は endpoint path 変更の OTA 修復を妨げるため不採用。

### 3. （agent 仮決め）Response parser は raw text と primitive metadata だけを受け取る

`me.matsumo.fankt.fanbox.response.FanboxResponses` に internal endpoint-specific parser を置き、raw JSON を `createFanboxJson()` で既存 entity に decode して既存 mapper へ渡す。homepage は raw HTML を `FanboxMetadataParser` へ渡す。POST の success body は利用せず Unit を返す現行契約を保つ。

`plan.listSupporting` は request descriptor を 1 個だけ持つ一方、response parser は `supportingPlansStrict(body)` と `supportingPlansTolerant(body, sink)` の 2 operation を明示する。strict/tolerant の選択は repository の public overload が行い、descriptor や executor policy には decode mode を混ぜない。inventory は **request route 28 unique / request operation 29 / response decode operation 29** を別々に数える。

Ktor `Url` に依存する cursor/host 抽出は internal pure `FanboxUrlParts` へ置き換える。必要な範囲を RFC 3986 に限定し、query を一度だけ percent-decodeする。`+`、encoded `&`/`=`、empty/missing value、duplicate key、malformed escape、host case を fixture test で固定する。

server が返す pagination `nextUrl` は実行対象として保持せず、現行どおり response parser が query component だけを `FanboxCursor` または page/offset 値へ変換する。次ページ request は同じ既知 endpoint ID の relative path と decoded cursor query から `FanboxEndpoints` が再構築する。絶対 `nextUrl` を executor へ渡す bypass は設けない。

`FanboxExceptionFactory` は次の 2 層へ分ける。

- pure failure interpreter: endpoint ID、status code、header values、raw body、clock value から既存 `FanboxException` を構築し、sanitize/bound response fragment と Retry-After を解釈する。
- Ktor adapter: `HttpResponse` を一度 raw primitive へ変換し、network/cancellation と pure interpreter を接続する。

HTTP-date は Ktor `fromHttpToGmtDate` に頼らず、現在受理する IMF-fixdate と delta-seconds を pure parser で扱う。schema mismatch は endpoint ID を request から逆引きせず descriptor から受け取る。

`FanboxListItemDecoder` は Napier を直接呼ばず、default no-op の `FanboxDiagnosticSink` を受け取る。既存 runtime は executor/parser wiring から Napier-backed sink を注入し、既存 public mismatch callback と partial result を維持する。raw fragment の sanitize/bound 処理は pure diagnostic utility へ移す。

**Alternative:** Ktor ContentNegotiation を parser 内から呼ぶ案は transport separation を達成しないため不採用。新しい JSON/domain model を導入する案は既存 fixture と mapper contract を重複させるため不採用。

### 4. （agent 仮決め）Normal request は raw-response 型の単一 executor を通す

internal `FanboxRequestExecutor` は `RequestDescriptor` を受け、status、headers、body text を保持する internal transport-neutral response を返す。Ktor implementation は ContentNegotiation を install せず、accepted descriptor から URL/body を構築して response body を一度だけ読む。repository は descriptor を buildし、executor を呼び、success body を `FanboxResponses` へ渡す。

これにより ContentNegotiation 有り/無しの 2 client と 2 Ktorfit instance は 1 request client + 1 executor に統合できる。executor は常に raw text を返して pure parser が decode するため、endpoint ごとの ContentNegotiation client variant は不要であり trusted policy に variant を追加しない。origin、referer、user-agent、Cookie、CSRF は単一 request client の共通設定として全 endpoint に適用する。download client は streaming/backpressure/redirect credential rules が異なるため独立して残す。`FanboxHttpClientFactory` と MockEngine injection は executor client の test seam として維持する。

Ktorfit と executor が共存する PR 2〜3 でも、全 client は同一 `FanboxDependencies.cookieStorage` adapter と同一 `getCsrfToken` provider を受け取る。HttpClient ごとの独立 Cookie jar/token cache は作らず、metadata GET で更新した host-owned store の状態を executor POST から直ちに参照できる。

non-success response は pure failure interpreter で既存 typed exception に変換する。decode failure は parser が `SchemaMismatch` に変換する。CancellationException と network failure の既存伝播/normalization を維持する。

**Alternative:** executor が typed entity を返す案は transport と decode を再結合するため不採用。executor が Ktor `HttpResponse` を repository へ返す案は response path に Ktor type が残るため不採用。

### 5. （ユーザー確認済み）Credential は検証済み request にだけ付与する

executor の処理順を固定する。

1. endpoint ID を trusted policy で解決する。
2. descriptor method と policy method を照合する。
3. relative path と query component を検証し、trusted HTTPS origin から URL を構築する。
4. 構築後 URL の scheme、host、port、userinfo が policy と完全一致することを再確認する。
5. ここまで成功した後だけ Cookie storage と token store を参照する。
6. domain-matched Cookie、request-time CSRF、origin/referer/user-agent を付与して送信する。

transport adapter が検証済み request に `x-csrf-token` を明示設定している場合は、token store の default 値で上書きしない。descriptor 自体には header を持たせず、現行 client plugin の non-overwrite semantics だけを維持する。

Ktor の unvalidated automatic redirect は無効化する。GET redirect は Location を解決した後、元 endpoint policy の exact origin と method を再検証し、成功した hop にだけ credential を付与する。cross-origin、HTTPS downgrade、userinfo、method mismatch、unknown/malformed Location は redirected request の前に拒否し、redirect loop は bounded hop count で拒否する。POST redirect は generated client の実効挙動と揃え、301/302/307/308 の全てを 2 回目の credential lookup または request send 前に拒否する。

validation failure は internal `InvalidRequestDescriptorException` とし、network、Cookie storage、token store のどれにも触れない。公開 `Fanbox` API は library-produced descriptor しか使わないため通常発生せず、将来 host bridge が untrusted descriptor を受けるときの fail-closed boundary になる。

**Alternative:** HttpCookies と CSRF plugin に先に request を渡してから URL を検査する案は credential lookup/attachment が先行し得るため不採用。redirect を Ktor default に任せる案は CSRF header の cross-origin 伝播を host policy が検査できないため不採用。

### 6. （agent 仮決め）Ktor-free boundary は package と build verification の両方で守る

portable source を次の package boundary に集約する。

- `endpoint/`: descriptor/value types、endpoint IDs、`FanboxEndpoints`
- `response/`: `FanboxResponses`、pure failure interpreter、URL/query/HTTP-date utilities、diagnostic sink
- 既存 `datasource/mapper` と `datasource/parser`: entity→model と HTML parse。Ktor/Napier reference を除去する

Ktor、Cookie adapter、client builder、redirect handling は `transport/ktor` 相当の internal boundary に限定する。Room は persistence artifact に留める。

Gradle verification は対象 source tree の import を scan し、`io.ktor`、`androidx.room`、`io.github.aakira.napier` を拒否する。既存 `verifyKtorBoundary` は public ABI gate として残し、新 gate は internal portable core gate として追加する。

### 7. （agent 仮決め）Ktorfit/KSP cleanup は fanbox module に限定する

削除対象は fanbox の 4 Ktorfit interface、generated `createFanbox*Api` call、fanbox module の Ktorfit plugin/dependency/processor wiring である。Fantia は Ktorfit、persistence modules は Room KSP を使うため、global KSP plugin、version catalog entry、共通 Ktorfit convention plugin は利用者が残る限り削除しない。

`MavenPublishPlugin` の sources task は `kspCommonMainKotlinMetadata` を文字列で無条件参照せず、task provider が存在する module では引き続き mandatory dependency とし、存在しない module だけ skip する。dependency 自体を全 module で緩和しない。fanbox publish が missing-task なしで構成できることに加え、Fantia/Room の sources artifact が generated sources task 完了後に作られ、生成 source を欠落させないことを configuration/test する。

`FanboxRouteDriftTest` は annotation source scan を廃止し、endpoint ID、builder inventory、trusted policy、response parser mode の coverage を検査する。29 declaration/28 unique route の既存意味を保ち、strict/tolerant `plan.listSupporting` の重複を明示する。

## Risks / Trade-offs

- **[query/body wire drift]** Ktorfit の encode と pure builder が異なる可能性がある → 全 endpoint の descriptor golden test と既存 MockEngine testで decoded query、JSON value type、null omission を比較してから generated API を削除する。
- **[response behavior drift]** raw body の read timing、schema mismatch、Retry-After、tolerant decode が変わる可能性がある → #15 fixtures、HTTP exception tests、tolerant callback tests を parser/executor 経路へ移し、old/new differential test を migration stage 中だけ保持する。
- **[credential leakage]** malicious path または redirect が trusted origin を脱出する可能性がある → trusted origin を descriptor に含めず、credential lookup 前と各 redirect hop 前に exact policy validation を行う。
- **[redirect wire drift]** 現行 Ktor automatic redirect に依存する endpoint があると明示 handling への移行で挙動が変わる → GET の同一 origin・同一 allowed method redirect だけを explicit handler で追従し、POST redirect は 301/302/307/308 の全てを拒否する。MockEngine と一時的な generated/executor differential assertion で現行実効挙動との parity を固定する。
- **[security metadata duplication]** builder と policy がずれる可能性がある → 独立を維持したまま exhaustive coverage test で全 ID と method を照合し、unknown ID は常に拒否する。
- **[manual URL/HTTP-date parser defect]** Ktor utility 撤去で edge case を落とす可能性がある → 使用範囲を cursor/host/Retry-After に限定し、現行 behavior と encoded/malformed fixtures を固定する。
- **[mixed migration graph]** stage 3〜4 で Ktorfit と executor が一時共存し client graph が複雑になる → repository 単位で ownership を分け、同じ operation が二重送信されない構成 test を置き、共存は 2 stage に限定する。
- **[publication breakage]** fanbox の KSP task 消滅で sources/publish configuration が失敗する可能性がある → optional task dependency と fanbox/KSP module の両 configuration test を cleanup stage に含める。
- **[Fantia regression]** shared plugin を削除すると Fantia build が壊れる → fanbox-specific removal に限定し、Fantia compile/publish task graph を verification に含める。
- **[bridge contract の先取り]** descriptor/parser を public 化すると Zipline bridge 前に不要な互換性を背負う → この change では portable core 全体を internal に保ち、公開 service/serialization contract は bridge versioning と同時に確定する。

## Migration Plan

OpenSpec change は最終契約を 1 つに保ち、実装を次の 5 PR に分割する。各 PR は前段へ stack し、単独で build/test 可能にする。

1. **PR 1 — portable core**
   - descriptor/value types、全 `FanboxEndpoints`、全 `FanboxResponses` を追加する。
   - URL/query/HTTP-date/diagnostic utility を pure 化し、mapper/parser/tolerant decoder から Ktor/Napier を除去する。
   - 全 endpoint descriptor golden、response fixtures、portable import gate を追加する。
   - runtime repository は Ktorfit のまま保ち、公開 operation の実行経路を変えない。

2. **PR 2 — executor and security policy**
   - trusted endpoint policy、raw response、single Ktor executor、explicit redirect validation を追加する。
   - credential-before-validation rejection、origin/method/path/redirect、request-time CSRF、Cookie/default header、failure normalization の MockEngine test を追加する。
   - executor は既存 Ktorfit と並存するが public operation にはまだ接続しない。

3. **PR 3 — post repository migration**
   - `FanboxPostRepository` の GET/POST を descriptor→executor→parser へ移す。
   - comment、like、pagination、post mapping、exception/tolerant tests を新経路へ移す。
   - creator/search/user は Ktorfit を使い続け、mixed graph の lifecycle/close を検証する。

4. **PR 4 — remaining repositories and generated API removal**
   - creator、search、user、homepage を新経路へ移す。
   - 4 Ktorfit interface と generated API wiring を削除し、route drift guard を descriptor inventory guard へ置き換える。
   - request client を 1 個に集約し、download client との lifecycle を検証する。

5. **PR 5 — build cleanup and documentation**
   - fanbox module の Ktorfit plugin/dependency/processor を除去する。
   - publication task dependency を task-existence 条件付きにし、存在する module では mandatory ordering を維持したまま fanbox publish と KSP 利用 module の generated-sources task graph を検証する。
   - ABI/boundary/compatibility/allTests を実行し、README に portable core と credential-safe executor の現在の境界を記載する。

各 stage の rollback は当該 PR の revert とする。DB/data migration はない。PR 3 の rollback は post repository を generated API へ戻し、PR 4 の rollback は残り repository と generated interface を戻す。PR 5 は runtime code を変えず build wiring だけを戻せる。最終 stage 完了前は Ktorfit dependency を残し、途中状態で generated symbol を解決不能にしない。

## Independent Falsification

clean-context の Opus falsifier による初回 code-aware pass は blocking 0 だったが、遅延して返った fact-limited pass が blocking 4 件を提示した。書き手が各 finding を production code と baseline specs へ照合して次の処置を反映し、Opus の独立 closure pass が旧 finding F1〜F5 をすべて **CLOSED**、NEW blocking なし、最終 blocking 0 / **PASS** と判定した。

- **KSP publication ordering（設計修正）**: `MavenPublishPlugin` の dependency を全体で optional にせず、`kspCommonMainKotlinMetadata` が存在する module では mandatory のまま、存在しない fanbox だけ skip する。Fantia/Room の generated sources inclusion を検証する。
- **strict/tolerant decode mode（設計修正）**: `plan.listSupporting` は 1 descriptor と strict/tolerant 2 parser operation を持ち、repository overload が mode を選ぶ。request operation 29 / unique route 28 / response decode operation 29 を別 inventory とする。
- **absolute pagination URL（反例前提を無効化）**: 現行 `FanboxCursor.translateToCursor()` と mapper は `nextUrl` を実行せず query 値だけを取り出している。同じ endpoint ID の relative request を再構築する契約を spec 化し、absolute URL executor bypass を禁止する。
- **ContentNegotiation client variant（反例前提を無効化）**: executor は raw body だけを返すため CN variant は不要である。default headers/Cookie/CSRF は endpoint policy ではなく単一 request client の共通設定として維持する。
- **mixed graph auth state（反例前提を無効化 + 設計強化）**: 現行の全 client は同じ `FanboxDependencies.cookieStorage` adapter と `getCsrfToken` provider を共有する。新 executor も同じ instance を受け取り、metadata refresh→executor POST の回帰テストで観測する。

先行 pass の non-blocking 指摘も、mapper 全体の Ktor URL 除去、explicit CSRF header non-overwrite、portable core の internal 化、homepage exact `GET /`、PR 2 executor client の owned-list 登録として反映済みである。redirect は GET の同一 origin・allowed method の現行挙動を explicit handler で維持し、cross-origin/downgrade/method-change と全 POST redirect を拒否する。

実装前 gate は、Opus closure pass の blocking 0 と `openspec validate` 成功により成立する。

## Open Questions

blocking な未決事項はない。Zipline service interface、host/guest bridge version、bundle policy は Kotlin/JS/Zipline change の設計対象であり、この change の portable contract と trusted executor boundaryを前提として別に定義する。
