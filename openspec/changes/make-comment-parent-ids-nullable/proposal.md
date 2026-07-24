## Why

`post.addComment` はルートコメントにも `rootCommentId` と `parentCommentId` を必須としており、呼び出し側が FANBOX API 上に存在しない番兵値 `"0"` を補う必要がある。API の事実をそのまま表現し、ルート投稿と返信投稿の要求を区別できる公開 API にする。

## What Changes

- **BREAKING** `Fanbox.addComment` と内部 repository の `rootCommentId` / `parentCommentId` を nullable にする。
- 両 ID が `null` のルートコメントでは、対応する JSON プロパティをリクエストから省略する。
- 両 ID が指定された返信では、従来どおり両プロパティを送信する。
- ルートコメントと返信コメントのリクエスト形状を自動テストで固定し、FANBOX 実環境でも双方を確認する。

## Capabilities

### New Capabilities

- `comment-submission`: ルートコメントと返信コメントを FANBOX API のリクエスト形状に合わせて投稿する契約。

### Modified Capabilities

なし。

## Impact

- 公開 API: `Fanbox.addComment` の引数型が変わるため、呼び出し側はルート投稿時に `null` を渡すよう移行する。
- 実装: `Fanbox.kt`、`FanboxPostRepository.kt`、コメント投稿リクエストのテスト。
- ABI: Android/KLIB の API dump を更新する。
- 外部確認: FANBOX 実環境でルート投稿と返信投稿を確認する。
- in-repo consumer: `composeApp` はコンパイル互換を維持し、reflection ベース手動リクエスト画面でルート投稿を再確認する。
- 後続変更: `migrate-domain-ids-to-value-classes` が `FanboxCommentId.EMPTY` を削除する前提になる。
