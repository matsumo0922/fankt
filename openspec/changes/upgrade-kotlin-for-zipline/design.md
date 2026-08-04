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

version catalog の Kotlin 行には、Zipline が Kotlin 2.4 に対応するまで 2.3.x に留める制約を現在形で記す。

### D2: Kotlin 2.3 互換修正は compiler / test failure が示した箇所に限定する

（agent 仮決め）まず version catalog だけを更新して build を実行し、Kotlin 2.3 の error・warning-as-error・deprecation により失敗した箇所だけを修正する。公開 API の意図的変更や、失敗を伴わない先回りの cleanup は行わない。

### D3: Zipline plugin は disposable validation copy で適用する

（agent 仮決め）production branch に `app.cash.zipline` を残すと後続 issue の plugin 適用を先取りするため、最終 fankt HEAD から作る disposable copy にだけ Zipline 1.27.0 を追加する。

- root plugin declaration に `app.cash.zipline` 1.27.0 を `apply false` で追加する。
- `:fankt:fanbox` に plugin を適用する。
- guest API の設計を要求する `ziplineApiCheck` は `apiTracking=false` とし、compiler plugin の解決・適用と既存 source の compile / build に検証範囲を限定する。
- disposable copy で `build` と `:fankt:fanbox:jsTest` を実行し、変更は commit しない。

Zipline 1.27.0 tag の公式 sample は `id("app.cash.zipline")` を KMP module に適用しており、plugin 実装は `KotlinCompilerPluginSupportPlugin` として全 compilation に compiler plugin artifact を追加する。この経路を検証対象とする。

### D4: consumer 検証は隔離した Maven repository と PixiView worktree を使う

（agent 仮決め）最終 fankt HEAD の publication を一時 Maven repository へ出し、PixiView-KMP の detached worktree だけにその repository を一時追加する。既存の `fankt = "0.1.1"` dependency coordinate は維持し、一時 repository にある新 build の artifact だけを解決させる。PixiView の main checkout、version catalog、tracked filesは変更しない。

consumer build は fankt を直接使う production module を含む Gradle build で行い、Kotlin 2.4.0 compiler が Kotlin 2.3.21 で生成した klib metadata を読めることを確認する。

### D5: 単一 PR で配送する

（agent 仮決め）version bump、必要な互換修正、検証記録は互いに単独では受け入れ条件を満たさない。同じ toolchain compatibility intent の一続きなので、OpenSpec artifacts と実装を単一 PR に載せる。

## Risks / Trade-offs

- [Risk] Zipline plugin の一時適用方法が後続の最終 guest 構成と異なる → plugin ID、version、compiler plugin 適用経路だけを今回の保証として明記し、guest task・API tracking は後続 issue に残す。
- [Risk] Kotlin 2.3.21 で通っても Zipline 1.27.0 が build した 2.3.20 と完全同一ではない → 実際の 1.27.0 compiler plugin を適用した compile / build を証拠にする。
- [Risk] shared Maven cache によって旧 artifact を誤消費する → 一時 repository を空の隔離 directory にし、consumer dependency resolution をその artifact に固定する。
- [Risk] dependency update が生成物や public API dump を変える → full build と既存 ABI / boundary checks を実行し、意図しない差分は修正または blocker とする。

## Migration Plan

1. version catalog と必要最小限の互換修正を同一 PR で merge する。
2. rollback はこの PR を revert し、Kotlin 2.2.10 / kotlinx.serialization 1.9.0 の組み合わせへ戻す。
3. Zipline plugin の恒久適用と guest 化は後続 issue で別途設計・検証する。

## Open Questions

なし。低リスクの検証構成と配送形態は PR の「人間に確認してほしいこと」へ転記する。
