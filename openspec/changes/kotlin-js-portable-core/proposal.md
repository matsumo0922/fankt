## Why

`:fankt:fanbox` は Android と iOS のみを publish しており、Kotlin/JS ターゲットを持たない。PixiView-KMP#104 が構想する Zipline OTA 配信では、リクエスト組み立てとレスポンス解釈を QuickJS 上の Kotlin/JS guest として実行するため、これらのコードが JS でコンパイルできる必要がある。

#38 で `RequestDescriptor` と `FanboxRequestExecutor` を分離し、endpoint / response / mapper / parser を `VerifyPortableImportBoundaryTask` が守る portable core として切り出した。しかし core が置かれた `commonMain` には Ktor 実行、ダウンロード、Ksoup による HTML パース、および JVM 専用依存（`kotlin-stdlib-jdk8` / `kotlin-reflect`）が同居しており、そのままでは JS ターゲットを追加できない。

本 change は portable core を JS でコンパイル可能にし、`:fankt:fanbox:jsTest` で #15 のフィクスチャテストを実行できる状態にする。

## What Changes

- `commonMain` の依存から JVM 専用の `kotlin-stdlib-jdk8` と `kotlin-reflect` を外し、Napier を含む `bundles.infra.api` の適用範囲を JS 非対応コードが残る source set へ限定する。
- Ktor / Napier / kotlinx.io / Ksoup / `Dispatchers.IO` に依存するコードを `commonMain` から、Android と iOS が共有する intermediate source set へ移す。対象は `Fanbox`、`ClientBuilder`、`FanboxDependencies`、`FanboxCookiesStorageAdapter`、`FanboxDownloadDestination`、`FanboxExceptionFactory` の Ktor 依存部、`transport/ktor/**`、repository 群、`FanboxMetadataParser`。
- `commonMain` に残す portable core を、endpoint / response / mapper / domain model / 認証ストレージ contract / 例外型 / tolerant decode に確定させる。
- `:fankt:fanbox` に `js(IR)` ターゲットを library として追加する。`browser()` と `nodejs()` は宣言せず、テスト実行に必要な環境のみ設定する。
- `commonTest` のうち Ktor MockEngine に依存しないフィクスチャテストを JS でも実行できるようにし、MockEngine 依存のテストを Ktor 実行コードと同じ source set へ移す。
- CI の検証コマンドに JS ターゲットのビルドとテストを追加する。
- ターゲット追加で影響を受ける ABI validation、publication task の列挙、`verifyKtorBoundary` / `verifyPersistenceBoundary` / `verifyPortableImportBoundary` を JS ターゲットの存在下で成立するよう更新する。
- **（agent 仮決め）** 変更は「依存整理と source set 再編」「JS ターゲット追加」「CI と publication 対応」の 3 stage に分割し、それぞれ独立してマージ可能にする。

## Non-goals

- Zipline の導入、`ZiplineService` 境界の定義、bundle の署名・配信。これらは PixiView-KMP#104 の担当であり、本 change は Kotlin/JS でコンパイルできる core を用意するところまでを範囲とする。
- Kotlin バージョンの引き上げ。Zipline 1.27.0 は Kotlin 2.3.x を要求するが、本 change は現行の 2.2.10 のまま `js(IR)` ターゲットを追加する。バージョン引き上げは fankt 全体に影響する独立した作業として別途扱う。
- JS ターゲットからの HTTP 実行。JS 成果物は request descriptor を組み立て raw response 文字列を解釈する純関数ライブラリであり、通信は行わない。
- wasmJs ターゲットの追加。
- 公開 API の意味的な変更。Android と iOS の利用者から見た `Fanbox` の API と挙動は維持する。

## Capabilities

### New Capabilities
- `javascript-portable-core`: FANBOX の request 組み立てと response 解釈を Kotlin/JS ターゲットでコンパイル・テストでき、HTTP 実行と HTML パースを JS 非対応の source set へ隔離する。

### Modified Capabilities
- `portable-request-descriptors`: import 境界の検査対象に、JVM 専用依存と JS 非対応 API の排除を加える。
- `pure-response-parsing`: HTML メタデータ抽出の実行主体を、portable core 内の Ksoup 呼び出しから、core 外の parser 実装へ移す。
- `codegen-free-fanbox-client`: 成果物のターゲット構成に Kotlin/JS を追加し、既存の Android / iOS 公開 API と wire 挙動を維持する。

## Impact

- `fankt/fanbox/build.gradle.kts`: ターゲット宣言、source set 依存、ABI validation、publication task の列挙、3 つの boundary 検証タスク。
- `build-logic/src/main/java/primitive/kmp/`: JS ターゲット用 primitive plugin の追加。
- `gradle/libs.versions.toml`: `bundles.infra.api` の構成、JS 対応 source set 向けの依存整理。
- `fankt/fanbox/src/commonMain`: Ktor / Napier / Ksoup / kotlinx.io / `Dispatchers.IO` 依存コードの移動。
- `fankt/fanbox/src/commonTest`: MockEngine 依存テストの移動、フィクスチャテストの JS 実行。
- 新規 source set: Android と iOS が共有する intermediate source set、および `jsMain` / `jsTest`。
- `.github/workflows/pull-request-lint.yml`: JS ビルドとテストの実行。
- `.github/workflows/deploy-library.yml`: JS artifact の publish。
- `README.md` と OpenSpec: 対応ターゲットと portable core の境界。
