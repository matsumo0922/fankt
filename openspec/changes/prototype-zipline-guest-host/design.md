## Context

fankt は FANBOX の非公式 API を扱うため、API のスキーマ変更が起きるたびに修正・publish・ストア審査が必要になる。#90 は Zipline による OTA 配信の prototype を作り、#12 相当の修正（`post.info` の `body` → `body.post`）が bundle 差し替えだけで反映されることを実証する。

現状は #39 と #89 の成果の上に立つ。

- `:fankt:fanbox` は `commonMain` / `clientMain` に分割済み。`FanboxEndpoints`（descriptor 組み立て）と `FanboxResponses`（response 解釈）は `commonMain` にあり、Ktor / Napier / Ksoup を含まない
- `FanboxRequestExecutor`（`suspend fun execute(descriptor: RequestDescriptor): FanboxRawResponse`）が descriptor と raw response で境界を切っている
- SSRF 対策（`TrustedFanboxEndpointPolicy` と `FanboxDescriptorValidator`）は `internal` で、`clientMain` の Ktor executor だけが通す
- js target は `js(IR) { nodejs(); binaries.library() }`（`build-logic/src/main/java/primitive/kmp/KmpJsPlugin.kt`）で、その klib を Maven Central へ publish している
- Kotlin 2.3.21 / kotlinx.serialization 1.10.0 に揃え、Zipline 1.27.0 の Gradle plugin を適用した実ビルドが disposable copy で通ることを確認済み

### Zipline 1.27.0 について確認した事実

tag 1.27.0 のソースと Maven Central で確認した。設計の結論を左右するものだけ挙げる。

**bridge の基本**

- `ZiplineService` は suspend 関数と `Flow` を bridge できる。引数と戻り値は既定で kotlinx.serialization による pass-by-value、`ZiplineService` 型だけは pass-by-reference
- **`Zipline.take` / `Zipline.bind` の public overload は、compiler plugin が adapter 付きの internal overload へ書き換える前提の stub である。** plugin が無い compilation では実行時に `error("unexpected call to Zipline.take: is the Zipline plugin configured?")` になる（`zipline/src/hostMain/kotlin/app/cash/zipline/Zipline.kt`）。したがって **host 側の compilation にも compiler plugin が必要**
- Gradle plugin の `isApplicable` は無条件に `true` を返すため、plugin を適用した project の全 compilation に compiler plugin が入る

**compiler options の強制範囲**

`ZiplinePlugin.kt` は `kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).all { ... }` の中で target の `compilerOptions` に `es2015` と `MODULE_UMD` を設定する。これは target 単位の設定で、その target の全 compilation・全 binary へ convention として伝播する。binary 単位で分離する仕組みはなく、js target を別名で 2 つ宣言しても両方に掛かる。

**binary の併存**

同一 js target に `binaries.library()` と `binaries.executable()` を併存させると、serve task 名 `serve${Target}${Mode}${Tool}Zipline` が binary 種別を含まないまま binary ごとに `tasks.register` されるため、同名 task の二重登録で configuration が失敗する。出力先 `build/zipline/PRODUCTION` も同様に衝突する。

**API tracking の対象**

`ziplineApiCheck` / `ziplineApiDump` の子 task は `target.tasks.withType(KotlinCompile::class.java)` に対してのみ登録される。この `KotlinCompile` は `org.jetbrains.kotlin.gradle.tasks.KotlinCompile`、すなわち **JVM 向けの compile task 型**である。Kotlin/JS の compile task は `Kotlin2JsCompile` でこの型のサブタイプではないため、**JS compilation だけを持つ構成では umbrella task に子が 1 つも付かず、検証が無内容に成功する**。

**署名検証の失敗の伝わり方が経路で非対称である**

`manifestVerifier.verify` は 2 箇所から呼ばれ、いずれも try ブロックの外にある。

