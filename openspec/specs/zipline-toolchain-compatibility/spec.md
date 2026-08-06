# zipline-toolchain-compatibility Specification

## Purpose

Define the Kotlin, serialization, Zipline, and consumer compiler compatibility boundary for fankt publications.
## Requirements
### Requirement: fankt uses the Zipline-compatible Kotlin toolchain
Issue #88 の「Kotlin 2.3.21」「kotlinx.serialization 1.10.0 以上」「Kotlin 2.4 系へ上げない」に対応する。fankt は Kotlin compiler / Gradle plugin 2.3.21 と kotlinx.serialization 1.10.0 を使用し、Zipline が Kotlin 2.4 に対応するまで Kotlin 2.3.x に留まらなければならない（SHALL）。

#### Scenario: Compatible versions and constraint are recorded
- **WHEN** `gradle/libs.versions.toml` の Kotlin と kotlinx.serialization version、および OpenSpec と README の compatibility guidance を検査する
- **THEN** Kotlin は 2.3.21、kotlinx.serialization は 1.10.0 であり、Kotlin 2.4 へ上げない制約は version catalog の version 定義へ説明 comment を追加せず OpenSpec と README に記録されている

### Requirement: fankt builds and tests every supported target
Issue #88 の「全 target の build / test」と受け入れ条件 `./gradlew build`、`:fankt:fanbox:jsTest` に対応する。toolchain update 後も、fankt の既存 target と検証 task はすべて成功しなければならない（SHALL）。

#### Scenario: Full build passes
- **WHEN** final implementation HEAD で `./gradlew build` を実行する
- **THEN** Android（JVM bytecode）、iOS、JS を含む configured target の build / test / boundary check が失敗せず完了し、存在しない standalone JVM target は保証に含めない

#### Scenario: JavaScript tests pass explicitly
- **WHEN** final implementation HEAD で `./gradlew :fankt:fanbox:jsTest` を実行する
- **THEN** portable-core test が Kotlin/JS 上で失敗せず完了する

### Requirement: Zipline 1.27.0 compiler plugin is compatible
Issue #90 の「`app.cash.zipline` 1.27.0 を production branch へ恒久適用する」に対応する。fankt は `app.cash.zipline` 1.27.0 の Gradle plugin を `:fankt:fanbox` へ恒久的に適用し、guest bridge API の固定を伴う状態で build できなければならない（SHALL）。plugin は適用先 project の全 compilation へ compiler plugin を入れる。これは host 側の `Zipline.take` が compiler plugin による書き換えを前提とするために必要である。

plugin は Kotlin/JS IR target 全体の compiler options を `es2015` と UMD module kind へ強制する。したがって既存の publish 用 Kotlin/JS target の compile 条件が変わる。この変更が publication を壊さないことを確認しなければならない（SHALL）。

#### Scenario: Disposable plugin-applied build passes

- **WHEN** production branch で `app.cash.zipline` 1.27.0 を `:fankt:fanbox` に適用した full build と `:fankt:fanbox:jsTest` を実行する
- **THEN** Gradle plugin と compiler plugin が解決・適用され、既存 compilation と test が失敗せず完了する。plugin は production branch に恒久適用されているため、この確認に disposable copy を要さない

#### Scenario: Publication survives the compiler options change

- **WHEN** plugin 適用後に `:fankt:fanbox` の Kotlin/JS publication metadata を生成する
- **THEN** 従来どおり生成され、既存の consumer 向け klib が失われない

#### Scenario: Guest bridge API is fixed and verified

- **WHEN** guest bridge API の検証を実行する
- **THEN** 記録済みの API 定義と照合され、実際に検査対象を持ったうえで差分がなければ成功する

### Requirement: Kotlin 2.4 PixiView consumes local fankt artifacts
Issue #88 の「PixiView-KMP 側で local publish artifact を使った build」に対応する。Kotlin 2.4.0 の PixiView-KMP は Kotlin 2.3.21 で publish した `fanbox` と `fanbox-persistence-room` artifact を隔離 repository から解決し、それらを使う `core:repository` production module を Android と iOS の両方で compile できなければならない（SHALL）。

#### Scenario: Isolated local artifact consumption succeeds
- **WHEN** final implementation HEAD の両 artifact を unique version で空の一時 Maven repository へ publishし、検証開始時の PixiView-KMP `origin/main` detached worktree が `exclusiveContent` でその repository だけから `:core:repository:compileAndroidMain` と `:core:repository:compileKotlinIosSimulatorArm64` を実行する
- **THEN** 両 coordinate が unique version で解決され、resolved artifact の SHA-256 が一時 repository 内 artifact と一致し、Kotlin 2.4.0 compiler による Android と iOS の production compilation が失敗なく完了する

### Requirement: Consumer compiler minimum follows the producer metadata version
Issue #88 の Kotlin 2.3.21 producer update に伴い、Kotlin/Native・Kotlin/JS metadata を消費する compiler は Kotlin 2.3.21 以上でなければならない（SHALL）。Kotlin 2.2 consumer compatibility は保証しない。

#### Scenario: Consumer compatibility boundary is explicit
- **WHEN** version catalog、OpenSpec、README の consumer guidance を検査する
- **THEN** Kotlin 2.3.21 が producer と consumer の最低 version として記録され、Kotlin 2.2 consumer が保証対象であるとは記載されていない

