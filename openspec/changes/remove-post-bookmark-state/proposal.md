## Why

`FanboxPostDetail.isBookmarked` は FANBOX API の応答ではなく PixiView 固有の状態であり、ライブラリのドメインモデルを可変にしている。FANBOX の事実とアプリ固有状態の所有者を分離し、取得結果を決定的かつ不変にする。

## What Changes

- **BREAKING** `FanboxPostDetail` から可変プロパティ `isBookmarked` を削除する。
- mapper から常時 `false` を設定する処理を削除する。
- ライブラリの fixture / serialization test を新しい不変モデルへ更新する。
- PixiView は BookmarkDataStore 等のアプリ状態と `FanboxPostDetail` を組み合わせる UI モデルを所有する。

## Capabilities

### New Capabilities

- `api-faithful-post-model`: 投稿詳細モデルが FANBOX API 由来の不変な事実だけを保持する契約。

### Modified Capabilities

なし。

## Impact

- 公開 API: `FanboxPostDetail.isBookmarked` の削除は破壊的変更になる。
- 実装: `FanboxPostDetail.kt`、`FanboxPostMapper.kt`、関連 fixture / serialization test。
- ABI: Android/KLIB の API dump を更新する。
- 消費側: PixiView の Bookmark 状態を UI モデル側へ移す連動変更が必要になる。fankt のリリースと PixiView の依存更新を同一の移行単位として扱う。
- in-repo consumer: `composeApp` は `isBookmarked` を参照しておらずコンパイル互換を維持するが、fanbox module と合わせて build 対象に含める。
