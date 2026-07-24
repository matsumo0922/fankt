## ADDED Requirements

### Requirement: 投稿詳細モデルは API の事実だけを保持する
`FanboxPostDetail` は FANBOX API 応答から得られる投稿詳細だけを保持し、消費アプリ固有の Bookmark 状態を公開プロパティとして保持してはならない（MUST NOT）。

#### Scenario: API 応答を投稿詳細へ mapping する
- **WHEN** 同一の FANBOX API 投稿詳細応答を mapping する
- **THEN** ライブラリはアプリの Bookmark 状態に依存しない等価な `FanboxPostDetail` を生成する

### Requirement: 投稿詳細モデルは不変である
`FanboxPostDetail` の公開状態は構築後に変更できてはならない（MUST NOT）。

#### Scenario: 投稿詳細を消費側へ公開する
- **WHEN** 呼び出し側が取得済みの `FanboxPostDetail` を参照する
- **THEN** 呼び出し側はモデルの公開プロパティを再代入できない

### Requirement: Bookmark 状態は消費側が合成する
ライブラリは Bookmark 状態の保存・取得・更新を `FanboxPostDetail` の責務にしてはならない（MUST NOT）。

#### Scenario: アプリが Bookmark 表示を構築する
- **WHEN** 消費アプリが投稿詳細と Bookmark 状態を同じ画面に表示する
- **THEN** 消費アプリは `FanboxPostDetail` とアプリ所有の Bookmark 状態をライブラリ外で合成する
