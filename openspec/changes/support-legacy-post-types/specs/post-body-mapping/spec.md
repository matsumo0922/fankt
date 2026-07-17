## MODIFIED Requirements

### Requirement: 投稿 type を本文 variant の正本にする

システム SHALL `post.info` の `post.type` を本文 variant 選択の正本として使用し、article 内では各 `block.type` を block variant 選択の正本として使用しなければならない。本文 payload や block 内の非空 field の組み合わせ・順序から variant を推測してはならない。Trace: Issue #26「mapper を post.type の when 分岐に書き換える」、Issue #27 の3 type variant、Issue #28 の embed block、および未知 block の非退行要件。

#### Scenario: article 投稿を Article に変換する

- **WHEN** `post.type` が `article` で、各blockが1つの既知payloadを持ち、参照IDが対応mapに存在するarticle bodyを変換する
- **THEN** システムは解決可能なblockの順序と参照先を保った `Body.Article` を返す

#### Scenario: image 投稿を Image に変換する

- **WHEN** `post.type` が `image` で image body を持つ投稿詳細を変換する
- **THEN** システムは本文テキストと画像を持つ `Body.Image` を返す

#### Scenario: file 投稿を File に変換する

- **WHEN** `post.type` が `file` で file body を持つ投稿詳細を変換する
- **THEN** システムは本文テキストとファイルを持つ `Body.File` を返す

#### Scenario: text 投稿を Text に変換する

- **WHEN** `post.type` が `text` で文字列の `body.text` を持つ投稿詳細を変換する
- **THEN** システムは同じ本文文字列を持つ `Body.Text` を返す

#### Scenario: video 投稿を Video に変換する

- **WHEN** `post.type` が `video` で文字列の `body.video.serviceProvider` と `body.video.videoId` を持つ投稿詳細を変換する
- **THEN** システムは同じ provider と video ID を持つ `Body.Video` を返す

#### Scenario: entry 投稿を Html に変換する

- **WHEN** `post.type` が `entry` で文字列の `body.html` を持つ投稿詳細を変換する
- **THEN** システムは同じ HTML 文字列を持つ `Body.Html` を返す

#### Scenario: payload に複数形式のフィールドが共存する

- **WHEN** 既知の `post.type` と、その type 以外の形式に使われる非空フィールドを同時に持つ投稿詳細を変換する
- **THEN** システムは `post.type` に対応する variant だけを返す

#### Scenario: block に複数形式の field が共存する

- **WHEN** 既知の `block.type` と、その type 以外の block に使われる非空 field が同時に存在する
- **THEN** システムは `block.type` に対応する variant だけを返す

#### Scenario: 未知 block type を変換する

- **WHEN** article body が未知の `block.type` と、既知 block field と同名の構造不一致値を含む任意の field を持つ
- **THEN** システムは block の raw JSON を持つ `Article.Block.Unknown` を該当位置に返し、後続 block の変換を継続する

#### Scenario: 既知 block の field 型が不正である

- **WHEN** HTTP 200 の article body が既知 `block.type` に対して型不一致の field を持つ
- **THEN** `Fanbox.getPostDetail` は endpoint `post.info`、status 200 の `FanboxException.SchemaMismatch` を返し、`Article.Block.Unknown` へフォールバックしない

### Requirement: 未対応および未知の type は raw body を保持する

システム SHALL 既知の6 type以外の `post.type` を `Body.Unknown(type, rawBodyJson)` として返し、投稿詳細全体の変換を継続しなければならない。Trace: Issue #26「Unknown に raw を持たせる」と Issue #27 による既知6 typeの専用 variant 化。

#### Scenario: 未知の type を Unknown に変換する

- **WHEN** 既知の6 type以外の `post.type` と任意のJSON bodyを持つ投稿詳細を変換する
- **THEN** システムは受信した type と body のJSON表現を持つ `Body.Unknown` を含む投稿詳細を返す

#### Scenario: body が null の未知 type を変換する

