## ADDED Requirements

### Requirement: The embedded bundle layout differs from the build output and is produced by the download task

同梱する bundle のディレクトリ構成は、Zipline plugin のビルド出力と一致しない。plugin は `manifest.zipline.json` と可読名の `*.zipline` を出力するが、loader が同梱 bundle として読むのは `<applicationName>.manifest.zipline.json` と、拡張子を持たない `<sha256 の hex>` という名前のモジュールである。

ビルド出力をそのまま同梱した場合、loader は manifest を見つけられずに退避する。この失敗は例外として現れず、guest を経由しない経路へ静かに落ちる。したがって同梱成果物の生成手順は文書化されなければならない（SHALL）。

生成には Zipline が提供する `ZiplineDownloadTask` を用いる。fankt は同等の変換を自前で実装してはならない（SHALL NOT）。

#### Scenario: The layout requirement is documented

- **WHEN** 同梱 bundle を用意する担当者が手順を参照する
- **THEN** manifest のファイル名がアプリケーション名を含むこと、モジュールが sha256 の hex で命名されること、ビルド出力の直接コピーでは動作しないことが記載されている

#### Scenario: Download-time verification is absent by design

- **WHEN** `ZiplineDownloadTask` で同梱成果物を取得する
- **THEN** そのダウンロードは署名を検証しないが、保存された manifest の署名は実行時に consumer の公開鍵で検証され、検証に失敗した同梱 bundle のコードは実行されない

#### Scenario: Replacement cadence is documented

- **WHEN** consumer がリリース手順を参照する
- **THEN** リリースごとに同梱 bundle を最新へ差し替える必要と、差し替えを怠った場合に配信不達時に古い挙動で動作することが記載されている
