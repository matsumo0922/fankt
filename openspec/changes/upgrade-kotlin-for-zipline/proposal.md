## Why

Zipline 1.27.0 を使う後続の OTA prototype に進むには、fankt の Kotlin compiler と kotlinx.serialization を Zipline が対応する組み合わせへ揃える必要がある。現行の Kotlin 2.2.10 / kotlinx.serialization 1.9.0 のままでは compiler plugin の互換範囲を満たさない。

## What Changes

- Kotlin を 2.3.21、kotlinx.serialization を 1.10.0 以上へ更新する。
- **BREAKING** Kotlin/Native・Kotlin/JS metadata を消費する compiler の最低 version を Kotlin 2.3.21 とし、Kotlin 2.2 consumer compatibility は保証しない。既知 consumer の PixiView-KMP 2.4.0 は local publication で検証する。
- Kotlin 2.3 系で新たに顕在化する compile error、警告強化、deprecation を受け入れ条件に必要な範囲で解消する。
- Android（JVM bytecode）、iOS、JS を含む fankt の build / test と `:fankt:fanbox:jsTest` を通す。standalone JVM target は存在せず、保証対象に含めない。
- Zipline 1.27.0 の Gradle plugin を一時的に適用した検証 build を行い、plugin 自体の恒久適用と guest 化は後続 issue に残す。
- Kotlin 2.4.0 の PixiView-KMP から local publish した fankt artifact を消費できることを検証する。
- version catalog に、Zipline の対応確認なしに Kotlin 2.4 系へ更新しない制約を現在形で記録する。
- 配送形態は単一 PR とする。依存更新・互換修正・検証は同じ toolchain compatibility intent であり、独立した production vertical slice に分割できないためである。（agent 仮決め）

## Capabilities

### New Capabilities

- `zipline-toolchain-compatibility`: fankt が Zipline 対応 Kotlin toolchain で全ターゲットを build / test でき、Kotlin 2.4 consumer から local artifact を消費できる契約。

### Modified Capabilities

- `javascript-portable-core`: portable core の JS build / test 契約を Kotlin 2.3.21 と kotlinx.serialization 1.10.0 以上の toolchain 上で維持する。
- `stdlib-time-public-api`: Kotlin 2.3 で stable になった `kotlin.time.Instant` / `Clock` から不要な `ExperimentalTime` 境界を除き、consumer guidance を現在の最低 Kotlin version に合わせる。

## Impact

- 主な変更箇所: `gradle/libs.versions.toml`、Kotlin 2.3 互換修正が必要な source / build logic、対応する test。
- 依存: Kotlin compiler / Gradle plugin 2.3.21、kotlinx.serialization 1.10.0、Android Gradle Plugin 8.13.2、Ktorfit 2.7.3、KSP 2.3.6。
- consumer compatibility: Kotlin/Native・Kotlin/JS metadata の最低 consumer compiler は Kotlin 2.3.21。Kotlin 2.2 consumer は保証外となる。
- 検証対象: fankt 全ターゲット、Zipline 1.27.0 plugin 適用時の build、PixiView-KMP 2.4.0 consumer build。
- 公開 API の意図的な変更、Zipline plugin の恒久適用、guest / host 実装は含まない。（ユーザー確認済み: Issue #88）
