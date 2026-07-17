## Why

`post.info` は本文形式を `post.type` で明示するが、現在の mapper は本文内の非空フィールドから形式を推測するため、複数フィールドが共存した場合の優先順位が暗黙的である。未対応または未知の type では本文 payload も失われるため、明示 type を正本にしつつ将来のフォールバックに必要な raw body を保持する。

## What Changes

- `article`、`image`、`file` を `post.type` に基づいて対応する既知の `Body` variant へ map する。
- `text`、`video`、`entry`、および未知の type を、type と raw body JSON を保持する `Body.Unknown` へ map し、投稿詳細全体は返す。
- **BREAKING**: `Body.Unknown` を `data object` から `data class Unknown(val type: String, val rawBodyJson: String?)` へ変更する。
- 6 type の fixture test を追加・更新する。`video` と `entry` は実レスポンスを直ちに用意できないため synthetic fixture とし、保証範囲を PR に明記する。（ユーザー確認済み）
- PixiView-KMP の追従変更は本 change に含めず、別対応とする。（ユーザー確認済み）

## Capabilities

### New Capabilities

- `post-body-mapping`: `post.type` による本文 variant 選択と、未対応・未知 type の raw-preserving fallback 契約。

### Modified Capabilities

なし。

## Impact

- `:fankt:fanbox` の `FanboxPostDetailEntity`、`FanboxPostMapper`、公開 domain model `FanboxPostDetail.Body.Unknown`。
- `post.info` の golden fixture と mapper test。
- 公開 sealed subtype の constructor 変更により、PixiView-KMP を含む consumer は別途追従が必要である。旧 `Unknown` の serialized value は default 値で読み戻せる互換経路を維持する。
- 外部依存の追加、network endpoint、内部DB、認証、server deployment migration への変更はない。