- network 経路（`fetchManifestFromNetwork`）は `loadFromNetwork` の try 内で呼ばれるため、検証失敗は `LoadResult.Failure` として flow に流れる
- **local 経路（`loadCachedOrEmbeddedManifest`）は `loadFromLocal` の try に入る前に呼ばれるため、検証失敗は flow の例外として collector まで素通しになる。** `loadOnce` は `load().first()` なので、この場合は例外を投げる

**その他**

- `ManifestVerifier` は Ed25519 と ECDSA P-256 に対応する。検証失敗は `IllegalStateException` で、Zipline 自身は fallback しない
- guest が投げた例外は bridge を越えると `ZiplineException` になる。kdoc が「the wrapped exception type is not generally available」と明記するとおり、**元の例外型は host 側で復元できない**
- `Zipline.create()` 自体は dispatcher の単一スレッド性を強制しないが、`ZiplineLoader` の kdoc が「各インスタンスは thread-confined であり、dispatcher がその強制を実装しなければならない」と述べる
- `zipline` と `zipline-loader` はいずれも iosArm64 / iosSimulatorArm64 / iosX64 の artifact を publish している

## Goals / Non-Goals

**Goals:**

- guest が `RequestDescriptor` と `FanboxPostDetail` を供給し、host が検証済み HTTP 実行と credential 付与を担う分割を作る
- `post.info` の response 解釈を bundle 差し替えだけで変更できることを、テストで実証する
- 署名検証に失敗した bundle が実行されず、fallback bundle へ落ちることを保証する
- guest bridge API に credential を渡す経路が型として存在しないことを保証する
- 公開 API（`Fanbox` クラス）を壊さない

**Non-Goals:**

- 配信基盤（ホスティング、署名鍵の運用管理、kill switch、同梱 fallback bundle の更新運用）
- bridge API のバージョニング運用設計
- `post.info` 以外の 28 operation の guest 化
- PixiView 側の対応（PixiView-KMP#104）
- guest の起動レイテンシやメモリ使用量の最適化。prototype は実測値を記録するに留める

## Decisions

### D1: Zipline plugin を `:fankt:fanbox` に適用する（ユーザー確認済み）

host 側の compilation にも compiler plugin が必要である以上、plugin を適用しない構成は成立しない。plugin を `:fankt:fanbox` に適用し、guest と host を同一 module に置く。#39 の設計判断 D10（host / guest をいずれも fankt 内に置く）をそのまま実現する構成であり、`FanboxDescriptorValidator` と `TrustedFanboxEndpointPolicy` は `internal` のまま保たれる。

代償は、publish 用の js klib の compile 条件が `es2015` / `MODULE_UMD` へ変わることにある。**V1 の実測結果（PR1 で確認済み）**: plugin を適用した状態で `:fankt:fanbox:generateMetadataFileForJsPublication` が成功し、`compileKotlinJs` も通る。compile 条件の変更で publish 用 klib は壊れない。

**代替案として検討し、却下したもの**: host bridge を別 module へ切り出し、plugin を guest module と bridge module だけに適用する。`:fankt:fanbox` の js target の compile 条件は変わらないが、module が 2 つ増え、`FanboxGuestService` の interface 定義を両側から見えるようにする配置も要る。fankt の consumer が事実上ユーザー本人に限られる現状では、compile 条件変更のリスクより構成の複雑さのほうが大きい。

### D2: guest bundle 用に 2 つ目の js target を宣言する

guest bundle は `mainFunction` で指定した entry point を必要とし、Zipline の公式サンプルとテストプロジェクトはいずれも `binaries.executable()` を使う。既存の js target は publish 用に `binaries.library()` を宣言しており、同一 target への併存は configuration time に失敗する。

そこで `js("guest") { nodejs(); binaries.executable() }` を追加で宣言する。Zipline の公式テストプロジェクト `multipleJsTargets` が複数 js target の構成を実証しており、`ZiplineCompileTask` も target 名が `js` 以外のとき task 名と出力先に target 名を混ぜる設計になっている。

