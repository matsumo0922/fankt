# Design

## Context

#38 完了時点で `:fankt:fanbox` は次の構造を持つ。

- ターゲットは `androidTarget` と ios 3 種のみ。
- `VerifyPortableImportBoundaryTask`（`fankt/fanbox/build.gradle.kts:921`）が `endpoint/**`、`response/**`、`datasource/mapper/**`、`datasource/parser/**` から `io.ktor` / `androidx.room` / `io.github.aakira.napier` の import を拒否している。
- しかし portable core と HTTP 実行コードはどちらも `commonMain` にあり、source set としては分離されていない。

この change の主眼は、**import 境界として存在するだけの portable core を、source set 境界として実体化する**ことにある。JS ターゲットの追加自体は、その帰結として自然に通る。

### 現状の依存分布

`commonMain` を JS 非対応要因ごとに分類した結果は次のとおり。

| ファイル | Ktor | Napier | kotlinx.io | Ksoup | Dispatchers.IO |
|---|---|---|---|---|---|
| `Fanbox.kt` | ✅ | ✅ | ✅ | | ✅ |
| `ClientBuilder.kt` | ✅ | ✅ | ✅ | | |
| `FanboxDependencies.kt` | ✅ | | | | |
| `FanboxCookiesStorageAdapter.kt` | ✅ | | | | |
| `FanboxDownloadDestination.kt` | ✅ | | | | |
| `FanboxExceptionFactory.kt` | ✅ | | | | |
| `transport/ktor/KtorFanboxRequestExecutor.kt` | ✅ | ✅ | ✅ | | |
| `transport/ktor/FanboxDescriptorValidator.kt` | ✅ | | | | |
| `repository/Fanbox*Repository.kt`（4 ファイル） | | | | | ✅ |
| `datasource/parser/FanboxMetadataParser.kt` | | | | ✅ | |

これ以外の 60 ファイル強（domain model、entity、mapper、endpoint、response、`FanboxAuthStorage`、`FanboxException`、`FanboxJson`、`FanboxListItemSchemaMismatch`、`FanboxLogLevel`、`TrustedFanboxEndpointPolicy`）は kotlinx.serialization / kotlinx.coroutines の共通 API / `kotlin.time` のみに依存しており、JS で解決できる。

加えて、`commonMain` が適用している `libs.bundles.infra.api` に **JVM 専用の `kotlin-stdlib-jdk8` と `kotlin-reflect`** が含まれている（`gradle/libs.versions.toml:193-197`）。これは JS ターゲット追加時に依存解決の段階で失敗するため、ソース移動とは独立に対処が必要になる。

## Goals / Non-Goals

### Goals

- portable core を `commonMain` に残したまま JS でコンパイルできる状態にする。
- `:fankt:fanbox:jsTest` で mapper / parser / endpoint / response / domain model のフィクスチャテストが実行される。
- Android と iOS の公開 API と wire 挙動を維持する。
- source set の分離により、portable core への非 portable 依存の混入がコンパイル時に検出されるようにする。

fankt の利用者は PixiView（同一開発者）のみである。ABI dump は外部利用者への互換の約束ではなく、**意図しない公開 API の変化を検出するための回帰検査**として扱う。本 change は source set の再配置とターゲット追加であり公開 API の意味を変えないため、dump の宣言内容が変化した場合は移動の副作用を疑う手がかりとする。

### Non-Goals

- Zipline の導入と guest 化（PixiView-KMP#104 の担当）。
- Kotlin 2.3.x への引き上げ。
- JS からの HTTP 実行。
- wasmJs ターゲット。

## Decisions

### D1: source set 階層 — Android と iOS が共有する intermediate source set を導入する

Ktor 実行コードは Android と iOS の両方で必要だが JS では不要である。`commonMain` から外したこれらを両方に置くと重複するため、両者を親とする intermediate source set を設ける。

```
commonMain            portable core（endpoint / response / mapper / entity / domain / auth contract / exception）
  └─ clientMain       Fanbox / ClientBuilder / transport.ktor / repository / parser / Ktor 依存の例外変換
       ├─ androidMain  Ktor OkHttp engine
       └─ iosMain      Ktor Darwin engine
  └─ jsMain           （実装追加なし）
```

