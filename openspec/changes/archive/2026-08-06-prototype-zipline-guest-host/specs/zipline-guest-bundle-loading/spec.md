## ADDED Requirements

### Requirement: A bundle change alone changes the response interpretation

Issue #90 の受け入れ条件「ローカル manifest 配信で #12 相当の修正（`post.info` の `body` → `body.post`）が、アプリの再ビルドなしに bundle の差し替えだけで反映される」に対応する。host のコードと binary を変更せずに guest bundle を差し替えるだけで、`post.info` の response 解釈が変わらなければならない（SHALL）。

#### Scenario: Envelope fix is delivered by bundle replacement

- **WHEN** `post.info` の envelope を `body` として解釈する guest bundle をロードした host が、`body.post` 形式のレスポンスで失敗した後、host を変更せず manifest だけを `body.post` として解釈する guest bundle へ差し替える
- **THEN** 同じ host が同じレスポンスから `FanboxPostDetail` を得る

#### Scenario: Host binary is unchanged between the two bundles

- **WHEN** 上記 2 つの bundle を順に使う検証を行う
- **THEN** host 側のソースとビルド成果物はいずれの実行でも同一である

### Requirement: A bundle that fails signature verification is not executed

Issue #90 の受け入れ条件「署名検証に失敗した bundle が実行されず、fallback へ落ちる」に対応する。fankt は manifest の署名を信頼された公開鍵で検証し、検証に失敗した bundle のコードを実行してはならない（SHALL NOT）。検証に失敗した場合は同梱 fallback bundle へ退避する。署名検証を無効化する設定は production 経路で使用してはならない（SHALL NOT）。

#### Scenario: Tampered signature falls back

- **WHEN** 信頼された鍵に対応しない署名、または改竄された署名を持つ manifest をロードする
- **THEN** その bundle のコードは実行されず、同梱 fallback bundle がロードされる

#### Scenario: Unknown signing key falls back

- **WHEN** manifest の署名鍵名がいずれも信頼された鍵集合に含まれない
- **THEN** その bundle のコードは実行されず、同梱 fallback bundle がロードされる

#### Scenario: Embedded bundle signature failure does not escape

- **WHEN** 同梱 fallback bundle の manifest が信頼された鍵で検証できない
- **THEN** 検証の失敗が呼び出し元へ例外として伝播せず、guest を経由しない既存経路で `post.info` が成功する

#### Scenario: Valid signature loads

- **WHEN** 信頼された鍵で正しく署名された manifest をロードする
- **THEN** その bundle がロードされ、guest service が利用可能になる

#### Scenario: Signature checks are not disabled in production

- **WHEN** production 経路の manifest verifier 構成を検査する
- **THEN** 署名検証をスキップする設定が使われていない

### Requirement: An unreachable delivery target falls back to the embedded bundle

Issue #90 の受け入れ条件「配信先に到達できない場合、同梱 fallback bundle で従来どおり動作する」に対応する。manifest の配信先へ到達できない場合、fankt は同梱 fallback bundle をロードしなければならない（SHALL）。

#### Scenario: Network failure falls back

- **WHEN** manifest URL への到達に失敗する
- **THEN** 同梱 fallback bundle がロードされ、`post.info` が従来どおり成功する

#### Scenario: Embedded bundle failure preserves the existing behaviour

- **WHEN** 同梱 fallback bundle のロードにも失敗する、または guest の初期化に失敗する
- **THEN** `post.info` は guest を経由しない既存経路で成功する

#### Scenario: Fallback is observable

- **WHEN** 署名検証失敗、配信到達不能、guest 初期化失敗のいずれかで fallback が起きる
- **THEN** その事実が診断経路へ報告され、無言では退避しない

### Requirement: Guest initialization is deferred and owned by the Fanbox instance

Issue #90 の設計上の未決事項 4「`Fanbox` クラスの生成コスト」に対応する。Zipline engine の起動と bundle のロードは `Fanbox` のインスタンス生成時に同期実行してはならない（SHALL NOT）。guest を必要とする最初の操作まで遅延させ、`Fanbox.close()` がその engine を解放する。guest の呼び出しは Zipline が要求する単一スレッドの実行文脈で行う。

初期化に失敗した `Fanbox` インスタンスは、以降 guest を経由しない経路に固定しなければならない（SHALL）。呼び出しごとに初期化を再試行すると、配信先へ到達できない状況で全ての呼び出しに待ち時間が乗るためである。

#### Scenario: Construction performs no engine startup

- **WHEN** `Fanbox` のインスタンスを生成する
- **THEN** Zipline engine の起動と bundle のロードは行われない

#### Scenario: First guest-backed call initializes once

- **WHEN** guest を経由する操作を複数回実行する
- **THEN** engine の起動と bundle のロードは 1 回だけ行われ、以降は同じインスタンスが再利用される

#### Scenario: Initialization failure is not retried per call

- **WHEN** guest の初期化に失敗した後、同じ `Fanbox` インスタンスで `post.info` を繰り返し実行する
- **THEN** 初期化は再試行されず、いずれの呼び出しも guest を経由しない経路で完了する

#### Scenario: Runtime guest failure is not retried per call

- **WHEN** 初期化に成功した後の呼び出しで guest の実行が故障し、同じ `Fanbox` インスタンスで `post.info` を繰り返し実行する
- **THEN** その呼び出しを含めていずれも guest を経由しない経路で完了し、故障した guest は再び呼ばれない

#### Scenario: Close releases the engine

- **WHEN** guest を初期化した `Fanbox` を `close` する
- **THEN** Zipline engine が解放され、以降の guest 経由の操作は既存の公開契約どおり失敗する

### Requirement: Continuous integration verifies the guest bundle and its API

Issue #90 の「guest bundle のビルド task を通し、guest API を固定する」と受け入れ条件「`./gradlew build` と `:fankt:fanbox:jsTest` が通る」に対応する。pull request の検証ワークフローは guest bundle をビルドし、guest bridge API の検証を実行しなければならない（SHALL）。

#### Scenario: Guest bundle builds in CI

- **WHEN** pull request の検証ワークフローを実行する
- **THEN** guest bundle のビルド task と bridge API の検証が実行され、いずれも成功する

#### Scenario: Existing verification still passes

- **WHEN** 本 change の実装後に `./gradlew build` と `:fankt:fanbox:jsTest` を実行する
- **THEN** いずれも失敗せず完了する

#### Scenario: Existing publications keep building

- **WHEN** Zipline plugin を適用した状態で `:fankt:fanbox` の Kotlin/JS publication metadata を生成する
- **THEN** 従来どおり生成され、既存の consumer 向け klib が失われない
