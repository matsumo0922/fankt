実装単位は 3 つの PR に分かれる。PR1 = 1〜3 章、PR2 = 4〜6 章、PR3 = 7〜8 章。

## 1. Zipline plugin の適用と guest target（PR1）

- [x] 1.1 `gradle/libs.versions.toml` に Zipline 1.27.0 の version、Gradle plugin、`zipline` core、`zipline-loader` を追加する
- [x] 1.2 `:fankt:fanbox` に `app.cash.zipline` plugin を適用する
- [x] 1.3 `js("guest") { nodejs(); binaries.executable() }` を宣言し、`guestMain` source set を `commonMain` に依存させる（`clientMain` には依存させない）
- [x] 1.4 guest target の publication を無効化する。既存の js publication が残ることを確認する
- [x] 1.5 `zipline { mainFunction }` に guest の entry point を設定し、guest bundle 生成 task が成功することを確認する

## 2. PR1 の実測（V1〜V3）

各項目は結果を design.md へ追記して閉じる。

- [x] 2.1 **V1**: plugin 適用後に `generateMetadataFileForJsPublication` が従来どおり成功することを確認する。既存 js klib が `es2015` / `MODULE_UMD` への変更で壊れないことを確かめる。壊れる場合は D1 の代替案（host bridge の別 module 切り出し）へ戻す判断を行う
- [x] 2.2 **V2**: 2 つ目の js target 追加が `generateMetadataFileForKotlinMultiplatformPublication` と klib ABI dump の `// Targets:` 行、`checkKotlinAbi` に与える影響を確認する。variant 解決の曖昧さが出る場合は distinguishing attribute を設定する
- [x] 2.3 **V3**: `./gradlew :fankt:fanbox:ziplineApiCheck --dry-run` で子 task が 1 つ以上あることと、`api/zipline-api.toml` が生成されることを確認する。子 task がゼロの場合は D3 の (a)(b) いずれかを選び、design と spec へ反映する。無内容に成功する検証を CI に残さない
- [x] 2.4 guest bundle に Ktor / Napier / kotlinx.io / Ksoup が含まれないことを確認する
- [x] 2.5 `nodejs()` のみで guest bundle が生成できるか確認する。できない場合は guest target に `browser()` を宣言する

## 3. guest service の定義（PR1）

- [x] 3.1 `commonMain` に `FanboxGuestService : ZiplineService` を定義する。`buildPostDetailRequest(postId: String): RequestDescriptor` と `parsePostDetail(body: String, statusCode: Int): GuestParseResult` の 2 関数のみとする
- [x] 3.2 `commonMain` に `@Serializable` sealed 型 `GuestParseResult` を定義する。成功（`FanboxPostDetail` を運ぶ）、スキーマ不一致、guest 実行故障の 3 分類を区別できる形にする
- [x] 3.3 `guestMain` に `FanboxGuestService` の実装を置く。既存の `FanboxEndpoints.postDetail` と `FanboxResponses.postDetail` へ委譲し、`FanboxException.SchemaMismatch` を捕捉してスキーマ不一致の結果へ変換する
- [x] 3.4 guest の entry point（`Zipline.get().bind`）を `guestMain` に置く
- [x] 3.5 bridge API のシグネチャに credential 型（`FanboxCookieStorage` / `FanboxTokenStore` / cookie record / CSRF token）が現れないことをテストで固定する
- [x] 3.6 `api/zipline-api.toml` を生成して commit する（V3 の結果次第）

## 4. bundle loader（PR2）

- [ ] 4.1 `clientMain` に `zipline-loader` 依存を追加する
- [ ] 4.2 guest 専用の単一スレッド dispatcher を用意する
- [ ] 4.3 loader ラッパーを実装する。manifest URL と trusted 公開鍵を受け取り、`ManifestVerifier` を構成する。署名検証を無効化する設定は使わない
- [ ] 4.4 D6 の 3 段 fallback を実装する。ローカル manifest → 同梱 fallback bundle → guest を経由しない既存経路
- [ ] 4.5 fallback の失敗検出で `LoadResult.Failure` と throw の両方を捕捉する。local 経路の署名検証失敗が例外として漏れることに対応する
- [ ] 4.6 fallback へ落ちた事実を診断経路へ報告する
- [ ] 4.7 manifest URL と trusted 公開鍵のいずれかが与えられない場合、guest を初期化せず既存経路で動作させる。既定値としての鍵も URL も埋め込まない

