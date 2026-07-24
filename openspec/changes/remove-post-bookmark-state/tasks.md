## 1. 消費側の移行準備

- [ ] 1.1 PixiView 全体で `isBookmarked` の読み書き、Navigation serialization、BookmarkDataStore との連携箇所を特定する
- [ ] 1.2 PixiView で `FanboxPostDetail` とアプリ所有の Bookmark 状態を合成する UI model と更新経路を実装する連動 PR を準備する
- [ ] 1.3 連動 PR で Bookmark 表示・追加・解除と process recreation 後の復元方針を検証する

## 2. ドメインモデルの純化

- [x] 2.1 `FanboxPostDetail` から `isBookmarked` を削除し、公開プロパティが不変であることを確認する
- [x] 2.2 `FanboxPostMapper` から `isBookmarked = false` の設定を削除する
- [x] 2.3 `FanboxPostMapperGoldenTest` の fixture と `FanboxTimestampSerializationTest` を含む serialization test から Bookmark 状態を削除する

## 3. 互換性と検証

- [x] 3.1 Android/KLIB の API dump を再生成し、`isBookmarked` の削除差分を確認する
- [x] 3.2 fanbox module の unit test、公開 API 検証、detekt を実行する
- [x] 3.3 README と docs を `isBookmarked`、`FanboxPostDetail`、Bookmark で検索し、影響する記述を現在の仕様へ更新する
- [ ] 3.4 fankt のリリースと PixiView の依存更新順を連動 PR に記録し、旧 PixiView が破壊的版を参照しないことを確認する
