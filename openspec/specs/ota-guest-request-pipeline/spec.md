# ota-guest-request-pipeline Specification

## Purpose

Define the split where the Zipline guest supplies the FANBOX request descriptor and the parsed
domain model while the host keeps validated HTTP execution and credentials, so a parsing fix reaches
users by replacing the guest bundle rather than by shipping a new app.

## Requirements
### Requirement: A guest service supplies the request descriptor and the domain model

Issue #90 の「guest 側の `ZiplineService` を定義する」に対応する。fankt は Zipline guest が実行する service を定義しなければならない（SHALL）。その service は `post.info` について、post ID から `RequestDescriptor` を組み立てる関数と、raw response body と HTTP status code から解釈結果を返す関数を提供する。request の組み立てと response の解釈の両方が guest 側に置かれ、entity から domain model への mapping も guest に含まれる。

guest の解釈が失敗した場合、その失敗は例外ではなく serialize 可能な戻り値として host へ渡さなければならない（SHALL）。戻り値は「FANBOX のレスポンスがスキーマと一致しない」失敗と「guest の実行そのものが壊れている」失敗を区別できる形とする。理由は、guest が投げた例外が bridge を越えると元の型が失われ、host が 2 つを区別できなくなることにある。

#### Scenario: Guest builds the post detail descriptor

- **WHEN** guest service の request 組み立て関数に post ID を渡す
- **THEN** endpoint ID が `post.info` に対応し、path が `post.info`、method が GET、query に当該 post ID を含む `RequestDescriptor` が返る

#### Scenario: Guest parses the post detail response

- **WHEN** guest service の response 解釈関数に `post.info` の成功レスポンス body と status code 200 を渡す
- **THEN** 既存の host 側 parsing と同じ内容の `FanboxPostDetail` を運ぶ成功結果が返る

#### Scenario: Schema mismatch reaches the caller as a schema mismatch

- **WHEN** guest service の response 解釈関数に、FANBOX のスキーマと一致しない body を渡す
- **THEN** guest はスキーマ不一致を示す失敗結果を返し、host はそれを `FanboxException.SchemaMismatch` として呼び出し元へ伝える

#### Scenario: Schema mismatch does not trigger the guest fallback

- **WHEN** guest がスキーマ不一致を示す失敗結果を返す
- **THEN** host は guest 経路が故障したとは扱わず、同梱 fallback bundle や guest を経由しない経路への退避を行わない

#### Scenario: Guest execution failure is distinguishable

- **WHEN** guest の実行そのものが失敗する、または guest が予期しない失敗結果を返す
- **THEN** host はそれをスキーマ不一致とは区別し、guest 経路の故障として扱う

### Requirement: The guest bridge API cannot receive credentials

Issue #90 の受け入れ条件「guest の bridge API に credential（`FANBOXSESSID` / CSRF token）を渡す経路が存在しない」に対応する。guest bridge API の関数シグネチャは、cookie storage、CSRF token provider、cookie の値、CSRF token の値のいずれも引数または戻り値の型として含んではならない（SHALL NOT）。credential の付与は host 側の HTTP 実行経路だけが行う。

#### Scenario: Bridge API signatures contain no credential type

- **WHEN** guest bridge service の全関数のシグネチャを検査する
- **THEN** `FanboxCookieStorage`、`FanboxTokenStore`、cookie record、CSRF token を表す型がいずれの引数にも戻り値にも現れない

#### Scenario: Guest API changes are detected

- **WHEN** guest bridge API の関数を追加・変更・削除して bridge API の検証を実行する
- **THEN** 記録済みの API との差分が検出され、検証が失敗する

#### Scenario: API verification is not vacuous

- **WHEN** bridge API の検証 task を実行する
- **THEN** 実際に検査対象を持つ。検査対象がゼロのまま成功する状態で継続的検証へ組み込まれていない

#### Scenario: Credentials are attached after host validation

- **WHEN** guest が返した descriptor で `post.info` を実行する
- **THEN** host が `FanboxDescriptorValidator` の検証を通した後に cookie と CSRF token を解決し、guest はそのいずれも観測しない

### Requirement: Guest descriptors pass through the existing trusted endpoint validation

Issue #90 の「host 側で guest を呼び出し、返った descriptor を `FanboxDescriptorValidator` に通してから実行する経路を作る」に対応する。guest が返した `RequestDescriptor` は、既存の host 経路と同一の検証（HTTPS 強制、origin allowlist、method 一致、path traversal 排除、リダイレクト先の再検証）を通してからでなければ HTTP 実行してはならない（SHALL NOT）。`TrustedFanboxEndpointPolicy` と `FanboxDescriptorValidator` は `internal` のまま保たれる。

#### Scenario: Tampered guest descriptor is rejected

- **WHEN** guest が信頼された origin の外を指す descriptor、または endpoint policy と異なる method の descriptor を返す
- **THEN** credential を読む前、かつ transport の処理を始める前に実行が失敗する

#### Scenario: Validation types stay internal

- **WHEN** `:fankt:fanbox` の公開 API を検査する
- **THEN** `TrustedFanboxEndpointPolicy` と `FanboxDescriptorValidator` はいずれも公開されていない

### Requirement: The public Fanbox API is unchanged by the guest pipeline

Issue #90 の構成表「PixiView から見える API は従来どおり `Fanbox` クラスのみ」に対応する。guest 経由の経路を導入しても、`Fanbox` の公開 API と既存の publication の構成は変わってはならない（SHALL NOT）。guest bundle 用の Kotlin/JS target は publication に含めない。

#### Scenario: Public API surface is preserved

- **WHEN** guest 経路の導入後に klib ABI dump と Android ABI を検査する
- **THEN** 既存の公開宣言が減らず、Zipline の型が公開 API に現れない

#### Scenario: Existing publications still build

- **WHEN** guest 経路の導入後に `:fankt:fanbox` の Android、iOS、JS の publication metadata を生成する
- **THEN** いずれも従来どおり生成され、guest bundle 用の target の publication は生成されない

### Requirement: The guest path is inert without an explicit delivery configuration

Issue #90 の非対象宣言「配信基盤（ホスティング、署名鍵の管理、kill switch、同梱 fallback の更新運用）は本 issue の範囲外」に対応する。本 change は production の manifest URL と信頼する公開鍵の正本を定義しない。したがって guest 経路が有効になるのは、呼び出し側が manifest URL と信頼する公開鍵の両方を明示的に与えたときに限らなければならない（SHALL）。いずれかが与えられない構成では、guest を経由しない既存経路で動作する。

#### Scenario: Default configuration uses the existing path

- **WHEN** manifest URL と信頼する公開鍵を与えずに `Fanbox` を構成し `post.info` を実行する
- **THEN** guest engine は起動せず、本 change の前と同じ経路と同じ結果で完了する

#### Scenario: Explicit configuration enables the guest path

- **WHEN** manifest URL と信頼する公開鍵を与えて `Fanbox` を構成し `post.info` を実行する
- **THEN** guest 経由で descriptor が組み立てられ、response が解釈される

#### Scenario: No default trusted key is embedded

- **WHEN** ライブラリの production 経路を検査する
- **THEN** 既定値としての manifest URL も信頼公開鍵も埋め込まれていない