ただしその公式テストプロジェクトは、2 つの js target の双方に variant を区別する Gradle attribute を設定している。同一 platform type の target が 2 つあると consumer 側の variant 解決が曖昧になるためで、**fankt でも同様の attribute が必要になる見込みが高い**。

guest target の source set は `commonMain` に依存するだけで、`clientMain` には依存しない。したがって guest bundle には parsing 層とその依存（kotlinx.serialization、domain model）だけが入り、Ktor / Napier / Ksoup は入らない。これは #39 で `clientMain` を分離した結果の直接的な帰結である。

publish 対象は既存の `js` target のみとし、`guest` target を publication から除外する。

**V2 として PR1 で確認する**: 2 つ目の js target 追加が既存の `generateMetadataFileForJsPublication`、`generateMetadataFileForKotlinMultiplatformPublication`、klib ABI dump（`fankt/fanbox/api/fanbox.klib.api` の `// Targets:` 行）に与える影響。あわせて guest target を publication から除外する具体的な手段を確定する。KMP に target を publication から外す first-class な API は無いため、該当 publication task の無効化になる。

V2 で問題が出た場合の戻り先は、distinguishing attribute の追加と ABI dump の更新である。いずれも guest target の宣言方法の調整で閉じ、D2 の構成そのものは変えない。

### D3: `ziplineApiCheck` が実効を持つよう JVM compilation を確保する

`ziplineApiCheck` の子 task は JVM 向け `KotlinCompile` にしか登録されない。`:fankt:fanbox` の Android target の compile task がこの型のサブタイプかどうかで、検証が実効を持つかが決まる。

**V3 の実測結果（PR1 で確認済み）**: `./gradlew :fankt:fanbox:ziplineApiCheck --dry-run` は `compileDebugKotlinAndroidZiplineApiCheck` と `compileReleaseKotlinAndroidZiplineApiCheck` を子 task として列挙する。Android target の compile task が JVM 向け `KotlinCompile` のサブタイプであるため、検証は空回りしない。D1 で plugin を `:fankt:fanbox` へ適用した構成の副次的な帰結であり、当初案（js のみの guest module へ適用）であれば子 task はゼロだった。

子 task がゼロだった場合、`apiTracking` を有効にしたまま放置すると「検証が通っている」という誤った信号になる。その場合の対処を PR1 で決める。選択肢は (a) guest service interface を含む JVM target を追加する、(b) `ziplineApiCheck` に依存しない形で bridge API の固定を行い、その旨を spec の scenario に反映する、のいずれか。**どちらを採るにせよ、無内容に成功する検証を CI に置いたままにはしない。**

### D4: guest の bridge API は 2 メソッドの単一 service に絞り、失敗を戻り値で運ぶ

prototype の範囲を `post.info` に限る（ユーザー確認済み）ため、bridge API は次の 1 interface に収まる。

```kotlin
interface FanboxGuestService : ZiplineService {
    fun buildPostDetailRequest(postId: String): RequestDescriptor
    fun parsePostDetail(body: String, statusCode: Int): GuestParseResult
}
```

`GuestParseResult` は成功時に `FanboxPostDetail` を、失敗時に failure の分類（schema mismatch か、guest 内部の予期しない失敗か）と診断情報を運ぶ `@Serializable` な sealed 型とする。

**配置**: `FanboxGuestService` interface と `GuestParseResult` は `commonMain` に置き、guest 側の実装だけを `guestMain` に置く。host（`clientMain`）と guest の双方が同じ宣言を見る必要があるためである。この結果、`app.cash.zipline` の core 依存は `commonMain` に入り、published な全 target（Android / iOS / js klib）の依存グラフに現れる。`zipline-loader` は host 側だけが使うため `clientMain` に留める。

interface を `commonMain` に置くことは D3 にも効く。`ziplineApiCheck` の子 task は JVM 向け compile task にしか付かないため、interface が Android compilation の対象に入らないと検証されない。

