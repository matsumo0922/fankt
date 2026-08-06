## ADDED Requirements

### Requirement: The portable core compiles as the Zipline guest bundle

Issue #90 の「guest bundle のビルド task を通す」に対応する。#39 で portable にした core（request descriptor、endpoint builder、response parsing、entity と domain model、tolerant list decoding）は、既存の publish 用 Kotlin/JS target に加えて guest 用の Kotlin/JS target でもコンパイルされ、Zipline guest bundle の内容にならなければならない（SHALL）。guest bundle は HTTP 実行、media download、Napier logging、HTML パースのいずれも含まない。guest 用の target は publication に含めない。

#### Scenario: Guest bundle compiles from the portable core

- **WHEN** guest 用 target の Zipline bundle 生成 task を実行する
- **THEN** portable core が unresolved reference なくコンパイルされ、bundle が生成される

#### Scenario: Guest bundle excludes the client-only layer

- **WHEN** guest 用 target がコンパイルする source set を検査する
- **THEN** `io.ktor`、`io.github.aakira.napier`、`kotlinx.io`、Ksoup の import を含む source set がいずれも含まれていない

#### Scenario: Portable core changes break the guest build

- **WHEN** portable core へ Kotlin/JS で解決できない依存を追加する
- **THEN** guest 用 target のコンパイルが失敗する

#### Scenario: Guest target is excluded from publication

- **WHEN** `:fankt:fanbox` の publication 一覧を検査する
- **THEN** guest 用 Kotlin/JS target の publication が存在せず、既存の js publication が残っている
