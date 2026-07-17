## Why

article 投稿の `embed` block は現在 entity と mapper のどちらにも表現がなく、Twitter、YouTube、Vimeo、SoundCloud、Google Forms、FANBOX 投稿の埋め込みが本文から消える。Issue #28 の受け入れ条件を満たし、未知 provider や未知 block も黙って欠落させない公開モデルが必要である。

## What Changes

- article body の `embedMap` と block の `embedId` を decode し、既知 provider を公開 `Article.Block.Embed` へ変換する。
- `Embed` に provider 別 URL 復元 helper を追加する。
- 未知 provider と未知 block type を raw JSON を持つ公開 `Article.Block.Unknown` へ変換する。
- 主要 provider、未知 provider、未知 block の synthetic fixture と、公開 `Fanbox.getPostDetail` を通る production-path test を追加する。（ユーザー確認済み）
- synthetic fixture は内部 decode・参照解決・URL 復元・fallback 契約だけを保証し、FANBOX 本番の `embedMap` schema 互換性は保証しない。（ユーザー確認済み）
- **BREAKING**: 公開 sealed interface `Article.Block` に `Embed` と `Unknown` subtype を追加するため、exhaustive `when` を持つ consumer は追従が必要になる。

## Capabilities

### New Capabilities

- `article-embed-blocks`: article の embed 参照解決、provider 別 URL 復元、未知 provider・block の raw fallback を規定する。

### Modified Capabilities

- `post-body-mapping`: article block の type を分岐の正本とし、未知 block を欠落させない要件を追加する。

## Impact

- `fankt/fanbox` の post detail entity、mapper、公開 `FanboxPostDetail.Body.Article.Block`、serialization contract、fixture test が対象になる。
- 公開 sealed subtype の追加は source compatibility に影響し、PixiView-KMP を含む exhaustive consumer は dependency 更新時に追従が必要になる。
- 実 API、DB、migration、認証・permission boundary は変更しない。