**この形を採る理由は、guest が投げた例外が bridge を越えると `ZiplineException` に型消去されることにある。** 例外に頼ると host 側で 2 つの失敗が区別できなくなる。

- FANBOX のレスポンスがスキーマと合わない（= guest の解釈を更新すべき、`FanboxException.SchemaMismatch` として呼び出し元に伝えるべき）
- guest の実行そのものが壊れている（= OTA 経路の故障、fallback すべき）

前者を後者として扱うと、スキーマ不一致のたびに direct path へ無言退避し、OTA 経路が実質死んだまま気付かれない。後者を前者として扱うと、engine の故障が `SchemaMismatch` として呼び出し元に見え、原因を誤らせる。戻り値で分類を運べば host が正しく振り分けられる。

host は `GuestParseResult` の schema mismatch を既存の `FanboxException.SchemaMismatch` へ変換し、それ以外の失敗と `ZiplineException` を D6 の fallback 対象として扱う。

`FanboxDiagnosticSink` は引数に取らない。`FanboxResponses.postDetail`（`FanboxResponses.kt:142`）は tolerant 系ではなく sink を使わないため、issue の未決事項 1（sink の bridge）は本 change では発生しない。全 29 operation を guest 化する後続 change で判断する。

**この API に credential 型は現れない。** 引数は `postId: String` / `body: String` / `statusCode: Int` のみで、cookie storage も CSRF token provider も渡らない。credential の付与は host 側の Ktor client が Cookie plugin と CSRF header で行う既存経路のままである。

### D5: guest は domain model を構築して返す（ユーザー確認済み）

guest が `FanboxPostDetail` を構築して bridge を越えさせる。response 解釈だけでなく mapper 側の変更も OTA で配信できる。#12 の実例では変更点が `FanboxPostDetailEntity` の envelope 構造にあり、entity から domain model への mapping も guest 側に置くことで、どちらの層の変更でも bundle 配信で対応できる。

`FanboxPostDetail` は既に `@Serializable` で、`kotlin.time.Instant` と sealed interface `Body` を含む。sealed の polymorphic serialization は closed polymorphism として動作するため `SerializersModule` の登録は不要だが、bridge 越えで sealed 階層が正しく round-trip することは PR2 で確認する（V4）。

**代償**: 全レスポンスに serialize / deserialize が乗る。prototype ではレイテンシを実測して記録し、判断材料を後続 change へ渡す。

### D6: fallback は 3 段とし、local 経路の例外も捕捉する

loader ラッパーが次の順序で解決する。

1. ローカル manifest URL から `loadOnce` を試みる
2. 失敗したら同梱 fallback bundle を `withEmbedded` 経由でロードする
3. それも失敗したら、guest を経由しない既存の直接呼び出しで `post.info` を処理する

**失敗の捕捉は `LoadResult.Failure` だけでは足りない。** local（embedded / cache）経路の署名検証失敗は `loadFromLocal` の try に入る前に起きるため、flow の例外として素通しになり `loadOnce` が throw する。ラッパーは `LoadResult.Failure` と throw の両方を同じ失敗として扱う。これを取り違えると、同梱 bundle の署名が検証できない状況（鍵ローテーション後の古い同梱 bundle など）で `getPostDetail` がそのまま例外を投げ、「Zipline の導入が既存の可用性を下げない」という前提が崩れる。

3 段目を置く理由は、prototype が既存の動作を退行させないことにある。`post.info` は現在すでに動いており、Zipline の導入がその可用性を下げてはならない。

**署名検証を無効化する設定（`ManifestVerifier.NO_SIGNATURE_CHECKS`）は production 経路で使わない。** テストが意図的に使う場合も、production 経路の構成に混入しないことをテストで固定する。

### D7: 初期化は遅延させ、失敗後は sticky に direct path へ固定する

`Zipline.create()` は QuickJS engine を起動し max stack size を 6 MiB へ引き上げる。これを `Fanbox` のコンストラクタで同期実行すると生成が重くなる。guest を必要とする最初の操作で初期化し、以降は同じインスタンスを再利用する。初期化は guest 専用の単一スレッド dispatcher 上で行い、`Fanbox.close()` が `Zipline.close()` を呼ぶ。

