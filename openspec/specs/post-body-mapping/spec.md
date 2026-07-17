# post-body-mapping Specification

## Purpose

`post.info` が返す投稿種別を正本として本文を解釈し、未対応・未知の本文でも型とJSON値を失わず安全に呼び出し側へ返す。

## Requirements

### Requirement: 投稿 type を本文 variant の正本にする

システム SHALL `post.info` の `post.type` を本文 variant 選択の正本として使用し、本文 payload 内の非空フィールドの組み合わせや順序から variant を推測してはならない。Trace: Issue #26「mapper を post.type の when 分岐に書き換える」および6 type の受け入れ条件。

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

### Requirement: 未対応および未知の type は raw body を保持する

システム SHALL `text`、`video`、`entry` および未知の `post.type` を `Body.Unknown(type, rawBodyJson)` として返し、投稿詳細全体の変換を継続しなければならない。Trace: Issue #26「Unknown に raw を持たせる」「未知 type でも投稿詳細自体は返す」および6 type の受け入れ条件。

#### Scenario: text 投稿を Unknown に変換する

- **WHEN** `post.type` が `text` の投稿詳細を変換する
- **THEN** システムは `type` が `text` で body のJSON表現を保持する `Body.Unknown` を返す

#### Scenario: video 投稿を Unknown に変換する

- **WHEN** `post.type` が `video` の投稿詳細を変換する
- **THEN** システムは `type` が `video` で body のJSON表現を保持する `Body.Unknown` を返す

#### Scenario: entry 投稿を Unknown に変換する

- **WHEN** `post.type` が `entry` の投稿詳細を変換する
- **THEN** システムは `type` が `entry` で body のJSON表現を保持する `Body.Unknown` を返す

#### Scenario: 未知の type を Unknown に変換する

- **WHEN** 既知の6 type以外の `post.type` と任意のJSON bodyを持つ投稿詳細を変換する
- **THEN** システムは受信した type と body のJSON表現を持つ `Body.Unknown` を含む投稿詳細を返す

#### Scenario: body が null の Unknown を変換する

- **WHEN** 未対応または未知の `post.type` の `post.body` がJSONの `null` である
- **THEN** システムは `rawBodyJson` が null の `Body.Unknown` を返す

### Requirement: Unknown は公開 serializable value として payload を保持する

公開 `FanboxPostDetail.Body.Unknown` SHALL `type: String` と `rawBodyJson: String?` を値として持つ serializable data class でなければならない。Trace: Issue #26 の破壊的変更要件。

#### Scenario: Unknown を値として比較する

- **WHEN** 同じ type と raw body JSON を持つ2つの `Body.Unknown` を構築する
- **THEN** システムは両者を等しい値として扱う

#### Scenario: Unknown を sealed Body として round-trip する

- **WHEN** `Body.Unknown` を `Body` serializerでJSONへencodeしてdecodeする
- **THEN** discriminatorと投稿typeが衝突せず、typeとraw body JSONを保持した同じ値を返す

#### Scenario: 旧 Unknown object を decode する

- **WHEN** payload fieldを持たない旧 `Body.Unknown` のserialized valueを新しいmodelでdecodeする
- **THEN** システムはtypeを `unknown`、raw body JSONをnullとする `Body.Unknown` を返す

### Requirement: 遅延した既知 body の schema error は公開例外契約を保つ

システム SHALL 既知 type のtyped body decodeが失敗した場合、公開 `Fanbox.getPostDetail` から生のserialization例外を漏らさず `FanboxException.SchemaMismatch` を返さなければならない。Trace: 公開APIの非退行 invariant。

#### Scenario: image body の必須fieldが欠落する

- **WHEN** HTTP 200の `post.info` が `type = image` かつ必須fieldを欠くimage bodyを返す
- **THEN** `Fanbox.getPostDetail` はendpoint `post.info`、status 200の `FanboxException.SchemaMismatch` を返す

#### Scenario: 既知 type の body が null である

- **WHEN** HTTP 200の `post.info` が `type = article`、`image`、または `file` かつ `post.body = null` を返す
- **THEN** `Fanbox.getPostDetail` はendpoint `post.info`、status 200の `FanboxException.SchemaMismatch` を返し、`Body.Unknown` へフォールバックしない
