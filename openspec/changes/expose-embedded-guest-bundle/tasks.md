# Tasks

配送単位は単一 PR とする。公開 API の追加、その内部実装、テスト、文書が互いに独立してレビューできる縦切りにならず、分割しても各 PR が production path へ接続されないため。

## 1. 公開 API の追加

- [ ] 1.1 `FanboxEmbeddedGuestBundle` を `clientMain` に定義する。`fun interface` で `suspend fun read(fileName: String): ByteArray?` を持つ。KDoc は日本語で書き、ファイル名が manifest 名または sha256 の hex であること、不在時に `null` を返す契約を記す
- [ ] 1.2 `embeddedGuestBundle: FanboxEmbeddedGuestBundle` を**非既定の必須引数**として受け取る新しい public コンストラクタを追加する。位置は `guestTrustedEd25519PublicKey` の直後。**既存の guest コンストラクタには一切触れない**（引数を足すと既定値の有無に関係なく descriptor が変わり、v0.1.2 で公開済みの ABI が消える。実測で確認済み）
- [ ] 1.3 `FanboxGuestDeliveryConfig` が公開 API 由来の読み出し経路を保持する形に変える
- [ ] 1.4 追加後に、コンパイル済み `Fanbox.class` のコンストラクタ descriptor を列挙し、v0.1.2 の 2 行（`api/android/fanbox.api:2-3`）が無傷で残ることを確認する。ABI ダンプの自動生成は機能していないため、この確認は手で行う

## 2. 内部実装

- [ ] 2.1 `FanboxEmbeddedGuestBundle` から manifest を読み、`ZiplineManifest.decodeJson` で decode して各モジュールの sha256 を得て、すべてを読み出す処理を書く。manifest が `null` の場合と、モジュールが欠けている場合を区別して失敗を返す
- [ ] 2.2 読み出した内容を保持する read-only な `okio.FileSystem` 実装を書く。`metadataOrNull` と `source` を実装し、embedded 経路が呼ばない操作は `error()` で塞ぐ。塞いだ理由をコメントに残す
- [ ] 2.3 `ZiplineGuestLoader.load` の embedded 段が 2.1 と 2.2 を経由して `withEmbedded` を呼ぶ形に変える
- [ ] 2.4 2.1 の 2 種類の失敗を `FanboxDiagnosticSink` へ区別して報告する

## 3. テスト

- [ ] 3.1 公開 API から構成した同梱 bundle が、配信不達時にロードされ guest 経路で `post.info` が成功することを確認する（`ziplineTestTest`）
- [ ] 3.2 公開 API から構成した同梱 bundle の署名検証が失敗した場合、例外が漏れず直接経路で成功することを確認する
- [ ] 3.3 manifest が読めない場合と、モジュールが欠けている場合の診断が区別されることを確認する
- [ ] 3.4 `read` が返す不在（`null`）が例外にならないことを確認する
- [ ] 3.5 2.2 の `FileSystem` 実装が embedded 経路で必要な操作だけで足りることを確認する。塞いだ操作が呼ばれた場合に失敗することを固定する

## 4. 公開境界の検証

- [ ] 4.1 ABI ダンプを更新する（`:fankt:fanbox:updateLegacyAbi` と klib 側）
- [ ] 4.2 公開 ABI に `okio` の型が現れないことを検証する仕組みを加える。既存の `verifyKtorBoundary` が Ktor と kotlinx-datetime に対して行っている検査に okio を足す形を優先し、新しいタスクは作らない

## 5. 文書

- [ ] 5.1 README の Zipline OTA 節に同梱 bundle の節を追記する。embedded レイアウトの規約（F1）、ビルド出力との差（F2）、`ZiplineDownloadTask` の設定例、ダウンロード時に署名検証がない理由と実行時検証の関係、リリースごとの差し替え運用
- [ ] 5.2 README の「Embedded fallback packaging is not yet exposed to callers」の記述を現状に合わせて書き換える
- [ ] 5.3 変更した機能名・型名で docs/ と README を grep し、誤りになった記述がないか確認する

## 6. 検証

- [ ] 6.1 `./gradlew build` が通ることを確認する
- [ ] 6.2 `:fankt:fanbox:ziplineTestTest` が通ることを確認する。`FanboxSignedBundleTest.manifestReplacementChangesResponseInterpretation` は #85 に報告済みの flaky があるため、失敗した場合は再実行して切り分ける
- [ ] 6.3 detekt が通ることを確認する