**初期化に失敗した場合、その `Fanbox` インスタンスは以降 direct path に固定する（sticky）。** 呼び出しごとに再試行すると、配信先へ到達できない状況で全ての `getPostDetail` に network timeout が乗る。sticky にすると一過性のネットワーク断でインスタンス生存中は OTA 経路が無効のままになるが、prototype では可用性とレイテンシを優先する。この選択は residual risk として記録し、再試行のポリシー設計は配信基盤の issue へ送る。

**初期化に成功した後の呼び出しで guest が故障した場合も同じ扱いとする。** `ZiplineException` や `GuestParseResult` の実行故障（schema mismatch ではないほう）を観測したら、その呼び出しを direct path で処理し、以降そのインスタンスを direct path へ固定する。engine が実行中に壊れた場合、都度再試行すると壊れた engine を呼び出しのたびに叩き続けることになる。

**fallback へ落ちた事実は診断経路へ報告する。** 無言で退避すると、OTA 経路が壊れていることに誰も気付かないまま既存経路で動き続ける。

### D8: 署名検証のテストは鍵をテスト内で生成する（ユーザー確認済み）

秘密鍵も署名済み bundle もリポジトリへ commit しない。テストが鍵ペアを生成し、正しい署名の manifest と改竄した署名の manifest の両方を組み立てて、前者はロードされ後者は fallback へ落ちることを確認する。CI でそのまま回る。

**production 経路の trusted 公開鍵と manifest URL の正本は本 change で定義しない。** 配信基盤が未整備の段階では定めようがなく、prototype で仮の値を埋め込むと配信基盤の設計時にそれが既成事実になる。したがって **guest 経路が有効になるのは、呼び出し側が manifest URL と trusted 公開鍵を明示的に与えたときに限る。** いずれも与えられない構成では D6 の 3 段目（direct path）で動作する。これは既存の動作そのものであり、退行しない。

fankt の consumer が事実上ユーザー本人に限られること（ユーザー確認済み）を踏まえ、feature flag のような追加機構は設けない。「設定が無ければ direct path」という既定の帰結が、そのまま隔離として働く。

### D9: prototype の検証は host 側テストで行う

受け入れ条件の「アプリの再ビルドなしに bundle の差し替えだけで反映される」は、次の形でテストする。

1. `post.info` の envelope を `body` として解釈する guest bundle A を作る
2. `body.post` として解釈する guest bundle B を作る
3. 同一の host コードが、manifest の差し替えだけで A → B の挙動変化を示す

これは #12 の修正を bundle 配信で反映する操作そのものである。実機やサンプルアプリでの手動確認は行わない。

## 実装単位

3 つに分ける。それぞれ独立してレビュー可能で、前段が後段の前提になる。

**PR1 — plugin 適用、guest target、guest service の定義**

Zipline plugin を `:fankt:fanbox` に適用し、`js("guest")` target と `FanboxGuestService` を追加する。guest bundle が生成されることを確認する。

この PR で V1〜V3 を実測し、結果を design へ追記する。

- **V1**: plugin 適用後、既存の js klib publication（`generateMetadataFileForJsPublication`）が従来どおり生成されるか。`es2015` / `MODULE_UMD` への変更で壊れないか
- **V2**: 2 つ目の js target 追加が klib ABI dump の `// Targets:` 行と `checkKotlinAbi` に与える影響
- **V3**: `ziplineApiCheck` に子 task が付くか。付かない場合、D3 の (a)(b) いずれを採るかを決める

V1 が失敗した場合、D1 を「host bridge を別 module へ切り出す」へ戻す判断になる。その場合も guest service の定義は再利用できる。

**PR2 — host 側の loader と guest 経由の `post.info` 経路**

