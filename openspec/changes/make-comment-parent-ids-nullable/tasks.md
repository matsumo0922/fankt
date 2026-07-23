## 1. リクエスト契約のテスト

- [ ] 1.1 コメント投稿 repository の既存テスト構成を確認し、送信 JSON を検査できる MockEngine ベースのテストを用意する
- [ ] 1.2 両親 ID が null のとき `rootCommentId` と `parentCommentId` が JSON に存在しないテストを追加する
- [ ] 1.3 両親 ID が non-null のとき両 API 値が JSON に含まれるテストを追加する

## 2. Nullable API の実装

- [ ] 2.1 `Fanbox.addComment` の `rootCommentId` と `parentCommentId` を nullable にする
- [ ] 2.2 `FanboxPostRepository.addComment` まで nullable 値を伝播し、non-null のプロパティだけを JSON に追加する
- [ ] 2.3 PixiView のルートコメント呼び出しを `FanboxCommentId.EMPTY` から両 ID の null 指定へ移行する連動 PR を準備する

## 3. 互換性と自動検証

- [ ] 3.1 Android/KLIB の API dump を再生成し、nullable な公開署名の差分を確認する
- [ ] 3.2 fanbox module の unit test、公開 API 検証、detekt を実行する
- [ ] 3.3 README と docs を `addComment`、`rootCommentId`、`parentCommentId` で検索し、影響する記述を現在の仕様へ更新する
- [ ] 3.4 `composeApp` を build し、reflection ベース手動リクエスト画面から親 ID なしのルート投稿を実行できることを確認する

## 4. FANBOX 実環境確認

- [ ] 4.1 認証済みのテスト用投稿へ親 ID なしでルートコメントを投稿し、作成結果を取得できることを確認する
- [ ] 4.2 作成したルートコメントへ両親 ID を指定して返信し、作成結果を取得できることを確認する
- [ ] 4.3 実環境確認結果と PixiView 連動 PR を変更の検証記録へ残す
