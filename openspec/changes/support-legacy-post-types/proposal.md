## Why

FANBOX の `text` / `video` / `entry` 投稿は本文 payload が返っていても現在は `Body.Unknown` へ落ち、consumer が型安全に表示できない。Issue #27 の3形式を公開 variant として返し、有料・旧形式投稿の本文欠落を解消する。

## What Changes

- `post.type` が `text` の本文を `Body.Text(text)` に変換する。
- `post.type` が `video` の `body.video` を `Body.Video(serviceProvider, videoId)` に変換し、YouTube / Vimeo URL を決定的に復元する。
- `post.type` が `entry` の `body.html` を、信頼できない HTML であることを公開 KDoc に示した `Body.Html(html)` に変換する。
- **BREAKING**: 公開 sealed `FanboxPostDetail.Body` に3 subtypeを追加するため、exhaustive `when` を持つ consumer は追従が必要になる。PixiView-KMP の描画対応は別 issue の責務とする。
- （ユーザー確認済み）Issue の「実レスポンス由来 fixture」は3形式の完全な実responseを各1件要求せず、保存済み実 `post.info` response と既存の actual-response-derived fixture を envelope / field source とし、未取得の type 固有部分だけを合成した hybrid fixture で満たす。fixture と PR では実測部分・合成部分・未検証の本番 schema 互換性を区別する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `post-body-mapping`: `text` / `video` / `entry` を raw fallback ではなく専用公開 variant へ map し、video URL と HTML trust boundary を定義する。

## Impact

- `FanboxPostDetailEntity.Body.PostBody`、`FanboxPostMapper`、公開 `FanboxPostDetail.Body` とその exhaustive helper を変更する。
- `post.info` の golden / production-call-path test と serialization test、fixture contributor contract の provenance 説明を更新する。
- fankt の公開 sealed API を利用する PixiView-KMP などの consumer は、新しい subtype の表示または fallback を別対応で追加する必要がある。