- **WHEN** 未知の `post.type` の `post.body` がJSONの `null` である
- **THEN** システムは `rawBodyJson` が null の `Body.Unknown` を返す

### Requirement: 遅延した既知 body の schema error は公開例外契約を保つ

システム SHALL 既知 type のtyped body decodeが失敗した場合、公開 `Fanbox.getPostDetail` から生のserialization例外を漏らさず `FanboxException.SchemaMismatch` を返さなければならない。Trace: 公開APIの非退行 invariant と Issue #27 の新規 known body。

#### Scenario: image body の必須fieldが欠落する

- **WHEN** HTTP 200の `post.info` が `type = image` かつ必須fieldを欠くimage bodyを返す
- **THEN** `Fanbox.getPostDetail` はendpoint `post.info`、status 200の `FanboxException.SchemaMismatch` を返す

#### Scenario: 新規 known body の必須fieldが欠落する

- **WHEN** HTTP 200の `post.info` が `text` の `text`、`video` の `video` / `serviceProvider` / `videoId`、または `entry` の `html` のいずれかを欠く
- **THEN** `Fanbox.getPostDetail` はendpoint `post.info`、status 200の `FanboxException.SchemaMismatch` を返し、`Body.Unknown` へフォールバックしない

#### Scenario: 既知 type の body が null である

- **WHEN** HTTP 200の `post.info` が既知の6 typeかつ `post.body = null` を返す
- **THEN** `Fanbox.getPostDetail` はendpoint `post.info`、status 200の `FanboxException.SchemaMismatch` を返し、`Body.Unknown` へフォールバックしない

## ADDED Requirements

### Requirement: video provider URL を復元する

公開 `Body.Video` SHALL youtube と vimeo の video ID から provider 固有 URL を決定的に復元し、未知 provider には URL を捏造してはならない。Trace: Issue #27 の provider 別 URL 復元メモ。

#### Scenario: YouTube URL を復元する

- **WHEN** provider `youtube` と video ID を持つ `Body.Video` の URL helper を評価する
- **THEN** システムは `https://www.youtube.com/watch?v={videoId}` を返す

#### Scenario: Vimeo URL を復元する

- **WHEN** provider `vimeo` と video ID を持つ `Body.Video` の URL helper を評価する
- **THEN** システムは `https://vimeo.com/{videoId}` を返す

#### Scenario: 未知 provider の URL を評価する

- **WHEN** 未知 provider を持つ `Body.Video` の URL helper を評価する
- **THEN** システムは null を返す

### Requirement: entry HTML の trust boundary を公開する

公開 `Body.Html` SHALL FANBOX から受信した HTML 文字列を変更せず保持し、その値を信頼できない HTML として sanitize してから描画する責務が caller にあることを KDoc で宣言しなければならない。Trace: Issue #27 の entry HTML 実装メモ。

#### Scenario: entry HTML を保持する

- **WHEN** tag、attribute、text を含む `body.html` を持つ entry 投稿を変換する
- **THEN** `Body.Html.html` は受信した文字列を変更せず返す

### Requirement: hybrid fixture の provenance と保証範囲を分離する

Issue #27 の fixture test SHALL actual-response-derived な `post.info` envelope / field shape と合成した type 固有 payload の provenance を source 上で区別し、entity decode、mapper、公開 production call path の内部契約だけを証明する。実測していない `text` / `video` / `entry` 完全 payload を本番 schema 互換性の証拠として扱ってはならない。Trace: Issue #27 の実レスポンス由来 fixture 条件と hybrid fixture に関するユーザー確認。

#### Scenario: hybrid fixture を production path で変換する

- **WHEN** actual-response-derived envelope と field source に type 固有部分を合成した3 fixture を公開 `Fanbox.getPostDetail` から取得する test を実行する
- **THEN** ContentNegotiation、entity、mapper を通って `Text` / `Video` / `Html` を返し、fixture source と PR に実測部分・合成部分・未検証範囲を記録する