`applyDefaultHierarchyTemplate()` は android + ios を束ねる既定の group を持たないため、この source set は明示的に宣言する。名前は `clientMain` とする。「HTTP client を持つ実装」という役割を表し、既定テンプレートの予約名（`nativeMain` / `appleMain` など）と衝突しない。

この構成が Kotlin 2.2.10 で成立することは、最小プロジェクトの実ビルドで確認済みである。`applyDefaultHierarchyTemplate()` と手書き `clientMain`（`androidMain` / `iosMain` が `dependsOn`）、および `js(IR) { nodejs(); binaries.library() }` を同時に宣言した状態で `compileKotlinJs` と JVM 側コンパイルが成功し、`commonMain` の `internal` 宣言を `clientMain` から参照できることも併せて確認した。

**代替案**: `commonMain` を portable core 専用にせず、非 portable コードを `androidMain` と `iosMain` に重複配置する。棄却理由は、`Fanbox.kt` 660 行を含む 10 ファイル超が二重管理になり、Phase 1〜2 で積み上げた挙動の一貫性が壊れやすいこと。

**代替案**: portable core を別 Gradle module（`:fankt:fanbox-core`）へ切り出し、それだけに JS ターゲットを与える。棄却理由は、`internal` 可視性が module を跨げないため、descriptor / endpoint builder / parser / entity といった現在 `internal` の型を軒並み `public` にせざるを得ず、公開 API が実装詳細で膨れること。これは #35 と #38 で public API を絞ってきた方向と逆行する。#39 の受け入れ条件は `:fankt:fanbox:jsTest` であり artifact 分割は要求されていない。ただし将来 Zipline guest の bundle サイズが問題になった場合の選択肢としては残る。

### D2: `Dispatchers.IO` — repository を `clientMain` へ移す

`Dispatchers.IO` は JS に存在しない。repository 4 ファイルはこれを既定引数に持つが、いずれも executor を呼び出す実行側のコードであり portable core の一部ではない。よって `clientMain` へ移す。

**代替案**: `expect val ioDispatcher: CoroutineDispatcher` を導入し、JS では `Dispatchers.Default` を actual にする。棄却理由は、repository を JS に残す動機がないこと（guest は HTTP を実行しないので repository も呼ばない）、expect/actual を増やすと source set 構成の変更コストが上がること。

### D3: HTML メタデータ抽出 — Ksoup 呼び出しを `clientMain` へ移し、core には parser interface を残す

`FanboxMetadataParser` は `datasource/parser/` にあり portable core の一部として扱われているが、Ksoup に依存する。調査の結果 **ksoup 0.2.5 は js ターゲットを publish しており**、技術的には JS でも動作しうる。ただし本 change では core から外す。

理由は 2 つある。第一に、Ksoup は HTML パーサ一式であり Zipline guest の bundle サイズに対して重い。第二に、#39 の記述どおり host 側 bridge の責務として整理する方が、#104 の「guest は request 組み立てと response 解釈に徹する」設計と整合する。

ただし単純な移動は成立しない。反証で次が判明した。

`FanboxResponses.homepage()`（`response/FanboxResponses.kt:319`）が `FanboxMetadataParser(formatter).parse(body, statusCode, "homepage")` を直接構築して呼んでおり、しかも他の endpoint parser と同じ `withMappers` の形で並んでいる。`FanboxResponses` は endpoint 別 parser の集約点として commonMain に残す対象なので、parser を丸ごと `clientMain` へ移すと `FanboxResponses` が二分される。

したがって「移動」ではなく「抽出処理の注入」とする。

- `commonMain`: HTML 文字列から metadata JSON 文字列を取り出す関数型 `FanboxMetadataExtractor`、抽出済み JSON を `FanboxMetaDataEntity` へデコードする純関数、および抽出失敗時とデコード失敗時の schema mismatch 例外。`FanboxResponses.homepage()` は extractor を引数で受け取り、既定値を持たない。
- `clientMain`: Ksoup で `meta[name=metadata]` の `content` を取り出す `FanboxMetadataExtractor` の実装。`FanboxResponses.homepage()` の呼び出し元（homepage を叩く repository）がこれを渡す。

