実装単位は 2 つの PR に分かれる。PR1 = 1〜3 章（署名と fixture）、PR2 = 4〜6 章（配信と文書）。

PR1 は配信を伴わないため単独で merge できる。PR2 は main への push で実際に配信を始めるため、PR1 の fixture が入った後に merge する。

## 1. 署名鍵の配線（PR1）

- [x] 1.1 `generateZiplineManifestKeyPairEd25519` を実行し、Ed25519 鍵ペアを生成する（V1）
- [x] 1.2 生成した秘密鍵を GitHub Secrets へ登録する。公開鍵は consumer が焼き込む値としてリポジトリに記録する
- [x] 1.3 `fankt/fanbox/build.gradle.kts` の `zipline { }` に `signingKeys` を追加する。秘密鍵は Gradle property から読み、未設定なら登録しない
- [x] 1.4 秘密鍵を渡したビルドで manifest に署名が載ることを確認する（V2）
- [x] 1.5 秘密鍵を渡さないビルドが成功し、署名のない manifest を生成することを確認する（V2）

## 2. wire schema の golden fixture（PR1）

- [x] 2.1 `RequestDescriptor` の代表値を serialize し、fixture として固定するテストを追加する
- [x] 2.2 `GuestParseResult` の全 subtype について同様に固定する
- [x] 2.3 `FanboxPostDetail` を `Body` の sealed 階層を含む代表値で固定する
- [x] 2.4 いずれかの型の field 名を一時的に変更し、テストが失敗することを確認する（変更は戻す）

## 2b. guest の失敗時の退避（PR1）

- [x] 2b.1 `FanboxGuestHost` の `guest.service.buildPostDetailRequest` と `parsePostDetail` の catch を、`CancellationException` の再 throw を保ったまま `Exception` へ広げ、`disableAndFallback` へ流す。`CancellationException` の catch は最初の節に置く
- [x] 2b.1b 既存の `guestOrNull()` の `catch (failure: Throwable)` は変更しない（独立レビューの指摘により当初方針を撤回）。揃えると初期化中の `Error` が consumer へ伝播し、既存の可用性を下げる。理由は design.md D7 に記録
- [x] 2b.2 退避が診断経路へ報告されることを確認する
- [x] 2b.3 host が decode できない値を返す guest で `post.info` が直接経路で成功し、例外が漏れないことをテストで確認する（V6）
- [x] 2b.4 descriptor 側の decode 失敗でも同様に退避することをテストで確認する
- [x] 2b.5 bridge API の関数シグネチャが一致しない guest で退避することをテストで確認する（`ZiplineApiMismatchException` は `Exception` を直接継承し `ZiplineException` の catch に掛からない）
- [x] 2b.6 guest を経由する呼び出しの cancel で `CancellationException` が伝播することをテストで確認する

## 3. PR1 の検証

- [x] 3.1 `./gradlew build` と `:fankt:fanbox:jsTest` が通ることを確認する
- [x] 3.2 リポジトリの追跡対象ファイルに秘密鍵の値が含まれないことを確認する
- [x] 3.3 PR CI が秘密鍵なしで通ることを確認する

## 4. 配信 workflow（PR2）

- [x] 4.1 main への push と `workflow_dispatch` で発火する workflow を追加する
- [x] 4.2 Secrets の秘密鍵を Gradle property として渡し、署名済み guest bundle をビルドする
- [x] 4.3 manifest が署名を持つことを配置前に確認する。持たない場合は失敗させる
- [x] 4.4 `destination_dir` を `zipline/v1` に限定して manifest と bundle を配置する
- [x] 4.5 `deploy-documents.yml` に `keep_files: true` を追加する。既存の documents URL を変えないため `destination_dir` は使わない
- [x] 4.6 配信 workflow が既存の documents を削除しないことを確認する（V5）。main の `2b3c68d8` での配信後、gh-pages に `fankt/` `index.html` 等の documents と `zipline/v1/` が併存することを確認した
- [ ] 4.7 documents 配信が `zipline/v1/` を削除しないことを確認する（V7）。次回の release publish で `deploy-documents.yml` が走った後に観測する

## 5. 配信された bundle の検証（PR2）

