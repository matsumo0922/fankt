## MODIFIED Requirements

### Requirement: Delivery publishes a signed manifest under a bridge API version path

manifest URL は bridge API バージョンを含むパスの下に置かなければならない（SHALL）。consumer は焼き込んだ URL を参照し続けるため、bridge API を変更した bundle が、その変更を知らない host へ届くことがない。

配信先はさらに 2 つのチャンネルへ分かれていなければならない（SHALL）。`main` への push で自動配信される dev チャンネルと、手動の昇格でのみ内容が変わる prod チャンネルである。consumer が既定で読むのは prod チャンネルであり、自動配信がそこへ直接届いてはならない（SHALL NOT）。検証していない bundle を配る前に止める手段が、配信後の停止機構とは別に必要なためである。

#### Scenario: Manifest is reachable at the versioned path

- **WHEN** 配信 workflow が完了した後、bridge API バージョンを含む manifest URL を取得する
- **THEN** 署名済み manifest が HTTPS で取得でき、参照する bundle も同じ配信先から取得できる

#### Scenario: A bridge API change moves to a new path

- **WHEN** `FanboxGuestService` の関数シグネチャを変更した bundle を配信する
- **THEN** 既存バージョンのパスの manifest は置き換えられず、新しいバージョンのパスへ配置される

#### Scenario: Delivery does not remove the previous version

- **WHEN** 新しいバージョンのパスへ配置する
- **THEN** 以前のバージョンのパスにある manifest と bundle が引き続き取得できる

#### Scenario: A push to the default branch reaches only the dev channel

- **WHEN** `main` へ push した結果として配信 workflow が完了する
- **THEN** dev チャンネルの manifest と bundle が新しい成果物へ置き換わり、prod チャンネルの内容は変化しない

#### Scenario: Both channels are named in the documentation

- **WHEN** 配信を運用する担当者が手順を参照する
- **THEN** 2 つのチャンネルの URL、`main` へのマージだけでは consumer に届かないこと、昇格の実行手順が記載されている

## ADDED Requirements

### Requirement: Promotion places the verified dev artifact into the production channel without rebuilding

prod チャンネルへの配置は、手動で起動する昇格の workflow だけが行わなければならない（SHALL）。昇格は dev チャンネルに置かれた成果物のバイト列をそのまま配置しなければならず、ビルドをやり直してはならない（SHALL NOT）。やり直すと、dev で検証したものとは別のバイト列が prod へ乗る。

昇格は配置の前に、対象の manifest が署名を持つことを検査しなければならない（SHALL）。署名のない manifest を配信しない防護は、dev チャンネルへの配信と prod チャンネルへの昇格の双方で成立していなければならない（SHALL）。前者はビルド出力を対象とするため、それを経ずに配信先へ置かれた内容を昇格させる経路を塞がない。

#### Scenario: Promotion copies the delivered bytes

- **WHEN** 昇格の workflow を実行する
- **THEN** ビルドは実行されず、prod チャンネルの manifest とモジュールが dev チャンネルのものとバイト列として一致する

#### Scenario: Promotion is not triggered by a push

- **WHEN** `main` へ push する
- **THEN** 昇格の workflow は起動しない

#### Scenario: Promotion refuses an unsigned manifest

- **WHEN** dev チャンネルの manifest が署名を持たない状態で昇格を実行する
- **THEN** 昇格は失敗し、prod チャンネルの内容は変化しない

#### Scenario: Promotion refuses a missing manifest

- **WHEN** dev チャンネルへ配信が一度も成功していない状態で昇格を実行する
- **THEN** 昇格は失敗し、prod チャンネルの内容は変化しない

#### Scenario: A promoted manifest verifies with the published public key

- **WHEN** 昇格された prod チャンネルの manifest を、公開された Ed25519 公開鍵で検証する
- **THEN** 署名検証を通る

#### Scenario: Promotion removes modules that the new bundle no longer references

- **WHEN** モジュール構成が変わった成果物を昇格する
- **THEN** prod チャンネルには昇格した成果物のファイルだけが残り、以前のモジュールは残らない
