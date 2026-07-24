## Why

FANBOX の ID 型は単一値を包む `data class` である一方、一部に UI key 用の非決定的な `uniqueValue` やルートコメント用の番兵値 `EMPTY` が混在している。ID を API の識別値だけを表す軽量で決定的な型に統一し、アプリ都合をドメインモデルから排除する。

## What Changes

- **BREAKING** 7 種の ID 型を `@JvmInline value class` に変更する。
- **BREAKING** `FanboxCommentId`、`FanboxPostId`、`FanboxPostItemId`、`FanboxNewsLetterId` から非決定的な `uniqueValue` を削除する。
- **BREAKING** `FanboxCommentId.EMPTY` を削除する。ルートコメントは先行変更 `make-comment-parent-ids-nullable` の nullable 引数で表現する。
- ID の文字列化・数値化を `.value` 参照へ統一し、ID 型独自の `toString()` 依存をなくす。
- value class の primitive serialization 形状をテストで固定する。
- Android/KLIB の ABI dump を再生成し、value class の公開署名を検証する。

## Capabilities

### New Capabilities

- `domain-id-types`: FANBOX の ID が単一の API 値だけを保持し、決定的な equality と primitive serialization を提供する契約。

### Modified Capabilities

なし。

## Impact

- 公開 API / ABI: data class 由来の `copy` / `component1`、`uniqueValue`、`EMPTY`、従来の JVM 署名が削除・変更される。
- serialization: ID の JSON 表現がオブジェクトから primitive に変わり、保存済みデータや Navigation 引数と非互換になる可能性がある。
- 実装: `domain/model/id` 配下の 7 型、各 repository とモデルの ID 値参照、serialization test、API dump。
- wire compatibility: `FanboxUserId.value` は Long だが、`follow.create` / `follow.delete` の `creatorUserId` は既存どおり JSON string を維持する。
- 消費側: PixiView の LazyColumn key、Navigation、BookmarkDataStore 等に対する連動移行が必要になる。
- in-repo consumer: `composeApp` の reflection ベース手動リクエスト画面が value class 引数を実行時に呼び出せることを再確認する。
- 依存関係: `make-comment-parent-ids-nullable` の完了後に適用する。