既存の 3 つの挙動（正常系のマッピング、meta 要素なし時の `SchemaMismatch`、不正 JSON 時の csrfToken 秘匿）はいずれも維持する。前者 2 つのうち「meta 要素なし」は extractor が null を返した場合として commonMain 側で例外化するため、**JS でもテストできる**。Ksoup に残るのは「HTML から該当要素を見つける」ことだけになる。

テストは次のように分かれる。`FanboxMetadataParserGoldenTest` の 3 テストのうち、デコードと例外の検証は抽出済み JSON を直接渡す形へ書き換えて `commonTest` に残す。Ksoup が実際に HTML から content を取り出せることの検証は `clientTest` へ移す。`FanboxResponsesTest:48` の homepage テストは extractor を差し替えて `commonTest` に残す。

正規表現ベースの軽量抽出への置き換えは、#104 で bundle サイズが問題になった時点で `FanboxMetadataExtractor` の別実装として差し込めばよく、本 change では扱わない。

### D4: `FanboxExceptionFactory` — Ktor 依存メソッドのみを分離する

このオブジェクトは 5 メソッドのうち `fromDownloadHttpResponse` だけが `HttpResponse` を受け取る。残りは `FanboxFailureInterpreter` と `FanboxDiagnostics` への委譲であり portable である。

`fromDownloadHttpResponse` を `clientMain` の download 実装側へ移し、`FanboxExceptionFactory` 本体は `commonMain` に残す。委譲先の `FanboxFailureInterpreter.httpFailure` は既に Ktor 非依存なので、移動先では status code と `Retry-After` ヘッダ文字列を渡すだけになる。

反証で呼び出し元を確認した。`fromDownloadHttpResponse` の呼び出しは `ClientBuilder.kt:118` の 1 箇所のみで、これは `clientMain` へ移る側にある。他の 4 メソッドの呼び出し元は `Fanbox.kt`（移動側）と `commonTest` のみで、`commonMain` 残留コードからの参照はない。したがって分割は成立する。

### D5: JVM 専用依存の除去 — `bundles.infra.api` を source set 単位で解体する

`bundles.infra.api` は `kotlin-stdlib-jdk8`、`kotlin-reflect`、`kotlinx-serialization-json`、`kotlinx-collections-immutable`、`napier` を含む。このうち JS で使えないのは前 2 者、portable core が不要とするのは `napier` である。

`commonMain` では bundle を使わず、必要な依存（`kotlinx-serialization-json`、`kotlinx-collections-immutable`）を個別に宣言する。`napier` は `clientMain` へ移す。`kotlin-stdlib-jdk8` と `kotlin-reflect` はコード上の使用箇所が存在しないため、`:fankt:fanbox` からは単に外す。

bundle 自体は他 module（`:composeApp` など）が使うため定義は残す。

### D6: JS ターゲット構成 — `js(IR)` を library として宣言し、テスト実行環境のみ設定する

#39 の記述に従い `browser()` / `nodejs()` の binary 生成は宣言しない。ただし Kotlin Gradle Plugin は環境を 1 つも宣言しない場合テストタスクを生成しないため、`nodejs()` をテスト実行のためだけに宣言し、`binaries.library()` で library 出力とする。

Zipline は guest を `jsMain` に置き `@JsExport` で公開する構成を取るが、その宣言は #104 側で `clientMain` 相当の位置づけとして追加されるため、本 change では `jsMain` にコードを置かない。

primitive plugin として `matsumo.primitive.kmp.js` を追加し、既存の `KmpIosPlugin` / `KmpAndroidPlugin` と同じ粒度で扱う。

### D7: テストの配置 — MockEngine 依存を `clientTest` へ、フィクスチャテストを `commonTest` に残す

`commonTest` の 31 ファイルのうち Ktor import を持つのは 14。このうち `response/FanboxUrlPartsTest.kt` は本体が Ktor 非依存（`FanboxUrlParts` は独自実装）で、テストが期待値の比較のために `io.ktor.http.Url` を使っているだけである。これは Ktor を使わない期待値へ書き換えて `commonTest` に残す。

残り 13 ファイルは MockEngine で HTTP をモックする統合テストであり `clientTest` へ移す。`fixture/**` の 7 ファイルは全て Ktor 非依存なので `commonTest` に残り、両側から参照される。

結果として `jsTest` で実行されるのは mapper golden test 3、parser golden test 1、serialization test 3、endpoint test 1、response test 2 の計 10 ファイルとなる。

