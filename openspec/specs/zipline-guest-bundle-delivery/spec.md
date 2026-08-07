# zipline-guest-bundle-delivery Specification

## Purpose
TBD - created by archiving change deliver-zipline-guest-bundles. Update Purpose after archive.
## Requirements
### Requirement: The signing private key never enters the repository

配信される bundle の信頼はこの秘密鍵だけに依拠する。鍵が漏れれば任意のコードを全ユーザーへ配信できるため、リポジトリと build script には秘密鍵を置いてはならない（SHALL NOT）。秘密鍵は GitHub Secrets に保管し、CI が Gradle property として渡す。

#### Scenario: Repository contains no private key

- **WHEN** リポジトリの追跡対象ファイルを検査する
- **THEN** Ed25519 秘密鍵の値がいずれのファイルにも含まれない

#### Scenario: Build without the key produces an unsigned bundle

- **WHEN** 秘密鍵の Gradle property を与えずに guest bundle をビルドする
- **THEN** ビルドは失敗せず、署名のない manifest が生成される

#### Scenario: Unsigned bundle is not published

- **WHEN** 署名のない manifest が生成された状態で配信 workflow を実行する
- **THEN** その manifest は配信先へ配置されない

### Requirement: Delivery publishes a signed manifest under a bridge API version path

manifest URL は bridge API バージョンを含むパスの下に置かなければならない（SHALL）。consumer は焼き込んだ URL を参照し続けるため、bridge API を変更した bundle が、その変更を知らない host へ届くことがない。

#### Scenario: Manifest is reachable at the versioned path

- **WHEN** 配信 workflow が完了した後、bridge API バージョンを含む manifest URL を取得する
- **THEN** 署名済み manifest が HTTPS で取得でき、参照する bundle も同じ配信先から取得できる

#### Scenario: A bridge API change moves to a new path

- **WHEN** `FanboxGuestService` の関数シグネチャを変更した bundle を配信する
- **THEN** 既存バージョンのパスの manifest は置き換えられず、新しいバージョンのパスへ配置される

#### Scenario: Delivery does not remove the previous version

- **WHEN** 新しいバージョンのパスへ配置する
- **THEN** 以前のバージョンのパスにある manifest と bundle が引き続き取得できる

### Requirement: A delivered bundle loads on the host that consumes it

配信された bundle は、実際の consumer 構成で署名検証を通り guest として動作しなければならない（SHALL）。テスト内で生成した鍵による検証は #90 で完了しているが、それは配信された成果物が正しいことを示さない。

#### Scenario: Delivered manifest verifies with the published public key

- **WHEN** 配信された manifest を、公開された Ed25519 公開鍵を信頼する `Fanbox` からロードする
- **THEN** 署名検証を通り、`post.info` が guest 経路で成功する

#### Scenario: Signing key mismatch falls back instead of failing

- **WHEN** 配信された manifest の署名鍵と異なる公開鍵を信頼する `Fanbox` からロードする
- **THEN** guest は使用されず、`post.info` が既存の直接経路で成功する

### Requirement: Key rotation keeps older applications working

鍵をローテーションする場合、移行期間中の manifest は新旧いずれの鍵でも検証できなければならない（SHALL）。consumer に焼き込まれた公開鍵はアプリ更新なしには変更できず、旧鍵しか知らないアプリが検証に失敗すると OTA 経路が失われる。

`ManifestVerifier.verify` は manifest の署名のうち最初に認識できた鍵で検証して成功を返すため、manifest へ複数の署名を載せることでこれを満たす。

#### Scenario: A manifest signed by both keys verifies with either

- **WHEN** 新旧 2 つの鍵で署名した manifest を、いずれか一方の鍵だけを信頼する host からロードする
- **THEN** どちらの host でも署名検証を通る

#### Scenario: Rotation procedure is documented

- **WHEN** 鍵のローテーションを行う担当者が手順を参照する
- **THEN** 移行期間中の二重署名、旧鍵の廃止時期の判断、公開鍵を焼き込んだ consumer への影響が文書化されている

### Requirement: The wire schema of bridged types is fixed by fixtures

