## ADDED Requirements

### Requirement: リスト要素を個別に decode する

ライブラリは issue #19 の対象 endpoint について、外側の response envelope と pagination field の JSON 型を通常どおり decode し、配列要素を 1 件ずつ対象 entity に decode しなければならない（SHALL）。個別要素の decode または schema 由来の map が失敗した場合、その配列境界の要素だけを除外し、同じ配列内の正常要素を保持しなければならない（SHALL）。`post.getComments` の top-level comment と nested reply はそれぞれ独立した配列境界として扱わなければならない（SHALL）。

Issue trace: やること 1、対象 endpoint 一覧、受け入れ条件。

#### Scenario: タイムラインに壊れた投稿が混在する

- **WHEN** `post.listHome` の response に正常な投稿 2 件と必須フィールドが壊れた投稿 1 件が混在する
- **THEN** production API decode と mapper を通した結果は正常な投稿 2 件を元の順序で返し、壊れた 1 件だけを skip する

#### Scenario: bell に必須フィールド欠落が混在する

- **WHEN** `bell.list` の response に正常な通知 2 件と、既知 type だが必須フィールドが欠落した通知 1 件が混在する
- **THEN** production API decode と mapper を通した結果は正常な通知 2 件を元の順序で返し、欠落した 1 件だけを skip する

#### Scenario: 対象 endpoint が共通の tolerant decode を使う

- **WHEN** `post.listHome`、`post.listSupporting`、`post.listCreator`、`bell.list`、`post.getComments`、`creator.listFollowing`、`creator.listPixiv`、`creator.listRecommended`、`plan.listCreator`、`plan.listSupporting` の response を map する
- **THEN** 各配列境界は共通 helper によって要素単位で decode・map される

#### Scenario: nested reply に壊れた要素が混在する

- **WHEN** 1 件の正常な root comment が正常 reply 2 件と壊れた reply 1 件を含む
- **THEN** root comment と正常 reply 2 件を保持し、壊れた reply 1 件だけを nested index path 付きで skip する

### Requirement: skip を安全な診断情報として記録する

ライブラリは対象 list method に per-call mismatch callback を受ける additive overload を提供し、要素を skip したとき、endpoint label と階層ごとの 0-based index path を callback に同期的に通知しなければならない（SHALL）。callback は repository の IO 処理が完了して呼び出し元 coroutine context に戻った後、method return より前に実行しなければならない（SHALL）。同じ endpoint と index path を Napier warning に記録し、`Fanbox` の logging が明示的に有効な場合だけ raw JSON fragment を追加しなければならない（SHALL）。raw fragment は full item string を生成せず、credential key の値を構造的に redaction しながら最大 2,048 文字まで描画しなければならない（SHALL）。例外の message、throwable、未加工 response をログまたは callback event へ含めてはならない（MUST NOT）。

Issue trace: やること 1、skip 通知の最低条件。

#### Scenario: 壊れた要素を診断する

- **WHEN** 対象 endpoint で 1 件の要素が decode または schema 由来の map に失敗する
- **THEN** callback event と warning は endpoint と失敗要素の index path を含み、logging が有効な warning だけが structurally redacted・bounded fragment を含み、credential の値と例外 message を含まない

#### Scenario: logging を無効化して private item を skip する

- **WHEN** `Fanbox` の logging が `NONE` で、private post または comment item を skip する
- **THEN** callback event と warning は endpoint と index path だけを通知し、raw fragment や user content を含まない

#### Scenario: callback caller が supporting plan の部分成功を識別する

- **WHEN** mismatch callback を渡した `plan.listSupporting` overload で item が skip され、method が残りの plan を返す
- **THEN** その call の callback は `plan.listSupporting` と該当 index path の event を method return より前に受け取る

#### Scenario: supporting plan の既存 caller は strict failure を維持する

- **WHEN** callback を持たない既存 `getSupportedPlans()` で 1 件以上の item が decode または map に失敗する
- **THEN** method は欠落した通常 List を返さず `FanboxException.SchemaMismatch` で失敗する

#### Scenario: 同一 endpoint を並行に呼ぶ

- **WHEN** 同じ `Fanbox` instance で同一 endpoint の callback overload A と B を並行に呼び、B だけが item を skip する
- **THEN** mismatch event は B に渡した callback だけへ通知され、A の callback へ通知されない

#### Scenario: callback を caller context で実行する

- **WHEN** Main context から callback overload を呼び、repository が IO context で item を skip する
- **THEN** mismatch callback は repository の IO context ではなく元の Main context に戻ってから method return より前に実行される

### Requirement: 公開契約と pagination を維持する

ライブラリは既存の public method と return type を変更せず、個別要素を skip しても outer response の `nextUrl` から従来の mapper が得る cursor、page、offset を維持しなければならない（SHALL）。outer envelope または pagination field の JSON 型を decode できない場合は、従来どおり request 全体を `SchemaMismatch` として失敗させなければならない（SHALL）。`nextUrl` query value の semantic validation はこの change の保証に含めない。

Issue trace: skip 通知方式の検討、既存 API の非退行 invariant。

#### Scenario: skip 後も pagination が残る

- **WHEN** pagination metadata が正常で、配列内の一部要素だけが壊れている
- **THEN** 正常要素と、outer response から得た既存の pagination value を返す

#### Scenario: outer envelope の JSON 型が壊れている

- **WHEN** response の `body` または pagination field の JSON 型を decode できない
- **THEN** 個別要素の skip に縮退せず request 全体を `SchemaMismatch` として失敗させる

#### Scenario: strict supporting plan は actual response metadata を保つ

- **WHEN** callback を持たない既存 `getSupportedPlans()` の item decode が HTTP success response 上で失敗する
- **THEN** production ContentNegotiation と exception pipeline が actual response metadata を持つ `FanboxException.SchemaMismatch` を生成する