`androidUnitTest` の 3 ファイル（`FanboxDependenciesTest` / `FanboxRouteDriftTest` / `KtorBoundaryTypeAliasFixture`）は Android 固有の検査なので現状維持とする。

### D8: 検証タスクの JS 対応

`fankt/fanbox/build.gradle.kts` には JS ターゲット追加の影響を受ける箇所が 4 つある。

1. **ABI validation**（`:785`）: `abiValidation` は klib ABI dump に JS ターゲットを追加する。`api/fanbox.klib.api` の `// Targets:` 行が変化するため dump を更新する。JS の public API は Android / iOS の共通部分集合になるので、宣言そのものは減らない想定。
2. **publication task の列挙**（`:830-841`）: `requiredPublicationMetadataTasks` に `generateMetadataFileForJsPublication` を加える。JS は macOS 限定条件の外側（Linux でも publish 可能）に置く。
3. **`verifyPersistenceBoundary`**（`:747-777`）: `persistenceBoundaryConfigurations` が `androidReleaseCompileClasspath` / `commonMainResolvableDependenciesMetadata` / `ios.*CompileKlibraries` を名指ししている。`jsCompileClasspath` を検査対象に加える。
4. **`verifyPortableImportBoundary`**（`:921-944`）: 現在 `src/*Main/kotlin/.../{endpoint,response,datasource/mapper,datasource/parser}/**` を走査対象にしている。source set 分離後は `commonMain` のみが portable core になるため、走査対象を `commonMain` に限定し、禁止 import に JVM 専用パッケージを追加する。走査対象が 0 件になると `check` で失敗する安全弁があるので、パス変更時はこれが効く。

`verifyKtorBoundary` は Android の compiled class と ABI dump を検査するもので、JS ターゲットの有無に影響されない。

### D9: JS artifact を Maven Central へ publish する

fankt の利用者は PixiView（同一開発者）のみであり、外部利用者への後方互換の約束は考慮しない。

既存の publication 設定は `KotlinMultiplatform` を対象にしているため、`js()` ターゲットを宣言すれば JS artifact は自動的に publish 対象へ入る。明示的に publish を止める方が手数が多く、artifact が存在して困ることもないため publish する。追加作業は `requiredPublicationMetadataTasks` への JS task 追加のみである。

`deploy-library.yml` は macOS runner で全ターゲットを publish しており、JS も同じ job で扱える。

**publish された JS artifact だけでは guest を構成できない点に注意する。** `FanboxEndpoints`、`FanboxResponses`、`RequestDescriptor` はいずれも `internal` であり、別 Gradle module からは参照できない。JS artifact の public API は domain model、ID と cursor の型、例外階層、認証ストレージ contract（`FanboxCookieStorage` / `FanboxTokenStore` とその in-memory 実装）で構成され、**request を組み立てる操作も response を解釈する操作も含まない**。

これは #104 の Zipline host を fankt の `clientMain` に置く構成（後述の D10）を前提とすれば問題にならない。guest と host が同一 module にあるため `internal` のまま相互参照できる。逆に host を PixiView 側の別 module に置く構成を選ぶ場合は、guest 向けの public facade と、検証済み HTTP 実行の public API の両方を新設する必要があり、本 change の範囲を超える。

### D10: Zipline の host / guest はいずれも fankt 内に置く

#104 の OTA 配信では、guest（QuickJS 上の Kotlin/JS）と host（HTTP 実行）の境界をどこに引くかで fankt に必要な public API が変わる。本 change は **両方を fankt 内に置く構成**を前提とする。

- guest: `jsMain`（本 change で追加した JS ターゲット上）
- host: `clientMain`（既存の Ktor 実行系と同じ source set）
- PixiView から見える API: 従来どおり `Fanbox` クラスのみ

この構成を取る理由は、SSRF 対策が `internal` に閉じていることにある。`TrustedFanboxEndpointPolicy`（endpoint ID から許可 origin と method を引く内部テーブル）と `FanboxDescriptorValidator`（HTTPS 強制、origin allowlist、path traversal 排除、リダイレクト先の再検証）は、descriptor を実際の HTTP に変換する唯一の安全な経路である。host を fankt の外に置くと、これらを public にするか、host 側で同等の検証を再実装するかのいずれかになる。前者は #35・#38 で絞った public API を開き直し、後者はセキュリティ境界が二重管理になる。

