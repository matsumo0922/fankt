## Why

README の Status 節が pixivFANBOX を「All features are fully functional」と記載しているが、Cloudflare challenge の検知・分類（Issue #18）は未実装であり、利用者は遮断時の挙動を事前に把握できない。また通知一覧の取得が未読ベルを既読へ変換する副作用を持ち、呼び出し側がこれを制御できない。あわせて version catalog と JSON formatter に、実態と乖離した宣言および本番不要な設定が残っている。

## What Changes

- README の Status 節を現在の対応範囲に合わせ、未知 type を保持する挙動と Cloudflare challenge 未検知を明記する。
- `Fanbox.getBells` に既読変換の制御を公開し、既定を「既読化しない」にする。
- `createFanboxJson` から `prettyPrint` を削除する。
- `FanboxEndpoints` の load size 定数と loadSize 引数を `Int` にし、query 生成時に文字列化する。
- `infra-api` bundle から `kotlin-reflect` を外し、実利用者である `composeApp` へ直接宣言する。
- `kotlinxCoroutines` の宣言値を実解決値へ合わせ、`ktot-logging` の alias typo を修正する。

## Capabilities

### New Capabilities

- `notification-read-state`: 通知一覧の取得が未読状態を変更するかを呼び出し側が制御できる契約。

### Modified Capabilities

なし。

## Impact

- 公開 API: `Fanbox.getBells` に overload を追加し、ABI dump を更新する。既定の未読変換挙動が変わる。
- ドキュメント: `README.md` の Status 節と License 節。
- ビルド設定: `gradle/libs.versions.toml`、`composeApp/build.gradle.kts`。
- Issue #41 のうち `setFanboxSessionId` 表記、`ClientBuilder` の冗長判定、`creatorId.toString()` は先行 issue で解消済みのため対象外とする。ライセンスは CC BY-NC 4.0 を維持する判断を README に記録する。