## 5. guest 経由の `post.info` 経路（PR2）

- [ ] 5.1 `Fanbox` に guest 初期化の遅延実行を実装する。コンストラクタでは engine を起動しない
- [ ] 5.2 `Fanbox.close()` が Zipline engine と guest dispatcher を解放するようにする
- [ ] 5.3 初期化失敗時に direct path へ sticky に固定する
- [ ] 5.4 初期化成功後の呼び出しで guest が故障した場合も、その呼び出しを direct path で処理し、以降 sticky に固定する
- [ ] 5.5 `FanboxPostRepository.getPostDetail` を guest 経由へ切り替える。guest が返した descriptor を `FanboxDescriptorValidator` へ通してから実行する
- [ ] 5.6 `GuestParseResult` のスキーマ不一致を `FanboxException.SchemaMismatch` へ変換する。この場合は fallback を起こさない
- [ ] 5.7 `GuestParseResult` の実行故障と `ZiplineException` を guest 経路の故障として扱う

**5 章の分量が 4 章と合わせて 1 本のレビューに収まらない場合、4 章を先行 PR とし 5 章を後続へ分ける。**

## 6. PR2 の実測と既存 gate（V4）

- [ ] 6.1 **V4**: `FanboxPostDetail` の sealed 階層（`Body` とその派生）が bridge を越えて正しく round-trip することを確認する。成立しない場合は D5 の代替（正規化済み JSON を返し host で model 構築）へ切り替える判断を行う
- [ ] 6.2 guest 経由の `post.info` のレイテンシと guest bundle のサイズを実測し、design へ記録する
- [ ] 6.3 `verifyKtorBoundary` と `verifyPersistenceBoundary` が Zipline の新依存に反応しないことを確認する。反応する場合は期待値を更新する
- [ ] 6.4 guest 経路の導入後も klib ABI dump と Android ABI に Zipline の型が現れないことを確認する

## 7. テスト（PR3）

- [ ] 7.1 テスト内で鍵ペアを生成し、正しく署名された manifest がロードされることを確認する
- [ ] 7.2 改竄された署名を持つ manifest が実行されず fallback bundle へ落ちることを確認する
- [ ] 7.3 信頼されない鍵名の manifest が実行されず fallback bundle へ落ちることを確認する
- [ ] 7.4 同梱 fallback bundle の署名検証失敗が呼び出し元へ例外として漏れず、既存経路で `post.info` が成功することを確認する
- [ ] 7.5 manifest URL へ到達できない場合に同梱 fallback bundle で動作することを確認する
- [ ] 7.6 **受け入れ条件の実証**: `body` 解釈の guest bundle と `body.post` 解釈の guest bundle を用意し、host を変更せず manifest の差し替えだけで挙動が変わることを確認する
- [ ] 7.7 manifest URL と公開鍵を与えない構成で guest engine が起動せず、本 change の前と同じ結果になることを確認する
- [ ] 7.8 guest の初期化失敗後と実行故障後に、いずれも再試行されず direct path で完了することを確認する
- [ ] 7.9 秘密鍵と署名済み bundle をリポジトリへ commit していないことを確認する

## 8. CI とドキュメント（PR3）

- [ ] 8.1 `.github/workflows/pull-request-lint.yml` に guest bundle のビルド task を追加する
- [ ] 8.2 bridge API の検証を CI へ追加する（V3 の結果に従う）
- [ ] 8.3 `./gradlew build` と `:fankt:fanbox:jsTest` が通ることを確認する
- [ ] 8.4 README に OTA prototype の位置づけを追記する。guest 経路が明示的な設定を要すること、既定では従来どおり動作することを書く
- [ ] 8.5 変更した機能名・クラス名・コマンド名で README を grep し、誤りになった記述がないか確認する
