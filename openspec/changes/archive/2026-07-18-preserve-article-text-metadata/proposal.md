## Why

article の `p` / `header` block は現状どちらも平文の `Text` に縮退し、styles、inline links、見出し種別、空段落が失われる。Issue #30 の受け入れ条件と保存済み actual response evidence に従い、描画方針を library に持ち込まず、downstream が本文構造を再現できる公開値として保持する。

## What Changes

- transport `Block` が nullable `styles` と `links` span を decode する
- 公開 `Article.Block.Text` が style span、link span、header flag を互換 default 付きで保持する
- `header` と `p` を同じ Text value shape のまま区別し、未知 style type と受信 offset / length / URL を変更せず保持する
- 空文字の `p` block を空段落として順序どおり返す
- actual-response-derived な匿名化 fixture と公開 `Fanbox.getPostDetail` 経路で header、bold、inline link、空段落を検証する
- **BREAKING**: public `Text` の serialized shape と constructor surface に field を追加する

## Capabilities

### New Capabilities

- `article-text-blocks`: article の paragraph / header text と style / link span、空段落を公開 model に保持する契約

### Modified Capabilities

- なし

## Impact

- `FanboxPostDetailEntity.Body.PostBody.Block` と span entity
- `FanboxPostDetail.Body.Article.Block.Text` と公開 span value
- `FanboxPostMapper` の `p` / `header` branch
- FANBOX golden fixture、mapper / serialization / public production-path tests
- downstream consumer は `isHeader` と span range から heading / AnnotatedString を構築できる。PixiView の描画変更は別 issue とし、この change には含めない
