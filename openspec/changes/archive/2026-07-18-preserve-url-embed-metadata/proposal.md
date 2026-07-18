## Why

`post.info` の article `url_embed` block は参照先の `urlEmbedMap` entry を解決しているが、transport model が `url` を decode せず、公開 `Link` も type と URL を保持しないため、プレーンリンクやカードリンクが情報欠落または schema error になる。Issue #29 の受け入れ条件に従い、既存の匿名化済み fixture と保存済み actual response evidence を使って URL embed metadata を欠落なく公開する。

## What Changes

- `urlEmbedMap` entry の `type` / `url` / `html` / `postInfo` を nullable field の欠落を許容しながら decode する
- 公開 `Article.Block.Link` が type、URL、HTML、FANBOX post を保持する
- `default`、`html.card`、`fanbox.post` の意味と HTML trust boundary を KDoc で公開する
- actual-response-derived な匿名化 fixture で各 type の field preservation と公開 `Fanbox.getPostDetail` 経路を検証する
- **BREAKING**: public `Link` の serialized shape と constructor surface に field を追加する

## Capabilities

### New Capabilities

- `article-url-embeds`: article の `url_embed` 参照を type-directed な公開 Link metadata として保持する契約

### Modified Capabilities

- なし

## Impact

- `FanboxPostDetailEntity.Body.PostBody.Url`
- `FanboxPostDetail.Body.Article.Block.Link`
- `FanboxPostMapper` の `url_embed` branch
- FANBOX golden fixture、mapper / serialization / public production-path tests
- downstream consumer は Link type に応じて post card、external URL、sanitized HTML、fallback を描画できる。PixiView の UI 変更は別 issue とし、この change には含めない
