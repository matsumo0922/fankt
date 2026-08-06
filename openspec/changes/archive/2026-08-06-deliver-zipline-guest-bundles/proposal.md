## Why

#90 で Zipline guest / host の prototype が完成し、`post.info` の response 解釈を bundle 差し替えだけで変更できることを実物の `ManifestVerifier` と QuickJS engine で実証した。ただし #90 は配信基盤を範囲外とし、テスト内で生成した鍵ペアとローカル manifest で完結させている。

本 change は #98 に対応し、**bundle を実際に配信する経路**を作る。これが PixiView-KMP#104 の唯一のクリティカルパスである。`Fanbox` の guest コンストラクタは manifest URL と信頼する Ed25519 公開鍵を必須で要求するため、配信先と鍵が確定しない限り consumer 側は guest を有効化できない。

## What Changes

調査の結果、Zipline plugin が鍵生成と manifest 署名を既に提供していることが分かった。本 change の実装は、その機能へ秘密鍵を渡し、生成物を配置する経路に限られる。

- **鍵の生成に自前の実装を作らない。** Zipline plugin が登録する `generateZiplineManifestKeyPairEd25519` task が Ed25519 鍵ペアを出力する。#90 のテストが `app.cash.zipline.loader.internal.tink.subtle.newKeyPairFromSeed` を internal パッケージから参照しているのは、multiplatform の test source から呼べる公開 API が無いためであり、production の鍵生成には Gradle task を使う
- **署名にも自前の実装を作らない。** `zipline { signingKeys { create("...") { privateKeyHex.set(...) } } }` に秘密鍵を渡すと、`ZiplineCompileTask` が Zipline CLI の `--sign` へ委譲して manifest に署名する。秘密鍵は GitHub Secrets から Gradle property として渡し、リポジトリにも build script にも置かない
- 署名済み bundle を GitHub Pages（gh-pages ブランチ）へ配置する CI workflow を追加する。fankt には `deploy-documents.yml` で gh-pages へ配信する実績があり、新しい外部サービスを増やさない
- manifest URL のパスに bridge API バージョンを埋める（`.../zipline/v1/manifest.zipline.json`）。アプリは焼き込んだ URL のバージョンを見続けるため、古い host が新しい bridge API の bundle を読む事故が構造的に起きない
- bridge を越える 3 型（`RequestDescriptor` / `GuestParseResult` / `FanboxPostDetail`）の serialize 結果を golden fixture で固定する。`ziplineApiCheck` は `FanboxGuestService` の関数シグネチャしか固定せず、pass-by-value で bridge を越える型の schema は検証対象外である
- host 側の catch を `CancellationException` 以外の失敗へ広げ、既存経路へ退避させる。fixture と `ziplineApiCheck` は配信前に止めるための検証であり、網羅の漏れを保証しない。漏れた失敗は `SerializationException`（値の decode 失敗）や `ZiplineApiMismatchException`（関数シグネチャの不整合、`Exception` を直接継承するため `ZiplineException` の catch に掛からない）として現れ、`Fanbox` の公開契約の外へ漏れる。型を列挙する方式は Zipline 側に型が増えるたびに同じ穴が開くため採らない（D7）

### 鍵のローテーションに公開 API の変更は要らない

`FanboxGuestDeliveryConfig` は trusted key を 1 組しか受け取らない。当初これがローテーションの障害になると見ていたが、`ManifestVerifier.verify` の実装を読んで不要と判断した。

`ZiplineManifest.signatures` は鍵名から署名への map であり、`verify` は**manifest の署名のうち最初に認識できた鍵で検証して成功を返す**。したがって移行期間中は新旧 2 つの鍵で manifest に署名すればよい。旧鍵しか知らないアプリは旧署名で検証が通り、新鍵を焼き込んだアプリは新署名で通る。

`FanboxGuestDeliveryConfig` を複数鍵へ広げるのは、consumer が 2 つの鍵を同時に焼き込む必要が生じたときに限られる。上記の方式ではその必要がないため、公開 API は変更しない。

**スコープ**

- 配信するのは `post.info` の guest bundle のみ。現在 guest 化されている operation がこれだけであるため
- ローテーションは手順の文書化までとし、実際の鍵交換は行わない

**非対象**

- 同梱 fallback bundle を consumer から指定する公開 API（#99）
- `post.info` 以外の 28 operation の guest 化（#100）
- PixiView 側の有効化と kill switch（PixiView-KMP#138）
- kill switch。#98 の issue 本文にも含めていない。無効化は consumer が guest コンストラクタを使わない選択で足りる

## Capabilities

### New Capabilities

- `zipline-guest-bundle-delivery`: 署名済み guest bundle の配信。鍵の生成と保管、CI での署名とアップロード、manifest URL の構成と bridge API バージョンの対応、bridge を越える型の wire schema の固定

### Modified Capabilities

- `zipline-guest-bundle-loading`: 退避する故障の種類に bridge の decode 失敗を加える。ロード側の既存 scenario は変更しない

## Impact

- `.github/workflows/`: 署名済み bundle をビルドして gh-pages へ配置する workflow を追加する
- `.github/workflows/deploy-documents.yml`: `keep_files: true` の追加。現在は gh-pages のルート全体を削除して再配置するため、bundle を同じブランチへ置くと release publish ごとに消える（D3）
- `fankt/fanbox/build.gradle.kts`: `zipline { signingKeys { ... } }` の追加。秘密鍵は Gradle property から読み、未設定なら署名なしでビルドする（ローカル開発と PR CI が鍵なしで通るようにするため）
- `fankt/fanbox/src/clientMain/.../guest/FanboxGuestHost.kt`: `guest.service.*` 呼び出しの catch を `Exception` へ広げる。あわせて既存の `guestOrNull()` の `Throwable` を `Exception` へ揃える（同じ関心に 2 つの書き方を残さないため）
- `fankt/fanbox/src/commonTest`: bridge を越える 3 型の golden fixture
- `fankt/fanbox/src/clientTest`: decode 失敗時の退避のテスト
- gh-pages: `zipline/v1/` 配下に manifest と bundle
- GitHub Secrets: 署名用の Ed25519 秘密鍵
- README: 配信の位置づけと鍵運用の参照先
- 公開 API は変更しない
