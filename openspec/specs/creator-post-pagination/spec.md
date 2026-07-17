# creator-post-pagination Specification

## Purpose

creator 投稿の初回ページ取得で、FANBOX が返す pagination の空状態と cursor 境界を一貫して扱う。

## Requirements

### Requirement: 空の creator pagination を空ページとして返す

ライブラリは creator 投稿の初回取得で `post.paginateCreator` が空配列を返した場合、例外を送出せず、contents が空で cursor が null の `PageCursorInfo` を返さなければならない（SHALL）。この場合、存在しない cursor を合成して `post.listCreator` を呼んではならない（MUST NOT）。

#### Scenario: 投稿 0 件 creator の初回ページを取得する

- **WHEN** `post.paginateCreator` が空配列を返す creator に対して、公開 API の `getCreatorPosts(creatorId, null, null)` を呼ぶ
- **THEN** method は例外を送出せず、`contents = emptyList()`、`cursor = null` のページを返し、`post.listCreator` を呼ばない

### Requirement: creator pagination の limit と取得境界を一致させる

ライブラリは creator 投稿の取得に用いる current cursor が `limit` を持つ場合、その値を `post.listCreator` request の `limit` に使わなければならない（SHALL）。current cursor の `limit` が null の場合に限り、既定値 20 を使わなければならない（SHALL）。

#### Scenario: 初回取得で paginate cursor の limit を使う

- **WHEN** `post.paginateCreator` の先頭 cursor が `limit = 10` を持ち、`getCreatorPosts(creatorId, null, null)` を呼ぶ
- **THEN** `post.listCreator` request は `limit=10` を使い、固定値 20 を使わない

#### Scenario: 継続取得で caller cursor の limit を使う

- **WHEN** caller が `limit = 15` の cursor を渡して creator 投稿を取得する
- **THEN** `post.listCreator` request は `limit=15` を使う

#### Scenario: cursor の limit が欠落する

- **WHEN** current cursor の `limit` が null で creator 投稿を取得する
- **THEN** `post.listCreator` request は既定値 `limit=20` を使う
