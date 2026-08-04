## ADDED Requirements

### Requirement: fankt uses the Zipline-compatible Kotlin toolchain
Issue #88 の「Kotlin 2.3.21」「kotlinx.serialization 1.10.0 以上」「Kotlin 2.4 系へ上げない」に対応する。fankt は Kotlin compiler / Gradle plugin 2.3.21 と kotlinx.serialization 1.10.0 を使用し、Zipline が Kotlin 2.4 に対応するまで Kotlin 2.3.x に留まらなければならない（SHALL）。

#### Scenario: Version catalog selects the compatible versions
- **WHEN** `gradle/libs.versions.toml` の Kotlin と kotlinx.serialization version を検査する
- **THEN** Kotlin は 2.3.21、kotlinx.serialization は 1.10.0 であり、Kotlin 2.4 へ上げない制約が隣接する comment に記録されている

### Requirement: fankt builds and tests every supported target
Issue #88 の「全 target の build / test」と受け入れ条件 `./gradlew build`、`:fankt:fanbox:jsTest` に対応する。toolchain update 後も、fankt の既存 target と検証 task はすべて成功しなければならない（SHALL）。

#### Scenario: Full build passes
- **WHEN** final implementation HEAD で `./gradlew build` を実行する
- **THEN** Android、JVM、iOS、JS を含む configured target の build / test / boundary check が失敗せず完了する

#### Scenario: JavaScript tests pass explicitly
- **WHEN** final implementation HEAD で `./gradlew :fankt:fanbox:jsTest` を実行する
- **THEN** portable-core test が Kotlin/JS 上で失敗せず完了する

### Requirement: Zipline 1.27.0 compiler plugin is compatible
Issue #88 の「Zipline 1.27.0 の Gradle plugin を適用した build」に対応する。fankt は `app.cash.zipline` 1.27.0 を `:fankt:fanbox` に適用した状態で既存 source を compile / build できなければならない（SHALL）。恒久的な plugin 適用と guest API tracking はこの requirement に含めない。

#### Scenario: Disposable plugin-applied build passes
- **WHEN** final implementation HEAD の disposable copy で `app.cash.zipline` 1.27.0 を `:fankt:fanbox` に適用し、guest API tracking を無効にして full build と `:fankt:fanbox:jsTest` を実行する
- **THEN** Gradle plugin と compiler plugin が解決・適用され、既存 compilation と test が失敗せず完了する

### Requirement: Kotlin 2.4 PixiView consumes local fankt artifacts
Issue #88 の「PixiView-KMP 側で local publish artifact を使った build」に対応する。Kotlin 2.4.0 の PixiView-KMP は Kotlin 2.3.21 で publish した fankt artifact を解決し、fankt を使う production module を compile できなければならない（SHALL）。

#### Scenario: Isolated local artifact consumption succeeds
- **WHEN** final implementation HEAD の fankt artifact を空の一時 Maven repository へ publish し、PixiView-KMP の detached worktree がその repository から artifact を解決して build する
- **THEN** PixiView-KMP の Kotlin 2.4.0 compiler は fankt の klib metadata と API を読み、production module の build を失敗なく完了する
