## ADDED Requirements

### Requirement: paragraph と header semantics を保持する

システム SHALL article の `p` と `header` block を公開 `Article.Block.Text` に変換し、同じ text、元の block 順序、および header semantics を保持しなければならない。Trace: Issue #30 の header と空段落に関する受け入れ条件。

#### Scenario: 通常 paragraph を変換する

- **WHEN** `type = p` かつ非空 text を持つ block を変換する
- **THEN** システムは同じ text と `isHeader = false` を持つ Text を返す

#### Scenario: header を変換する

- **WHEN** `type = header` かつ text を持つ block を変換する
- **THEN** システムは同じ text と `isHeader = true` を持つ Text を返す

#### Scenario: 空 paragraph を保持する

- **WHEN** `type = p` かつ空文字の text を持つ block が他の block の間に存在する
- **THEN** システムは空文字の Text を同じ位置に返し、段落を除外しない

### Requirement: style と inline link span を保持する

システム SHALL p / header block が持つ各 style と inline link の type、offset、length、URL を公開 Text span value に変更せず保持しなければならない。欠落または null の span list は公開 empty list としなければならない。Trace: Issue #30 の styles / links field と AnnotatedString consumer 向け受け入れ条件。

#### Scenario: bold style を保持する

- **WHEN** text block が `type = bold` と整数 offset / length を持つ style span を含む
- **THEN** システムは同じ type、offset、length を持つ StyleSpan を返す

#### Scenario: inline link を保持する

- **WHEN** text block が整数 offset / length と文字列 URL を持つ link span を含む
- **THEN** システムは同じ offset、length、URL を持つ LinkSpan を返す

#### Scenario: style と link が共存する

- **WHEN** 同じ text block が style と link span を同時に持つ
- **THEN** システムは各 list の順序と受信値を保った両 metadata を同じ Text に返す

#### Scenario: 未知 style type を保持する

- **WHEN** text block が未知の文字列 type を持つ style span を含む
- **THEN** システムは style を拒否・削除・既知 type へ変換せず同じ文字列 type を返す

### Requirement: 旧 Text value の decode 互換を保つ

公開 `Article.Block.Text` SHALL text だけを持つ旧 serialized value を decode でき、その場合は empty styles、empty links、`isHeader = false` を使用しなければならない。Trace: public serializable model への field 追加に対する非退行 invariant。

#### Scenario: 旧 Text value を decode する

- **WHEN** text field だけを持つ旧 Text serialized value を新しい Block serializer で decode する
- **THEN** システムは同じ text、empty styles、empty links、`isHeader = false` を持つ Text を返す

#### Scenario: span 付き Text を sealed Block として round-trip する

- **WHEN** header flag と style / link span を持つ Text を Article.Block serializer で encode して decode する
- **THEN** class discriminator と全 Text metadata を保持した同じ値を返す

### Requirement: span と URL の trust boundary を公開する

公開 Text span KDoc SHALL offset / length を受信値として保持し、caller が text boundary を検証してから適用する責務を記載しなければならない。LinkSpan KDoc SHALL caller が URL policy を検証してから navigation に使う責務を記載しなければならない。Trace: 未信頼 network metadata を downstream UI が安全に使用するための非退行 invariant。

#### Scenario: caller が span を描画へ適用する

- **WHEN** caller が StyleSpan または LinkSpan の range を text に適用する
- **THEN** 公開 KDoc は offset と length が text boundary 内か事前に検証する責務を示す

#### Scenario: caller が inline link を開く

- **WHEN** caller が LinkSpan URL を navigation に使用する
- **THEN** 公開 KDoc は許可 scheme と URL policy を事前に検証する責務を示す

### Requirement: actual-response-derived fixture で公開経路を検証する

fixture test SHALL 保存済み actual response から匿名化した header、bold style、inline link、空 paragraph の field presence と型を保持し、完全な実値や credential を repository に含めず、公開 `Fanbox.getPostDetail` 経路を検証しなければならない。Trace: Issue #30 の fixture 受け入れ条件と repository の golden fixture privacy contract。

#### Scenario: text metadata を production path で変換する

- **WHEN** actual-response-derived な匿名化 fixture を公開 `Fanbox.getPostDetail` から取得する test を実行する
- **THEN** ContentNegotiation、entity decode、mapper を通って header、bold、inline link、空 paragraph が対応する Text に保持される
