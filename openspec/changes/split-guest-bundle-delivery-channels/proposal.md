## Why

guest bundle は `main` への push ごとに、consumer が読む本番パス `zipline/v1` へ直接配信されている。検証していない bundle を配る前に止める手段が存在せず、誤った PR が `main` に入ると次に起動した全ユーザーがその bundle で投稿詳細を処理する。

PixiView-KMP#139 の kill switch は配られた後に止める手段であり、配らない手段ではない。遠隔コード実行の配信経路に、kill switch より前段のこの層が欠けている。

## What Changes

- `deploy-guest-bundle.yml` の配信先を `zipline/v1` から `zipline/v1-dev` へ変更する。`main` への push は本番チャンネルを変更しなくなる
- `promote-guest-bundle.yml` を追加する。`workflow_dispatch` で `zipline/v1-dev` の成果物をそのまま `zipline/v1` へ配置する。再ビルドを行わない
- promote は配置前に、対象 manifest が存在し署名を持つことを検査する
- **BREAKING**（運用手順）: `main` へのマージだけでは consumer に届かなくなる。届けるには promote の手動実行が要る

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `zipline-guest-bundle-delivery`: 配信先が bridge API バージョンパスであることに加え、チャンネル（dev / prod）の分離と、prod への配置が手動昇格であることが要件になる

## Impact

- `.github/workflows/deploy-guest-bundle.yml`（配信先とconcurrency group）
- `.github/workflows/promote-guest-bundle.yml`（新規）
- `README.md` の `#### Bundle delivery` 節と `guestManifestUrl` / `manifestUrl` の例
- gh-pages 上に `zipline/v1-dev/` が増える。既存の `zipline/v1/` の内容は本 change では変化しない
- consumer 側のコードは変更しない。PixiView-KMP は現在 `zipline/v1` を参照しており、その参照は有効なまま維持される

## 配送形態

単一 PR とする。変更は workflow 2 本とドキュメントに閉じており、縦切りに分割しても各 PR が独立してレビュー可能な単位にならない（workflow だけ先に入れると README が実態と食い違う状態が残る）。

## 受け入れ条件との対応

issue matsumo0922/fankt#107 の受け入れ条件はすべて本 PR で扱う。範囲外として issue が挙げた 2 点（consumer 側のチャンネル出し分け、GitHub Environments による承認履歴）は本 change に含めない。前者は matsumo0922/PixiView-KMP#148 が扱う。