- [x] 5.1 配信された manifest URL から HTTPS で manifest が取得できることを確認する。`https://matsumo0922.github.io/fankt/zipline/v1/manifest.zipline.json` が HTTP 200 / 2,586 bytes を返し、`unsigned.signatures` に `fanboxGuest` の Ed25519 署名、`mainFunction`、9 module を含む。bundle 本体（`fankt-fankt-fanbox-guest.zipline` 152,508 bytes ほか）と documents のルートもいずれも 200
- [ ] 5.2 公開鍵を信頼する `Fanbox` から配信された manifest をロードし、`post.info` が guest 経路で成功することを確認する（V3）
- [x] 5.3 異なる公開鍵では検証が拒否されることを確認する。配信された manifest を実物の `ManifestVerifier` に通し、公開鍵で検証成功、異なる公開鍵と未知の鍵名では失敗することを確認した（一時テスト 3 件、リポジトリには残していない）
- [ ] 5.4 新旧 2 鍵で署名した manifest が、いずれか一方の鍵だけを信頼する host で検証を通ることを確認する（V4）

### 未実施項目の状況

- **4.7**: 次回の release publish 待ち。`keep_files: true` が削除を行わせないことは action の実装で確認済み
- **5.2**: 配信された manifest から guest を起動して `post.info` を通す検証。manifest と bundle の取得（5.1）と署名検証（5.3）は確認済みで、残るのは engine を起動して実際に解釈させる部分。FANBOX への認証済みリクエストを要するため、consumer 側（matsumo0922/PixiView-KMP#138）の有効化と併せて確認する
- **5.4**: 鍵のローテーションを実施する時点で確認する。`ManifestVerifier.verify` が認識できた最初の鍵で検証することは実装で確認済み

### 前提として行った設定変更

このリポジトリの GitHub Pages は有効化されていなかった（`has_pages=false`）。`deploy-documents.yml` が gh-pages へ配信を続けていた一方で公開されておらず、README が案内する API Reference の URL も 404 を返していた。gh-pages ブランチを source として有効化した（ユーザー確認済み）。

有効化の直後は `status` が `building` のまま留まり、その間の HTTPS 取得は 404 を返す。`POST /repos/{owner}/{repo}/pages/builds` でビルドを起こすと 21.8 秒で `built` になり、以降は 200 を返した。初回有効化時はビルドの完了を待つ必要がある。

### 実配信を待った理由と、その結果

4.6 / 4.7 / 5 章はいずれも gh-pages への実配信を経た後にしか観測できない。配信は本 change の merge によって初めて起きるため、merge 前には checked へ移せなかった。

merge 後に配信が走り、4.6 / 5.1 / 5.3 が観測できた。可用性はこの間も保たれていた: consumer が manifest に到達できない場合は既存の直接経路で動作し、それは本 change 以前と同じ挙動である（`zipline-guest-bundle-loading` の fallback 契約）。

残る 4.7 は次回の release publish、5.2 は consumer 側の有効化、5.4 は鍵ローテーションの実施を待つ。いずれも本 change の側でできる検証は終えている。

## 6. 文書（PR2）

- [x] 6.1 鍵のローテーション手順を文書化する。移行期間中の二重署名、旧鍵の廃止時期の判断、公開鍵を焼き込んだ consumer への影響を含める
- [x] 6.2 bridge API バージョンを上げる条件と、旧バージョンのパスを消さない規則を文書化する
- [x] 6.3 配信した bundle に問題があった場合の緊急停止手順を文書化する。gh-pages から manifest を削除すれば consumer は fallback するため、これが実質的な停止手段になる
- [x] 6.4 README に配信の位置づけと上記文書への参照を追加する
- [x] 6.5 consumer が使う manifest URL と公開鍵を PixiView-KMP#138 が参照できる形で記録する

## 送り先

本 change で扱わず、後続へ送る項目。

- 複数 bridge API バージョンの並行運用ルール（旧バージョンの停止判断、consumer 分布の把握）→ #100
- 同梱 fallback bundle の更新運用 → #99
- consumer 側の kill switch → 不要と判断。緊急停止は配信側（6.3）で行える。consumer が個別に無効化する必要が生じた場合は guest コンストラクタを使わない選択で足りる（agent 仮決め）
