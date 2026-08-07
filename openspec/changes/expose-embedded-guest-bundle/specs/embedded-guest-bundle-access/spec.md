## ADDED Requirements

### Requirement: A consumer supplies the embedded bundle through a fankt-owned read abstraction

Issue #99 の受け入れ条件「consumer が同梱 bundle を指定して `Fanbox` を生成できる」に対応する。

fankt は同梱 guest bundle の読み出し経路を受け取る公開 API を提供しなければならない（SHALL）。その API はファイル名を受け取ってバイト列を返す形とし、`okio.FileSystem` と `okio.Path` を公開境界に含めてはならない（SHALL NOT）。`ktor-free-public-api` が Ktor に対して定めた境界と同じ方針に従う。

対象のファイルが存在しない場合、その読み出しは例外ではなく不在を表す値を返す。

#### Scenario: Embedded bundle is configured through the public API

- **WHEN** consumer が同梱 bundle の読み出し経路を指定して `Fanbox` を生成する
- **THEN** その `Fanbox` は配信先へ到達できない場合に同梱 bundle をロードする

#### Scenario: The public ABI contains no okio type

- **WHEN** `fanbox` の公開 ABI を検査する
- **THEN** 同梱 bundle 関連の宣言に `okio` の型が現れない

#### Scenario: A missing file is not an exception

- **WHEN** 読み出し経路が指定されたファイルを持たない
- **THEN** 不在を表す値が返り、例外は発生しない

#### Scenario: Existing constructors keep working

- **WHEN** 同梱 bundle を指定せずに既存の `Fanbox` コンストラクタを呼ぶ
- **THEN** 従来どおり動作し、同梱 bundle を要求されない

### Requirement: An unreachable delivery target loads the embedded bundle through the public API

Issue #99 の受け入れ条件「配信先へ到達できない場合、同梱 bundle で guest 経路が動作する」に対応する。

公開 API から構成した同梱 bundle は、配信 manifest へ到達できない場合にロードされ、guest 経路で `post.info` が処理されなければならない（SHALL）。

`zipline-guest-bundle-loading` は同じ退避を `internal` の型で構成した経路について既に要求しているが、公開 API 経由の経路がそれを満たすことは別に示す必要がある。#99 の時点でこの経路は存在しなかったためである。

#### Scenario: Network failure loads the embedded bundle

- **WHEN** manifest URL への到達に失敗し、公開 API から指定された同梱 bundle が有効な署名を持つ
- **THEN** 同梱 bundle がロードされ、`post.info` が guest 経路で成功する

#### Scenario: The read path is asked for the manifest first

- **WHEN** 同梱 bundle をロードする
- **THEN** manifest のファイル名で読み出しが行われ、その manifest が参照するモジュールのファイル名で追加の読み出しが行われる

### Requirement: An embedded bundle failure falls back to the direct path

Issue #99 の受け入れ条件「同梱 bundle の署名検証失敗時に直接経路へ落ち、例外が漏れない」に対応する。

同梱 bundle の署名検証が失敗した場合、その失敗を呼び出し元へ例外として伝播させてはならない（SHALL NOT）。guest を経由しない既存経路で処理する。

local 経路の署名検証失敗は `LoadResult.Failure` ではなく例外として現れる。`ZiplineLoader` は cache と embedded の manifest 検証を `loadFromLocal` の `try` の外側で行うためである。network 経路とのこの非対称を同じ失敗として扱わなければならない（SHALL）。

#### Scenario: Embedded signature failure uses the direct path

- **WHEN** 公開 API から指定された同梱 bundle の manifest が信頼された鍵で検証できない
- **THEN** 検証の失敗が呼び出し元へ伝播せず、`post.info` が既存の直接経路で成功する

#### Scenario: A missing embedded manifest uses the direct path

- **WHEN** 読み出し経路が manifest を持たない
- **THEN** `post.info` が既存の直接経路で成功する

#### Scenario: A missing module is distinguished in diagnostics

- **WHEN** manifest は読み出せるが、それが参照するモジュールのいずれかが読み出せない
- **THEN** manifest 自体が見つからない場合とは区別できる診断が報告され、`post.info` が既存の直接経路で成功する

#### Scenario: Fallback is observable

- **WHEN** 同梱 bundle のロードで退避が起きる
- **THEN** その事実が診断経路へ報告され、無言では退避しない
