## Why

FANBOX の非公式 API が予告なく変わると fankt の修正が必要になり、PixiView の復旧には Maven Central publish、依存更新、ストア審査、ユーザーのアップデートで 2〜3 日かかる（実例: #12 の `post.info` における `body` → `body.post` ラップ変更）。request 組み立てと response 解釈を Zipline の guest（QuickJS 上の Kotlin/JS）として OTA 配信できれば、ストア審査を経ずに復旧できる。

本 change は #90 に対応し、その guest / host 構成を fankt 内に prototype として作る。目的は配信基盤の構築ではなく、**#12 相当の修正が bundle 差し替えだけで反映されること**をローカル配信で実証することにある。

## What Changes

- `app.cash.zipline` 1.27.0 の Gradle plugin を `:fankt:fanbox` の production build へ恒久適用する。#89 で確認したのは disposable copy 上のビルド互換性のみで、plugin 自体は production branch に入っていない。`Zipline.take` は compiler plugin が無い compilation では実行時に失敗するため、guest 側だけでなく host 側の compilation にも plugin が要る
- guest bundle 用に 2 つ目の Kotlin/JS target（`js("guest")`）を宣言し、そこに `ZiplineService` を定義する。request 組み立て（`RequestDescriptor` を返す）と response 解釈（raw response body から domain model を返す）の 2 系統を持つ。publish 対象は既存の js target のみとする
- host（`clientMain`）に、guest を呼び出して得た descriptor を既存の `FanboxDescriptorValidator` へ通してから `KtorFanboxRequestExecutor` で実行する経路を追加する
- guest bundle のビルド task を通し、guest bridge API を固定する。`ziplineApiCheck` の子 task が JVM compile task にしか登録されないため、実効を持つかを実測して確認する
- 同梱 fallback bundle と、ローカル manifest を読む loader を用意する。配信先へ到達できない場合と署名検証に失敗した場合はいずれも fallback bundle で動作し、それも失敗した場合は guest を経由しない既存経路へ落ちる
- guest bridge API の型に credential（`FANBOXSESSID` / CSRF token）を渡す経路が存在しないことを保証する
- guest 経路が有効になるのは、呼び出し側が manifest URL と信頼する公開鍵を明示的に与えたときに限る。いずれも与えられない構成では既存の直接呼び出しで動作する

**スコープ**（ユーザー確認済み）

- guest 経由にするのは #12 の実証に必要な最小 endpoint（`post.info`）に限る。他の 28 operation は既存の直接呼び出しのまま残す。全 operation の guest 化は配信基盤の issue 以降の別 change とする
- guest は domain model（`FanboxPostDetail`）を構築して bridge を越えさせる。response 解釈だけでなく mapper 側の変更も OTA で配信できる形を採る
- 署名検証の実証はテスト内で生成した鍵ペアで完結させる。秘密鍵も署名済み bundle もリポジトリへ commit しない

**非対象**（issue が明記する範囲外）

- 配信基盤（ホスティング、署名鍵の運用管理、kill switch、同梱 fallback bundle の更新運用）
- bridge API のバージョニング運用設計。本 change は単一バージョンで実証する
- PixiView 側の対応（PixiView-KMP#104）

## Capabilities

### New Capabilities

- `ota-guest-request-pipeline`: guest が request descriptor と domain model を供給し、host が検証済み HTTP 実行と credential 付与を担う分割。guest bridge API の境界と、そこに credential 型が現れないこと
- `zipline-guest-bundle-loading`: guest bundle のロード、ローカル manifest 配信、署名検証、fallback bundle への退避、`Fanbox` 生成時の初期化コストの扱い

### Modified Capabilities

- `zipline-toolchain-compatibility`: Zipline 1.27.0 plugin の適用が「disposable copy で検証」から「production build へ恒久適用」へ変わる
- `javascript-portable-core`: `commonMain` の portable core が、既存の js target に加えて guest target でもコンパイルされ、Zipline guest bundle の内容になる

## Impact

- `fankt/fanbox/build.gradle.kts`: Zipline plugin の適用、`js("guest")` target、guest bundle task、publication からの guest target 除外
- `fankt/fanbox/src/commonMain`: guest bridge service の interface と戻り値型、`app.cash.zipline` core 依存
- `fankt/fanbox/src/guestMain`（新設）: guest 側 `ZiplineService` の実装
- `fankt/fanbox/src/clientMain`: `Fanbox` の初期化経路、guest 呼び出しを挟む `post.info` 経路、bundle loader、`zipline-loader` 依存
- `gradle/libs.versions.toml`: Zipline の version と artifact 定義
- `fankt/fanbox/api/fanbox.klib.api`: js target 追加に伴う `// Targets:` 行の変化（実測で確認する）
- 既存の `RequestDescriptor` / `FanboxPostDetail` など bridge を越える型のシリアライズ可能性
- `.github/workflows/pull-request-lint.yml`: guest bundle build と bridge API 検証の実行
- 公開 API（`Fanbox` クラス）と publication の構成は変更しない。ただし依存グラフと runtime footprint は変わる。bridge service の interface を `commonMain` に置くため `app.cash.zipline` の core 依存が published な全 target に入り、`zipline-loader` の追加により Android では ABI ごとのネイティブライブラリが増える
- `verifyKtorBoundary` / `verifyPersistenceBoundary` の期待値: 新依存への反応を確認する
- README: OTA prototype の位置づけ
