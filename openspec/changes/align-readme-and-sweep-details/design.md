## Context

Issue #41 は Phase 4 の整理項目として 2026-07-15 に作成された。その後 Phase 2（#26–#32）と Phase 3（#33–#39）が完了し、issue 記載の前提の一部が失効している。実装前にコードと突き合わせた結果、以下が確認された。

- `setFanboxSessionId` は README の全 3 箇所で既に正しい名称になっている。
- `ClientBuilder` の冗長な status 判定は #17 / #32 の書き換えで消滅し、現在は `HttpStatusCode.isSuccess()` 判定である。
- `creatorId.toString()` は #37 の value class 化で解消済みで、repository 層に残存しない。
- Phase 2 完了により text / video / entry / embed は対応済みであり、issue が指示する「非対応と書く」修正は現在では不正確である。
- `kotlin-reflect` は `composeApp` の `FanboxRequestsContent.kt` が `memberFunctions` / `callSuspend` で実際に使用しており、bundle から削除するだけではコンパイルが壊れる。
- `kotlinxCoroutines = "1.7.3"` の実解決値は 1.10.2 であり、宣言だけが実態と乖離している。

## Goals / Non-Goals

**Goals:**

- README の Status 節が現在の対応範囲と既知の未対応点を正しく示す。
- 通知一覧の取得が未読状態を変更するかを呼び出し側が決められる。
- version catalog の宣言が実際の依存構成と一致する。
- 本番 JSON formatter から不要な整形設定を除く。

**Non-Goals:**

- Cloudflare challenge の検知・分類を実装しない（Issue #18 の範囲）。
- ライセンスを変更しない。CC BY-NC 4.0 を維持し、判断結果だけを記録する。
- Fantia の記述と公開方針を変更しない（Issue #40 で解決済み）。
- 既存の `getBells(page)` overload を削除しない。

## Decisions

### 1. Status 節は対応範囲と未対応点を並記する（ユーザー確認済み）

「All features are fully functional」を、対応済みの投稿形式・未知 type の保持挙動・Cloudflare challenge 未検知の 3 点に置き換える。issue が指示した「text/video/entry 非対応と書く」は Phase 2 完了により事実に反するため採用しない。「fully functional」を単に削るだけでは、利用者が遮断時の挙動を判断する材料が残らない。

### 2. `getBells` は既読変換を制御する overload を追加し、既定を「変換しない」にする（ユーザー確認済み）

FANBOX の `bell.list` は `skipConvertUnreadNotification` を受け取り、現在の実装は常に `0`（変換する）を送っている。一覧を読むだけの呼び出しが未読状態を破壊するため、既定値を「変換しない」へ改める。

既存の `getBells(page)` と `getBells(page, onItemSchemaMismatch)` は残し、`markNotificationsRead: Boolean = false` を持つ形へ既定引数で拡張する。Kotlin の既定引数は JVM で overload を生成しないため、ABI dump には `Boolean` を伴う新しい signature と既定値解決用の synthetic signature が現れる。source 互換は保たれ、既存呼び出しの実行時挙動だけが「既読化しない」へ変わる。

この既定値変更は破壊的である。README には記載せず、通知の既読化を意図する呼び出し側が明示的に `true` を渡す必要がある点を KDoc に記す。

### 3. load size は `Int` を正本とし、query 生成時に文字列化する（ユーザー確認済み）

`FanboxEndpoints` は internal であり公開 API に影響しない。`DEFAULT_LOAD_SIZE` を `Int` にし、`postComments` と `recommendedCreators` の `loadSize` 引数も `Int` にする。`RequestDescriptor` の query は文字列を保持する契約のため、変換は query 構築の直前で行う。

### 4. `kotlin-reflect` は bundle から外し利用者へ直接宣言する（ユーザー確認済み）

`infra-api` bundle の利用者は `composeApp` と `fankt/fantia` の 2 つで、`fankt/fanbox` は使用しない。実際に reflection API を呼ぶのは `composeApp` だけであるため、bundle から外して `composeApp` に `libs.kotlin.reflect` を直接宣言する。`fankt/fantia` は reflection を使用しておらず、宣言を追加しない。alias 定義自体は残す。

### 5. coroutines は宣言値を実解決値へ合わせる（agent 仮決め）

`kotlinxCoroutines` を 1.10.2 にする。Kotlin 2.2.10 の strictly 制約によって実際の解決結果は既に 1.10.2 であり、この変更は解決結果を変えず宣言の乖離だけを解消する。

## Risks / Trade-offs

- `getBells` の既定値変更は、通知一覧の表示をもって既読化していた呼び出し側の挙動を変える。PixiView が一覧取得の副作用に依存している場合、既読化されなくなる。fankt は API wrapper であり、副作用を暗黙の既定にしない方が呼び出し側で制御しやすいため、既定値変更を選ぶ。PixiView 側の対応要否は fankt 更新時に確認する。
- `prettyPrint` の削除は encode 結果の文字列表現を変える。production の encode 経路は現状存在せず、既存テストは `parseToJsonElement` を経由して構造で検証しているため影響しない。

## Migration Plan

段階移行は不要。`getBells` の既定値変更は同一 release に含め、ABI dump を同じ変更で更新する。

## Open Questions

なし。
