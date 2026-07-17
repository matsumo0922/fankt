## 1. Creator 投稿ページング

- [x] 1.1 空の pagination を mismatch のない空 `PageCursorInfo` として返し、`post.listCreator` を呼ばない
- [x] 1.2 初回・継続取得で選択した current cursor の `limit` を request に使い、null の場合だけ 20 へ fallback する

## 2. Production-path fixture tests

- [x] 2.1 既存の空 `post.paginateCreator` fixture を再利用し、公開 `Fanbox.getCreatorPosts` が空ページを返す test を追加する
- [x] 2.2 paginate cursor、caller cursor、null limit の各 request query を production HTTP 経路で検証する

## 3. 検証とドキュメント

- [ ] 3.1 full test・lint・build を validation lease と分離した `GRADLE_USER_HOME` で実行する
- [x] 3.2 `getCreatorPosts`、`PageCursorInfo`、`FanboxCursor` を README と docs で検索し、現在仕様に反する記述がないことを確認する