`zipline-loader` 依存と loader ラッパーを `clientMain` へ追加し、`FanboxPostRepository.getPostDetail` を guest 経由へ切り替える。D6 の 3 段 fallback（`LoadResult.Failure` と throw の両方を捕捉）と D7 の遅延初期化・sticky fallback を実装する。

V4（sealed 階層の bridge 越え round-trip）をここで確認する。round-trip が成立しない場合の戻り先は D5 の代替（guest が正規化済み JSON を返し、domain model の構築を host に残す）である。その場合 mapper の変更は OTA で配信できなくなり、bridge API の形も変わる。

**PR2 の規模が 1 本のレビューに収まらない場合、loader ラッパーとその fallback 挙動を先に入れ、`getPostDetail` の切り替えを後続へ分ける。** 前者だけでも単体でテストでき、後者は差分が小さくなる。

**PR3 — 署名検証テストと CI**

D8 のテストを追加し、guest bundle ビルドと（V3 の結果次第で）`ziplineApiCheck` を PR 検証ワークフローへ組み込む。D9 の bundle 差し替えテストをここに置く。

## Risks / Trade-offs

**plugin 適用で publish 用 js klib の compile 条件が変わる** → V1 で実測する。壊れる場合は D1 の代替案（host bridge の module 分離）へ戻す。PR1 の時点で判定できるため、後段の作業は無駄にならない。

**`ziplineApiCheck` が無内容に成功する** → V3 で子 task の有無を確認する。ゼロなら D3 のとおり、検証が実効を持つ形にするか、bridge API の固定手段を変えて spec を合わせる。**検証が空回りしていることに気付かないまま CI をグリーンにしない。**

**`zipline-loader` の追加が既存の boundary gate に反応する** → `verifyKtorBoundary` と `verifyPersistenceBoundary`（`fankt/fanbox/build.gradle.kts`）は published artifact の依存を検査する。Zipline とその推移依存（QuickJS のネイティブライブラリを含む）が追加されると、これらの gate の期待値を更新する必要がある。PR2 で扱う。

**published artifact の依存と runtime footprint が変わる** → Android では ABI ごとのネイティブライブラリが増える。proposal の「既存の publish を変更しない」は公開 API と publication の構成についての記述であり、依存グラフは変わる。

**初回呼び出しの hot path に manifest fetch が乗る** → D7 の遅延初期化により、最初の `getPostDetail` で manifest の取得が同期的に発生する。配信先へ到達できない場合は timeout まで待つ。`ZiplineCache` を使わない構成のため、プロセス起動ごとに繰り返される。prototype では実測して記録し、キャッシュ方針は配信基盤の issue へ送る。

**sticky fallback により一過性の障害で OTA 経路が無効化される** → D7 のとおり意図的な選択。residual risk として記録する。

**bundle サイズと起動レイテンシが実用に耐えない可能性** → prototype では実測して記録するに留める。閾値を満たさない場合、D5 を「正規化済み JSON を返し host で model 構築」へ切り替える判断材料になる。この切り替えは bridge API の変更であり、後続 change の範囲。

**QuickJS の thread-confined 制約** → guest 呼び出し専用の単一スレッド dispatcher を `Fanbox` 内に持つ。既存の `ioDispatcher` パラメータの意味は変えない。`Fanbox` が dispatcher をもう 1 つ所有することになり、`close()` の責務が増える。

**署名鍵の運用が prototype に存在しない** → D8 のとおり、guest 経路は manifest URL と trusted 公開鍵が明示的に与えられたときだけ有効になる。与えられなければ既存の direct path で動作する。実運用の鍵管理は配信基盤の issue へ送る。

## Open Questions

- V1〜V4 の結果は PR1・PR2 で確定し、この design へ追記する
- guest target に `nodejs()` のみで足りるか。Zipline の公式テストプロジェクトは全て `browser()` を宣言しており、`nodejs()` のみは upstream のテスト対象外である。PR1 で確認し、必要なら guest target に `browser()` を宣言する（guest target は publish しないため、サブターゲットの選択は publish に影響しない）
