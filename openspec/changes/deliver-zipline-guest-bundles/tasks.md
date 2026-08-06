実装単位は 2 つの PR に分かれる。PR1 = 1〜3 章（署名と fixture）、PR2 = 4〜6 章（配信と文書）。

PR1 は配信を伴わないため単独で merge できる。PR2 は main への push で実際に配信を始めるため、PR1 の fixture が入った後に merge する。

## 1. 署名鍵の配線（PR1）

- [ ] 1.1 `generateZiplineManifestKeyPairEd25519` を実行し、Ed25519 鍵ペアを生成する（V1）
- [ ] 1.2 生成した秘密鍵を GitHub Secrets へ登録する。公開鍵は consumer が焼き込む値としてリポジトリに記録する
- [ ] 1.3 `fankt/fanbox/build.gradle.kts` の `zipline { }` に `signingKeys` を追加する。秘密鍵は Gradle property から読み、未設定なら登録しない
- [ ] 1.4 秘密鍵を渡したビルドで manifest に署名が載ることを確認する（V2）
- [ ] 1.5 秘密鍵を渡さないビルドが成功し、署名のない manifest を生成することを確認する（V2）

## 2. wire schema の golden fixture（PR1）

- [ ] 2.1 `RequestDescriptor` の代表値を serialize し、fixture として固定するテストを追加する
- [ ] 2.2 `GuestParseResult` の全 subtype について同様に固定する
- [ ] 2.3 `FanboxPostDetail` を `Body` の sealed 階層を含む代表値で固定する
- [ ] 2.4 いずれかの型の field 名を一時的に変更し、テストが失敗することを確認する（変更は戻す）

## 2b. guest の失敗時の退避（PR1）

- [ ] 2b.1 `FanboxGuestHost` の `guest.service.buildPostDetailRequest` と `parsePostDetail` の catch を、`CancellationException` の再 throw を保ったまま `Exception` へ広げ、`disableAndFallback` へ流す。`CancellationException` の catch は最初の節に置く
- [ ] 2b.1b 既存の `guestOrNull()` の `catch (failure: Throwable)` を `Exception` へ揃える。同じ関心に 2 つの書き方を残さないため
- [ ] 2b.2 退避が診断経路へ報告されることを確認する
- [ ] 2b.3 host が decode できない値を返す guest で `post.info` が直接経路で成功し、例外が漏れないことをテストで確認する（V6）
- [ ] 2b.4 descriptor 側の decode 失敗でも同様に退避することをテストで確認する
- [ ] 2b.5 bridge API の関数シグネチャが一致しない guest で退避することをテストで確認する（`ZiplineApiMismatchException` は `Exception` を直接継承し `ZiplineException` の catch に掛からない）
- [ ] 2b.6 guest を経由する呼び出しの cancel で `CancellationException` が伝播することをテストで確認する

## 3. PR1 の検証

- [ ] 3.1 `./gradlew build` と `:fankt:fanbox:jsTest` が通ることを確認する
- [ ] 3.2 リポジトリの追跡対象ファイルに秘密鍵の値が含まれないことを確認する
- [ ] 3.3 PR CI が秘密鍵なしで通ることを確認する

## 4. 配信 workflow（PR2）

- [ ] 4.1 main への push と `workflow_dispatch` で発火する workflow を追加する
- [ ] 4.2 Secrets の秘密鍵を Gradle property として渡し、署名済み guest bundle をビルドする
- [ ] 4.3 manifest が署名を持つことを配置前に確認する。持たない場合は失敗させる
- [ ] 4.4 `destination_dir` を `zipline/v1` に限定して manifest と bundle を配置する
- [ ] 4.5 `deploy-documents.yml` に `keep_files: true` を追加する。既存の documents URL を変えないため `destination_dir` は使わない
- [ ] 4.6 配信 workflow が既存の documents を削除しないことを確認する（V5）
- [ ] 4.7 documents 配信が `zipline/v1/` を削除しないことを確認する（V7）。既存 workflow は現在 gh-pages のルート全体を削除するため、この方向の検証が本体である

## 5. 配信された bundle の検証（PR2）

- [ ] 5.1 配信された manifest URL から HTTPS で manifest が取得できることを確認する
- [ ] 5.2 公開鍵を信頼する `Fanbox` から配信された manifest をロードし、`post.info` が guest 経路で成功することを確認する（V3）
- [ ] 5.3 異なる公開鍵を信頼する `Fanbox` では guest が使われず、直接経路で成功することを確認する
- [ ] 5.4 新旧 2 鍵で署名した manifest が、いずれか一方の鍵だけを信頼する host で検証を通ることを確認する（V4）

## 6. 文書（PR2）

- [ ] 6.1 鍵のローテーション手順を文書化する。移行期間中の二重署名、旧鍵の廃止時期の判断、公開鍵を焼き込んだ consumer への影響を含める
- [ ] 6.2 bridge API バージョンを上げる条件と、旧バージョンのパスを消さない規則を文書化する
- [ ] 6.3 配信した bundle に問題があった場合の緊急停止手順を文書化する。gh-pages から manifest を削除すれば consumer は fallback するため、これが実質的な停止手段になる
- [ ] 6.4 README に配信の位置づけと上記文書への参照を追加する
- [ ] 6.5 consumer が使う manifest URL と公開鍵を PixiView-KMP#138 が参照できる形で記録する

## 送り先

本 change で扱わず、後続へ送る項目。

- 複数 bridge API バージョンの並行運用ルール（旧バージョンの停止判断、consumer 分布の把握）→ #100
- 同梱 fallback bundle の更新運用 → #99
- consumer 側の kill switch → 不要と判断。緊急停止は配信側（6.3）で行える。consumer が個別に無効化する必要が生じた場合は guest コンストラクタを使わない選択で足りる（agent 仮決め）
