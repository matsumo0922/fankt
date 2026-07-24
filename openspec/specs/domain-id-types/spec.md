# domain-id-types Specification

## Purpose
TBD - created by archiving change migrate-domain-ids-to-value-classes. Update Purpose after archive.
## Requirements
### Requirement: ID 型は単一の API 値を保持する
各 FANBOX ID 型は対応する String または Long の API 値だけを保持する distinct な value class でなければならない（MUST）。

#### Scenario: String ID を構築する
- **WHEN** 呼び出し側が同じ String 値から同じ種類の ID を 2 つ構築する
- **THEN** 2 つの ID は等しく、追加のランダム値を保持しない

#### Scenario: User ID を構築する
- **WHEN** 呼び出し側が Long 値から `FanboxUserId` を構築する
- **THEN** `FanboxUserId.value` は指定した Long 値と一致する

### Requirement: ID は非決定的な UI key を生成しない
ID 型は構築時または参照時に乱数 UUID その他の非決定的な値を生成してはならない（MUST NOT）。

#### Scenario: 同じ API 値を繰り返し ID に変換する
- **WHEN** 同じ API 値から ID を繰り返し構築する
- **THEN** 公開状態、equality、hashCode は毎回同じ結果になる

### Requirement: ID の基底値を明示的に使用する
ライブラリ内部で ID を API の query、body、URL へ変換する箇所は `value` プロパティを起点とし、既存 endpoint の wire 型を維持しなければならない（SHALL）。

#### Scenario: String ID を API リクエストへ設定する
- **WHEN** repository が String 基底の ID を query parameter または JSON body に設定する
- **THEN** 送信値は ID の `value` と同じ JSON string になる

#### Scenario: User ID を follow リクエストへ設定する
- **WHEN** repository が Long 基底の `FanboxUserId` を `follow.create` または `follow.delete` の `creatorUserId` に設定する
- **THEN** 送信値は `userId.value.toString()` と同じ JSON string になり、JSON number へ変化しない

### Requirement: ID は primitive として serialize される
String を基底値に持つ ID は JSON string、Long を基底値に持つ ID は JSON number として serialize されなければならない（MUST）。

#### Scenario: String ID を serialize する
- **WHEN** serializer が String 基底の ID を encode する
- **THEN** 出力は `value` オブジェクトではなく JSON string になる

#### Scenario: User ID を serialize する
- **WHEN** serializer が `FanboxUserId` を encode する
- **THEN** 出力は `value` オブジェクトではなく JSON number になる

#### Scenario: primitive ID を round-trip する
- **WHEN** serializer が ID を primitive JSON へ encode して同じ型へ decode する
- **THEN** decode 後の ID は元の ID と等しい

### Requirement: ルートコメントは番兵 ID を使用しない
`FanboxCommentId` はルートコメントを表す `EMPTY` その他の番兵定数を公開してはならない（MUST NOT）。

#### Scenario: ルートコメントを表現する
- **WHEN** 呼び出し側がルートコメントを投稿する
- **THEN** 呼び出し側はコメント ID の番兵値ではなく nullable な親 ID を使用する

### Requirement: 公開 ABI は value class 形状を反映する
Android と KLIB の公開 API dump は 7 種の ID の value class 形状を反映し、data class 固有 API を公開してはならない（MUST NOT）。

#### Scenario: ABI dump を検証する
- **WHEN** 公開 API 検証を実行する
- **THEN** dump に `uniqueValue`、`EMPTY`、data class の `copy` または `component1` が存在せず、検証が成功する
