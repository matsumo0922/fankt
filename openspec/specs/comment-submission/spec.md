# comment-submission Specification

## Purpose
TBD - created by archiving change make-comment-parent-ids-nullable. Update Purpose after archive.
## Requirements
### Requirement: ルートコメント投稿
ライブラリはルートコメント投稿時に、存在しない親コメント ID の番兵値を要求してはならず、`rootCommentId` と `parentCommentId` が null の場合は両プロパティを FANBOX API リクエストから省略しなければならない（SHALL）。

#### Scenario: 親 ID なしでルートコメントを投稿する
- **WHEN** 呼び出し側が `rootCommentId = null`、`parentCommentId = null` と本文を指定する
- **THEN** ライブラリは `rootCommentId` と `parentCommentId` を含まない `post.addComment` リクエストを送信する

### Requirement: 返信コメント投稿
ライブラリは返信コメント投稿時に、指定されたルートコメント ID と親コメント ID を FANBOX API リクエストへ含めなければならない（SHALL）。

#### Scenario: ルートコメントへ返信する
- **WHEN** 呼び出し側が non-null の `rootCommentId` と `parentCommentId` および本文を指定する
- **THEN** ライブラリは両 ID の API 値を含む `post.addComment` リクエストを送信する

### Requirement: FANBOX 実環境での投稿互換性
変更は FANBOX 実環境でルートコメント投稿と返信コメント投稿の双方が成功することを満たさなければならない（MUST）。

#### Scenario: 実環境で両投稿形式を確認する
- **WHEN** 認証済みクライアントがテスト用投稿にルートコメントを作成し、そのコメントへ返信する
- **THEN** FANBOX は双方のリクエストを成功として受理し、作成されたコメントを取得できる

