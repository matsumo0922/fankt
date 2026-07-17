## Why

`getCreatorPosts` は投稿が 0 件の creator に対して空の pagination の先頭を参照し、空ページを返すべき通常状態を例外へ変えている。初回取得の `limit` も paginate cursor と独立した固定値のため、FANBOX のページ境界と取得件数を一致させる必要がある。

## What Changes

- 空の creator pagination を受けた初回取得は `post.listCreator` を呼ばず、`contents = emptyList()`、`cursor = null` のページを返す。
- 初回取得で pagination が存在する場合は、先頭 cursor の `limit` を `post.listCreator` の `limit` に使う。
- 空 pagination と cursor 由来 limit を、公開 `Fanbox.getCreatorPosts` から production HTTP 経路を通す fixture test で固定する。

## Capabilities

### New Capabilities

- `creator-post-pagination`: creator 投稿の初回ページ取得で、pagination の空状態と cursor 境界を一貫して扱う振る舞い。

### Modified Capabilities

なし。

## Impact

- `fankt/fanbox` の `FanboxPostRepository` と production-path fixture test が対象になる。
- 公開 API signature と型は変更しない。投稿 0 件で発生していた `NoSuchElementException` を空の `PageCursorInfo` に置き換える。
- （ユーザー確認済み）issue #25 の受け入れ条件と「やること」3項目を本 change の契約とする。
- （ユーザー確認済み）本 change は fankt 単体の契約に限定する。PixiView は pagination を先に直接取得するため、本修正だけでは同アプリの空 cursor error を解消しない。PixiView 側の空 cursor 処理は別 Issue / change に stage-out する。
