## MODIFIED Requirements

### Requirement: 投稿 type を本文 variant の正本にする

システム SHALL `post.info` の `post.type` を本文 variant 選択の正本として使用し、article 内では各 `block.type` を block variant 選択の正本として使用しなければならない。本文 payload や block 内の非空 field の組み合わせ・順序から variant を推測してはならない。Trace: Issue #26「mapper を post.type の when 分岐に書き換える」、Issue #28 の embed block、および未知 block の非退行要件。

#### Scenario: article 投稿を Article に変換する

- **WHEN** `post.type` が `article` で、各blockが1つの既知payloadを持ち、参照IDが対応mapに存在するarticle bodyを変換する
- **THEN** システムは解決可能なblockの順序と参照先を保った `Body.Article` を返す

#### Scenario: image 投稿を Image に変換する

- **WHEN** `post.type` が `image` で image body を持つ投稿詳細を変換する
- **THEN** システムは本文テキストと画像を持つ `Body.Image` を返す

#### Scenario: file 投稿を File に変換する

- **WHEN** `post.type` が `file` で file body を持つ投稿詳細を変換する
- **THEN** システムは本文テキストとファイルを持つ `Body.File` を返す

#### Scenario: payload に複数形式のフィールドが共存する

- **WHEN** 既知の `post.type` と、その type 以外の形式に使われる非空フィールドを同時に持つ投稿詳細を変換する
- **THEN** システムは `post.type` に対応する variant だけを返す

#### Scenario: block に複数形式の field が共存する

- **WHEN** 既知の `block.type` と、その type 以外の block に使われる非空 field が同時に存在する
- **THEN** システムは `block.type` に対応する variant だけを返す

#### Scenario: 未知 block type を変換する

- **WHEN** article body が未知の `block.type` と任意の field を持つ
- **THEN** システムは block の raw JSON を持つ `Article.Block.Unknown` を該当位置に返し、後続 block の変換を継続する

#### Scenario: 既知 block の field 型が不正である

- **WHEN** HTTP 200 の article body が既知 `block.type` に対して型不一致の field を持つ
- **THEN** `Fanbox.getPostDetail` は endpoint `post.info`、status 200 の `FanboxException.SchemaMismatch` を返し、`Article.Block.Unknown` へフォールバックしない