同一 module に置けば、guest が返した descriptor を host が `internal` の validator を通して実行できる。credential の付与も既存どおり host 側に閉じるため、#104 の「guest は credential に触れない」要件も満たす。

代償として fankt に Zipline 依存が入る。ただし Zipline plugin の適用と guest bundle の生成は #104 で行う作業であり、本 change では JS ターゲットを用意するところまでにとどめる。

### D11: PR 分割

3 stage に分ける。それぞれ独立してマージ可能で、前段が後段の前提になる。

**PR1 — 依存整理と source set 再編（ターゲット追加なし）**

D1〜D5 を実施する。`js()` は宣言しない。この時点で targets は Android + iOS のままなので、**既存の CI がそのまま回帰テストとして機能する**。source set 移動でビルドが壊れれば Android / iOS のビルドで検出される。ABI dump は変化しない想定（public API の宣言位置が変わるだけで内容は同一）だが、変化した場合はこの PR で確認できる。

分量が最大の PR になるが、ファイル移動が主体でロジック変更は D3（parser 分割）と D4（例外 factory 分割）の 2 箇所に限られる。

**PR2 — JS ターゲット追加と `jsTest` グリーン化**

D6 と D7 を実施する。`js(IR)` 追加、primitive plugin 追加、テストの source set 移動、`FanboxUrlPartsTest` の Ktor 除去。ABI dump の targets 行更新。

PR1 が済んでいれば、この PR でコンパイルエラーが出る箇所は「PR1 で見落とした非 portable 依存」だけになる。仮に想定外の依存が出て難航しても、PR1 の成果は残る。

**PR3 — CI と publication 対応**

D8 の 2〜4 と、`.github/workflows/` の更新。JS のビルド・テストを PR CI に追加し、`deploy-library.yml` に JS artifact の publish を通す。リリースフローに触れる変更をここに隔離することで、万一の publish 失敗が PR1・PR2 のマージを妨げない。

## Risks / Trade-offs

**`clientMain` の導入が KMP hierarchy template と衝突する可能性**: `applyDefaultHierarchyTemplate()` を使いつつカスタム intermediate source set を足す構成は、KGP のバージョンによって警告や依存解決の差異を生むことがある。PR1 で Android / iOS 両方のビルドとテストが通ることを確認して判定する。

**ABI dump の JS ターゲット追加で差分が想定より大きくなる可能性**: klib ABI dump は targets 単位で宣言をグルーピングするため、JS が加わると共通部分と JS 固有部分の表現が再編される場合がある。PR2 で dump を再生成し、Android / iOS 向けの宣言が減っていないことを確認する。

**Ksoup を core から外す判断が #104 で覆る可能性**: bundle サイズが実際には問題にならず、guest 内で HTML パースをしたくなる場合がある。その場合は `clientMain` の実装を `commonMain` へ戻し ksoup の js artifact に依存させればよく、D3 の分割（contract と JSON デコードを core に残す）はその際も無駄にならない。

**Kotlin 2.2.10 のまま進めることによる後戻り**: Zipline 1.27.0 は Kotlin 2.3.0 / serialization 1.10.0 / coroutines 1.10.2 を要求する。現在の fankt は Kotlin 2.2.10 / serialization 1.9.0 / **coroutines 1.7.3** であり、#104 の prototype 着手時にはいずれも引き上げが要る。特に coroutines 1.7.3 は要求より 3 マイナー古い。本 change で `js()` が通ることと、Zipline が要求するバージョンで通ることは別問題であり、バージョン引き上げ時に JS ビルドが再度壊れる可能性がある。ただしその作業は本 change の有無にかかわらず必要であり、先に JS ターゲットがあった方が壊れた箇所を検出できる。

**`Dispatchers.IO` を持つ repository を JS から外すことの影響**: JS 側には repository がないため、#104 の guest は endpoint builder と response parser を直接呼ぶことになる。guest の API 表面が repository より低レベルになるが、#104 の設計（guest は request 記述子を返し host が実行する）とはむしろ整合する。

## Open Questions

- `clientMain` という名前でよいか。`httpMain` / `runtimeMain` なども候補。実装時に KGP の予約名と衝突しないことを確認する。
