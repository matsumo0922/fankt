## 1. 前提条件と消費側の棚卸し

- [ ] 1.1 `make-comment-parent-ids-nullable` が適用済みで、PixiView のルートコメント投稿が `FanboxCommentId.EMPTY` に依存しないことを確認する
- [ ] 1.2 PixiView で 7 ID 型の `uniqueValue`、`EMPTY`、`copy`、destructuring、暗黙 `toString()` の利用箇所を特定する
- [ ] 1.3 PixiView で ID を含む Navigation 引数と永続データを列挙し、各保存形式の migration または invalidation 方針を決定する
- [ ] 1.4 PixiView の LazyColumn 等に、API ID と表示文脈から構築する安定 key を実装する連動 PR を準備する
- [ ] 1.5 in-repo consumer の `composeApp` で reflection による ID 引数付き API の呼び出し経路を確認する

## 2. ID 型の value class 化

- [ ] 2.1 String 基底の `FanboxCommentId`、`FanboxPostId`、`FanboxPostItemId`、`FanboxNewsLetterId`、`FanboxCreatorId`、`FanboxPlanId` を `@Serializable @JvmInline value class` に変更する
- [ ] 2.2 Long 基底の `FanboxUserId` を `@Serializable @JvmInline value class` に変更する
- [ ] 2.3 4 ID 型から `uniqueValue` と UUID 依存を削除し、全 ID 型から独自 `toString()` を削除する
- [ ] 2.4 `FanboxCommentId.EMPTY` を削除する

## 3. ID 利用箇所の移行

- [ ] 3.1 `FanboxPostRepository` の query、body、URL 用 ID 参照を `.value` に変更する
- [ ] 3.2 `FanboxCreatorRepository` の user ID 参照を `userId.value.toString()` に変更し、follow / unfollow の `creatorUserId` を既存どおり JSON string で送信する
- [ ] 3.3 `FanboxPostDetail`、`FanboxCreatorPlan`、`FanboxCreatorDetail` の URL 文字列化を `${id.value}` 形式へ変更する
- [ ] 3.4 fankt 全体で対象 ID の `toString()`、暗黙 interpolation、`uniqueValue`、`EMPTY` を検索し、意図しない残存がないことを確認する

## 4. Serialization と ABI の検証

- [ ] 4.1 String 基底 ID が JSON string、`FanboxUserId` が JSON number になる serialization test を追加する
- [ ] 4.2 7 ID 型の primitive encode/decode round-trip test を追加し、既存 model serialization test を更新する
- [ ] 4.3 follow / unfollow の request body で `creatorUserId` が JSON string のまま維持される MockEngine test を追加する
- [ ] 4.4 Android/KLIB の API dump を再生成し、`copy`、`component1`、`uniqueValue`、`EMPTY` が消える差分を確認する
- [ ] 4.5 value class の JVM 名マングリングを含む custom ABI 検証、fanbox module unit test、detekt を実行する
- [ ] 4.6 `composeApp` の reflection ベース手動リクエスト画面から ID 引数を持つ API を実行し、runtime compatibility を確認する

## 5. 連動移行とドキュメント

- [ ] 5.1 PixiView の保存済み ID データに決定済みの migration または invalidation を実装し、旧形式と新形式の境界を検証する
- [ ] 5.2 PixiView を新しい fankt version で build し、Navigation 復元、Bookmark、コメント投稿、リスト key を実機確認する
- [ ] 5.3 README と docs を 7 ID 型、`uniqueValue`、`EMPTY`、serialization で検索し、影響する記述を現在の仕様へ更新する
- [ ] 5.4 fankt と PixiView の連動 PR、適用順、ロールバック時のデータ破棄条件を変更の検証記録へ残す
