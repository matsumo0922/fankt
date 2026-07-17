## ADDED Requirements

### Requirement: URL embed metadata を公開 Link に保持する

システム SHALL article の `url_embed` block が参照する `urlEmbedMap` entry を解決し、entry の type、URL、HTML、FANBOX post を欠落させず公開 `Article.Block.Link` に保持しなければならない。Trace: Issue #29 の「Url entity に url を追加」「Block.Link を拡張」と受け入れ条件。

#### Scenario: default link の URL を保持する

- **WHEN** `type = default` かつ文字列の `url` を持ち、`html` と `postInfo` を持たない entry を参照する
- **THEN** システムは同じ type と URL、null の HTML と post を持つ `Link` を返す

#### Scenario: html.card の HTML を保持する

- **WHEN** `type = html.card` かつ文字列の `html` を持ち、`url` と `postInfo` を持たない entry を参照する
- **THEN** システムは同じ type と HTML、null の URL と post を持つ `Link` を返す

#### Scenario: html の HTML を保持する

- **WHEN** `type = html` かつ文字列の `html` を持ち、`url` と `postInfo` を持たない entry を参照する
- **THEN** システムは同じ type と HTML、null の URL と post を持つ `Link` を返す

#### Scenario: fanbox.post の投稿カードを保持する

- **WHEN** `type = fanbox.post` かつ `postInfo` を持ち、`url` と `html` を持たない entry を参照する
- **THEN** システムは同じ type と変換済み FANBOX post、null の URL と HTML を持つ `Link` を返す

#### Scenario: 未知 type の既知 metadata を保持する

- **WHEN** 未知の文字列 type と任意の `url` / `html` / `postInfo` を持つ entry を参照する
- **THEN** システムは type と存在する各 metadata を変更せず `Link` に保持する

### Requirement: URL embed の trust boundary と type semantics を公開する

公開 `Article.Block.Link` SHALL `default` をプレーンリンク、`html` と `html.card` を未信頼 HTML、`fanbox.post` を投稿カードとして KDoc に記載し、未知 type に対する metadata の捏造を禁止しなければならない。Trace: Issue #29 の type ごとの KDoc 要件と HTML 表示の安全境界。

#### Scenario: Link の HTML を caller が描画する

- **WHEN** caller が `Link.html` を描画する
- **THEN** 公開 KDoc はその HTML を sanitize してから描画する責務が caller にあることを示す

#### Scenario: 未知 type を構築する

- **WHEN** 公開 constructor で未知 type の `Link` を構築する
- **THEN** Link は受け取った metadata だけを返し、type 固有 URL や HTML を生成しない

### Requirement: 既存 Link serialized value の decode 互換を保つ

公開 `Article.Block.Link` SHALL class discriminator `type` と property の serialized key を衝突させず、type と URL を持たない旧 serialized value を decode でき、その場合は type `unknown` と null URL を使用しなければならない。Trace: public serializable model への field 追加に対する非退行 invariant。

#### Scenario: 旧 Link value を decode する

- **WHEN** HTML と post だけを持つ旧 `Link` serialized value を新しい `Body` serializer で decode する
- **THEN** システムは HTML と post を保持し、type `unknown` と null URL を持つ `Link` を返す

#### Scenario: Link を sealed Block として round-trip する

- **WHEN** type と URL を持つ `Link` を `Article.Block` serializer で encode して decode する
- **THEN** class discriminatorとLink typeが衝突せず、type、URL、HTML、postを保持した同じ値を返す

### Requirement: actual-response-derived fixture で公開経路を検証する

fixture test SHALL 保存済み actual response から匿名化した `default` / `html` / `html.card` / `fanbox.post` entry の field presence と型を保持し、完全な実値や credential を repository に含めず、公開 `Fanbox.getPostDetail` 経路を検証しなければならない。Trace: Issue #29 の3 fixture受け入れ条件と repository の golden fixture privacy contract。

#### Scenario: 4 type を production path で変換する

- **WHEN** actual-response-derived な4 type の匿名化 fixture を公開 `Fanbox.getPostDetail` から取得する test を実行する
- **THEN** ContentNegotiation、entity decode、mapper を通って URL、HTML、post が対応する `Link` に保持される
