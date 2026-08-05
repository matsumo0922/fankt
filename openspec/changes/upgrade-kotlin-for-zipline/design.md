## Context

fankt は Kotlin 2.2.10、kotlinx.serialization 1.9.0、kotlinx.coroutines 1.10.2 を使い、`:fankt:fanbox` は Android・iOS・JS target を公開している。後続の Zipline guest / host 化は Zipline 1.27.0 を前提とするが、同 release の build は Kotlin 2.3.20、kotlinx.serialization 1.10.0、kotlinx.coroutines 1.10.2 を使用している。Zipline の Gradle plugin `app.cash.zipline` は Kotlin compiler plugin を全 Kotlin compilation へ適用するため、compiler version の実 build 検証が必要である。

PixiView-KMP は Kotlin 2.4.0 で fankt の `fanbox` と `fanbox-persistence-room` artifact を消費している。本 change は fankt を Kotlin 2.3.x に留めたまま、この上位 compiler consumer で local artifact を解決・compile できることまでを契約に含める。

## Goals / Non-Goals

**Goals:**

- Kotlin 2.3.21 と kotlinx.serialization 1.10.0 以上で fankt の全 build / test を成立させる。
- `:fankt:fanbox:jsTest` を含む既存の JS portable-core 契約を維持する。
- Zipline 1.27.0 Gradle/compiler plugin の適用互換性を実 build で確認する。
- Kotlin 2.4.0 の PixiView-KMP が local publish した fankt artifact を消費できることを確認する。

**Non-Goals:**

- Zipline plugin の恒久適用、Zipline runtime dependency の追加、guest bundle の生成。
- Zipline guest / host API、OTA 配信、署名・manifest・rollback の実装。
- Kotlin 2.4 系への更新。
- 受け入れ条件と無関係な dependency update や refactor。

## Decisions

### D1: Kotlin 2.3.21 と kotlinx.serialization 1.10.0 に固定する

（ユーザー確認済み: Issue #88）Kotlin は Zipline main が採用している 2.3.21、kotlinx.serialization は 1.27.0 release と同じ 1.10.0 に固定する。`1.10.0 以上`を満たす最小値を選び、他の KotlinX dependency は更新しない。

OpenSpec と README には、Zipline が Kotlin 2.4 に対応するまで 2.3.x に留める制約を現在形で記す。version catalog の version 定義には説明 comment を追加しない。

### D2: Kotlin 2.3 互換修正は compiler / test failure と stable-time 境界に限定する

（agent 仮決め）まず version catalog だけを更新して build を実行し、Kotlin 2.3 の error・warning-as-error・deprecation により失敗した箇所だけを修正する。加えて Kotlin 2.3 で `kotlin.time.Instant` / `Clock` が stable になったため、それらの使用だけを理由に置かれた `ExperimentalTime` opt-in と Kotlin 2.2 向け説明を削除する。公開 API の意図的変更や、他の先回り cleanup は行わない。

実 build で確認した Kotlin 2.3 toolchain の成立条件として、Android Gradle Plugin は Kotlin 2.3 metadata に必要な R8 8.13.19 を含む 8.13.2、Ktorfit は Kotlin 2.3 compiler plugin に対応する 2.7.3、KSP は同 Ktorfit release が使用する 2.3.6 に揃える。Ktorfit 2.7.5 は project の Kotlin Gradle plugin と stdlib を 2.4.0 へ押し上げて D1 に反し、Ktorfit 2.7.3 と KSP 2.3.10 の組み合わせは削除済みの全 target `ksp` configuration を要求するため採用しない。Gradle 8.13 の publication validation に合わせ、aggregate FANBOX documentation だけを root `docs` に出力し、他 module の Dokka Javadoc output は各 build directory に分離する。この補助 build-tool 調整は Kotlin 2.3.21 固定、全 build、local publication の成功に必要な範囲に限定する。

### D3: Kotlin metadata consumer の最低 version は 2.3.21 とする

（ユーザー確認済み: Issue #88、および archived `kotlin-js-portable-core` design D9）Kotlin/Native・Kotlin/JS の klib metadata は古い compiler による新しい metadata の消費を保証しない。本 change は Kotlin 2.2 consumer compatibility を保証せず、consumer の最低 Kotlin version を 2.3.21 とする。既存 design は fankt の利用者を PixiView（同一開発者）のみとし、外部利用者への後方互換を約束しない。Issue #88 は既知 consumer の PixiView-KMP 2.4.0 を local publication で検証する契約を明示している。

