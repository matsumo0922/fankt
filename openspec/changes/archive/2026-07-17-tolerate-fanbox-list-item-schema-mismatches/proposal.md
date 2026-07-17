## Why

FANBOX のリスト系レスポンスは、1 件の要素が想定スキーマから外れるだけでレスポンス全体の decode が失敗する。非公式 API wrapper として利用可能な正常要素を返し続け、個別要素の不一致を診断できる境界が必要である。

## What Changes

- 対象 endpoint の配列要素を `JsonElement` として受け、共通 helper で 1 件ずつ decode・map する。
- 個別要素の decode または schema 由来の map が失敗した場合、その要素だけを skip し、endpoint と 0-based index path を公開 per-call callback で通知できる overload を追加する。
- Napier には endpoint と index path を常に記録し、明示的に logging を有効化した場合だけ sanitized・bounded raw JSON fragment を追加する。
- `bell.list` の mapper から `!!` を除去し、既知 type の必須フィールド欠落も個別要素の mismatch として skip する。
- 既存 method と return type は変更せず、skip callback の公開 API を additive に追加する。`plan.listSupporting` の既存 no-callback method は strict failure を維持し、callback overload だけが部分成功を返す。pagination metadata は従来どおり維持する。
- `post.listHome` / `post.listSupporting` / `post.listCreator` / `bell.list` / `post.getComments` / `creator.listFollowing` / `plan.listCreator` / `plan.listSupporting` を対象にする。共有 entity を使う `creator.listPixiv` と `creator.listRecommended` も同じ安全な decode 経路を通す。

## Capabilities

### New Capabilities

- `tolerant-list-decoding`: FANBOX のリスト要素を個別に decode し、正常要素を保持しながら不一致を診断する振る舞い。

### Modified Capabilities

なし。

## Impact

- `fankt/fanbox` の list response entity、mapper、repository wiring、production-path test が対象になる。
- 公開 `FanboxListItemSchemaMismatch` と per-call callback overload を additive に追加する。既存 method signature と dependency に破壊的変更はない。
- OpenSpec 未導入だったため、（ユーザー確認済み）この change で OpenSpec の project files を導入する。
