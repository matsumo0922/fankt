## Why

`FanboxGuestHost` の fallback は 3 段で設計されている（配信 manifest → 同梱 bundle → ネイティブ直接経路）が、2 段目を consumer から利用する経路が存在しない。`FanboxGuestDeliveryConfig.embeddedBundle` を受け取る型 `EmbeddedGuestBundle` が `internal` で、`Fanbox` の公開コンストラクタに渡す口がないためである。

3 段目があるため可用性は保たれるが、3 段目は guest を通らないため OTA で配信した修正が反映されない。配信先へ到達できない状態が続くと、bundle 側にしか存在しない修正が効かないまま古い挙動で動き続ける。2 段目があれば、少なくとも「アプリのリリース時点で最新だった bundle」までは guest 経路で動作する。

## What Changes

- 同梱 bundle の読み出し経路を consumer から渡す公開 API を追加する。`okio.FileSystem` / `okio.Path` は公開境界に出さず、`suspend fun read(fileName: String): ByteArray?` だけを持つ関数型インターフェースを公開する（ユーザー確認済み）
- fankt 内部でその関数型インターフェースを read-only な `okio.FileSystem` へアダプトし、`ZiplineLoader.withEmbedded` へ渡す
- 同梱 bundle を必要とする consumer が embedded レイアウトの成果物を生成する手順を README に定める。生成は Zipline が公式提供する `ZiplineDownloadTask` が担い、fankt は変換コードを持たない（ユーザー確認済み）
- 同梱 bundle の署名検証失敗が呼び出し元へ例外として漏れないことをテストで固定する

## Capabilities

### New Capabilities

- `embedded-guest-bundle-access`: 同梱 guest bundle の場所を consumer から受け取る公開 API と、その読み出し経路の失敗の扱い

### Modified Capabilities

- `zipline-guest-bundle-delivery`: 配信された bundle を同梱成果物へ変換する手順と、その成果物が満たすレイアウト規約を要件に加える

## Impact

- `fankt/fanbox/src/clientMain/kotlin/me/matsumo/fankt/fanbox/Fanbox.kt`: guest 用コンストラクタに同梱 bundle の引数を追加する。既存の引数の並びと既定値は変えないため、既存の呼び出しは影響を受けない
- `fankt/fanbox/src/clientMain/kotlin/me/matsumo/fankt/fanbox/guest/FanboxGuestHost.kt`: `EmbeddedGuestBundle` を公開 API 由来の型から組み立てる形に変える
- `fankt/fanbox/api/fanbox.klib.api` と `fankt/fanbox/api/android/fanbox.api`: 公開 API の追加により ABI ダンプが変わる。okio 型が現れないことが検証対象になる
- README: 同梱 bundle の生成手順とリリースごとの差し替え運用
- consumer（PixiView-KMP#138）: 同梱 bundle の配置と読み出し実装。fankt 側の変更だけでは fallback の 2 段目は有効にならない