これは dependency-only update に見えても consumer toolchain の breaking boundary なので、proposal と README に明記する。隔離した最小 JS KLib 実験では、Kotlin 2.3.21 producer の public symbol を Kotlin 2.2.10 consumer が解決できず、consumer を 2.3.21 にすると compile が成功した。Kotlin 2.2 consumer fixture は「維持する保証」ではないため追加しない。repository 内の compatibility fixture は producer と同じ catalog の Kotlin 2.3.21 で検証する。

### D4: Zipline plugin は disposable validation copy で適用する

（agent 仮決め）production branch に `app.cash.zipline` を残すと後続 issue の plugin 適用を先取りするため、最終 fankt HEAD から作る disposable copy にだけ Zipline 1.27.0 を追加する。

- root plugin declaration に `app.cash.zipline` 1.27.0 を `apply false` で追加する。
- `:fankt:fanbox` に plugin を適用する。
- guest API の設計を要求する `ziplineApiCheck` は `apiTracking=false` とし、compiler plugin の解決・適用と既存 source の compile / build に検証範囲を限定する。
- disposable copy で `build` と `:fankt:fanbox:jsTest` を実行し、変更は commit しない。

Zipline 1.27.0 tag の公式 sample は `id("app.cash.zipline")` を KMP module に適用しており、plugin 実装は `KotlinCompilerPluginSupportPlugin` として全 compilation に compiler plugin artifact を追加する。この経路を検証対象とする。

### D5: consumer 検証は隔離した Maven repository と PixiView worktreeを使う

（agent 仮決め）最終 fankt HEAD の `fanbox` と `fanbox-persistence-room` publication を一時 Maven repository へ `0.1.1-issue88-local` として出し、検証開始時の PixiView-KMP `origin/main` から作る detached worktree だけにその repository と version を一時設定する。PixiView の main checkout と tracked files は変更しない。

PixiView の repository 設定は `me.matsumo.fankt` group を一時 repository だけへ送る `exclusiveContent` とし、Maven Central や既存 cache への fallback を許さない。`core:repository` が `fanbox-persistence-room` を `api` で使用し、そこから `fanbox` も解決する production path を対象に、`:core:repository:compileAndroidMain` と `:core:repository:compileKotlinIosSimulatorArm64` を実行する。resolved component が両方とも unique version であることを dependency insight で確認し、repository 内 artifact と Gradle が解決した artifact の SHA-256 を照合する。

これにより Android（JVM bytecode）と iOS KLib の両経路で、Kotlin 2.4.0 compiler が Kotlin 2.3.21 で生成した metadata と API を読めることを確認する。standalone JVM target は fankt に存在せず、保証対象に含めない。

### D6: 単一 PR で配送する

（agent 仮決め）version bump、必要な互換修正、検証記録は互いに単独では受け入れ条件を満たさない。同じ toolchain compatibility intent の一続きなので、OpenSpec artifacts と実装を単一 PR に載せる。

## Risks / Trade-offs

- [Risk] Zipline plugin の一時適用方法が後続の最終 guest 構成と異なる → plugin ID、version、compiler plugin 適用経路だけを今回の保証として明記し、guest task・API tracking は後続 issue に残す。
- [Risk] Kotlin 2.3.21 で通っても Zipline 1.27.0 が build した 2.3.20 と完全同一ではない → 実際の 1.27.0 compiler plugin を適用した compile / build を証拠にする。
- [Risk] shared Maven cache によって旧 artifact を誤消費する → unique version、`exclusiveContent`、両 coordinate の resolved component、artifact SHA-256 照合で provenance を固定する。
- [Risk] Kotlin 2.2 consumer が新しい klib metadata を読めない → 最低 consumer Kotlin 2.3.21 を breaking boundary として明記し、既知 consumer PixiView 2.4.0 を実証する。
- [Risk] dependency update が生成物や public API dump を変える → full build と既存 ABI / boundary checks を実行し、意図しない差分は修正または blocker とする。

## Migration Plan

1. version catalog と必要最小限の互換修正を同一 PR で merge する。
2. rollback はこの PR を revert し、Kotlin 2.2.10 / kotlinx.serialization 1.9.0 の組み合わせへ戻す。
3. Zipline plugin の恒久適用と guest 化は後続 issue で別途設計・検証する。

## Open Questions

なし。低リスクの検証構成と配送形態は PR の「人間に確認してほしいこと」へ転記する。