`ziplineApiCheck` は `FanboxGuestService` の関数シグネチャのみを固定し、pass-by-value で bridge を越える型の serialization schema は検証しない。schema を変えても関数シグネチャが同じであれば検証は通り、古い host が新しい bundle を読むと decode に失敗する。この変更を CI で検出しなければならない（SHALL）。

対象は `RequestDescriptor`、`GuestParseResult`（sealed の discriminator と各 subtype の field を含む）、`FanboxPostDetail`（`Body` の sealed 階層を含む推移的な serial graph）である。

#### Scenario: A schema change fails the build

- **WHEN** 上記いずれかの型の serial name、field 名、sealed の discriminator を変更する
- **THEN** golden fixture との不一致で検証が失敗する

#### Scenario: Fixtures cover the sealed hierarchies

- **WHEN** golden fixture の対象を検査する
- **THEN** `GuestParseResult` の全 subtype と `FanboxPostDetail.Body` の sealed 階層が含まれる

#### Scenario: An intended schema change is recorded with a new bridge API version

- **WHEN** wire schema の変更を意図して行う
- **THEN** fixture の更新と bridge API バージョンの引き上げが同じ change に含まれる

### Requirement: A bridge failure falls back instead of escaping

guest の呼び出しで生じた失敗を、呼び出し元へ例外として伝播させてはならない（SHALL NOT）。guest を無効化して既存の直接経路へ退避する。対象は `CancellationException` を除くすべての `Exception` である。

`Error` 系はこの Requirement の対象としない。JVM が資源枯渇または破損した状態にあるシグナルであり、退避しても直接経路が同じ理由で失敗するためである。ただし guest の初期化経路が既に `Error` を退避対象にしている場合、その挙動を狭めてはならない（SHALL NOT）。狭めると、現在は退避できている失敗が呼び出し元へ届くようになる。

golden fixture と `ziplineApiCheck` は意図しない変更を配信前に止めるが、網羅の漏れを保証しない。漏れた場合の失敗は値ではなく例外として現れ、その型は 1 つではない。値の decode 失敗は `SerializationException`、bridge API の関数レベルの不整合は `ZiplineApiMismatchException` であり、後者は `Exception` を直接継承するため `ZiplineException` の catch にも掛からない。`Fanbox` の公開契約は `FanboxException` しか宣言しておらず、consumer はこれらを捕捉しない。

捕捉する型を列挙する形では、Zipline 側に型が増えるたびに同じ穴が開く。guest は信頼できない入力を解釈する境界であり、そこから来る失敗の型を事前に列挙できるという前提を置かない。

#### Scenario: Result decode failure falls back

- **WHEN** guest が返した値を host が decode できない
- **THEN** guest は無効化され、`post.info` が既存の直接経路で成功する

#### Scenario: Descriptor decode failure falls back

- **WHEN** guest が返した request descriptor を host が decode できない
- **THEN** guest は無効化され、`post.info` が既存の直接経路で成功する

#### Scenario: Bridge API mismatch falls back

- **WHEN** guest bundle の bridge API が host の期待する関数シグネチャと一致しない
- **THEN** guest は無効化され、`post.info` が既存の直接経路で成功する

#### Scenario: An unanticipated failure type falls back

- **WHEN** guest の呼び出しが上記のいずれにも該当しない型の例外で失敗する
- **THEN** guest は無効化され、`post.info` が既存の直接経路で成功する

#### Scenario: Cancellation is not swallowed

- **WHEN** guest を経由する呼び出しが cancel される
- **THEN** `CancellationException` は退避として扱われず、呼び出し元へ伝播する

#### Scenario: Fallback is observable

- **WHEN** guest の失敗で退避が起きる
- **THEN** その事実が診断経路へ報告され、無言では退避しない

#### Scenario: Failure does not widen the existing exception contract

- **WHEN** guest 経路を持つ `Fanbox` で guest の失敗が起きた後、`post.info` を繰り返し実行する
- **THEN** guest 由来の例外が呼び出し元へ伝播せず、いずれの呼び出しも既存の公開契約どおりに完了する

