## Context

`FanboxCommentId`、`FanboxPostId`、`FanboxPostItemId`、`FanboxNewsLetterId`、`FanboxUserId`、`FanboxCreatorId`、`FanboxPlanId` は、String または Long を 1 つだけ保持する `data class` として公開されている。4 型は構築時に `Uuid.random()` を呼ぶ `uniqueValue` を持ち、`FanboxCommentId` はルートコメント用の `EMPTY` を持つ。いずれも FANBOX API の ID そのものではなく、消費アプリや既存 API 形状の都合である。

value class 化は data class 由来 ABI と Kotlin serialization の wire shape を変更する。特に PixiView は model を Navigation 引数や永続データに使用するため、ライブラリ内のコンパイル成功だけでは移行完了にならない。

## Goals / Non-Goals

**Goals:**

- 7 種の ID 型を API 値だけを保持する `@JvmInline value class` に統一する。
- ID 構築から乱数生成を排除し、等しい API 値から等しい決定的な ID を得る。
- `FanboxCommentId.EMPTY` を削除し、ルートコメントを nullable な親 ID で表現する。
- ライブラリ内部の ID 値参照を `.value` に統一する。
- primitive serialization と公開 ABI をテスト・dump で明示する。

**Non-Goals:**

- 旧オブジェクト形式の serialization をカスタム serializer で維持しない。
- PixiView の LazyColumn key、Navigation、DataStore の具体的な移行実装を fankt に追加しない。
- ID の validation 規則や正規化を新設しない。
- コメント投稿の nullable 化自体はこの変更に含めない。

## Decisions

### 1. 7 種すべてを `@Serializable @JvmInline value class` にする（ユーザー確認済み）

ID ごとに型安全性を保ちつつ、基底値は現行どおり String または Long とする。typealias は ABI 上の区別を失い、通常 class は不要な allocation と data class API を残すため採用しない。

### 2. `uniqueValue` を削除し、UI key を消費側の責務にする（ユーザー確認済み）

ID は API 値だけで equality と hashCode が決まる。リスト内で同じ ID を持つ異種要素を区別する必要がある場合、PixiView が要素種別や位置など表示文脈を含む key を構築する。ライブラリは ID 生成時に乱数を作らない。

### 3. `FanboxCommentId.EMPTY` は先行変更後に削除する（ユーザー確認済み）

`make-comment-parent-ids-nullable` によってルートコメントを null で表現できることを前提とする。`EMPTY` を value class の companion object に残す方法は番兵値の契約を温存するため採用しない。

### 4. ID の利用箇所は `.value` を明示し、既存の wire 型を維持する（agent 仮決め）

query parameter、JSON body、URL などに基底値を渡す箇所は `.value` を起点にする。独自の `toString()` override は持たせず、暗黙の文字列化に依存しない。String interpolation が必要な URL では `${id.value}` を使用する。

ただし `follow.create` / `follow.delete` の `creatorUserId` は現在 JSON string として送信される。`FanboxUserId.value` をそのまま `put` すると JSON number に変わるため、ここだけは `userId.value.toString()` を使用して既存 wire 型を維持する。ID serializer の primitive 形状変更と、手組み API request body の wire 形状変更を混同しない。

### 5. primitive serialization を新しい契約にする（高リスク・要人間確認）

String ID は JSON string、`FanboxUserId` は JSON number として serialize する。旧 `{"value": ...}` 形式を読む互換 serializer は導入しない。v0.1.0 の破壊的変更として消費側が保存済みデータの migration または invalidation を選択する。選択が確定するまで破壊的版を PixiView へリリースしない。

### 6. ABI と消費側互換性を別々に検証する（agent 仮決め）

fankt では Android/KLIB API dump を再生成し、value class のマングルされた公開署名を既存検証が扱えることを確認する。PixiView では compile、Navigation 復元、永続データ、リスト key の動作を連動 PR で確認する。in-repo consumer の `composeApp` では reflection による public suspend 関数の列挙と value class 引数の `callSuspend` を手動実行し、runtime compatibility を確認する。

## Risks / Trade-offs

- [serialization 形式の変更で保存済みデータを decode できない] → PixiView 側で対象データを列挙し、明示的 migration または安全な invalidation をリリース前に実装する。
- [Navigation の保存状態復元が失敗する] → process recreation を含むテストを行い、旧 schema の状態を復元しない場合は version 境界で破棄する。
- [value class の JVM 名マングリングを ABI 検証が誤判定する] → API dump 再生成後に custom ABI 検証を実行し、検証器の問題は ID 実装を歪めず別修正として扱う。
- [uniqueValue 削除で LazyColumn key が衝突する] → PixiView 側で現在の利用箇所を全検索し、API ID と表示文脈から安定 key を構築する。
- [先行変更なしで `EMPTY` を削除するとルート投稿手段がなくなる] → `make-comment-parent-ids-nullable` の適用・リリースを明示的な前提条件にする。
- [data class の `copy` / destructuring 利用が消費側に残る] → PixiView と既知の消費側で 7 ID 型の利用を検索し、基底値またはコンストラクタを使うコードへ移行する。
- [`FanboxUserId.value` の Long を JSON body に直接入れて follow API の wire 型が変わる] → `creatorUserId` は `userId.value.toString()` で JSON string を維持し、follow / unfollow の request body test で固定する。
- [`composeApp` の reflection 呼び出しが value class 引数で実行時失敗する] → compile だけで完了とせず、手動リクエスト画面から ID 引数を持つ API を実行する。

## Migration Plan

1. `make-comment-parent-ids-nullable` を適用し、PixiView のルートコメント投稿から `EMPTY` 依存を除去する。
2. PixiView で `uniqueValue`、`EMPTY`、ID の `copy` / destructuring、serialization 利用箇所を棚卸しする。
3. fankt の 7 ID 型を value class 化し、`uniqueValue`、`EMPTY`、独自 `toString()` を削除する。
4. repository と model の ID 利用を `.value` に変更する。
5. primitive serialization のテストを追加し、既存 model の round-trip test を更新する。
6. Android/KLIB API dump を再生成し、unit test と ABI 検証を実行する。
7. PixiView で安定 UI key と保存済みデータの migration / invalidation を実装し、新しい fankt version で compile と実機動作を確認する。
8. ロールバック時は fankt と PixiView の version を同時に戻す。新形式で保存されたデータが旧版で読めない場合は対象キャッシュを破棄する。

## Open Questions

- PixiView の各永続データについて、旧 ID object 形式から primitive 形式へ migration するか、version 境界で invalidation するかは消費側設計で確定する。
