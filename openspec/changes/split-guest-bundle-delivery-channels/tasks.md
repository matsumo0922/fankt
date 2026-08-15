## 1. 配信 workflow

- [ ] 1.1 `deploy-guest-bundle.yml` の `destination_dir` を `zipline/v1-dev` へ変更し、冒頭のコメントを 2 チャンネル構成の説明へ更新する
- [ ] 1.2 `deploy-guest-bundle.yml` の concurrency group を、gh-pages への書き込みを直列化する literal へ変更する
- [ ] 1.3 `promote-guest-bundle.yml` を追加する（`workflow_dispatch` のみ、gh-pages の checkout、署名と base URL の検査、`zipline/v1` への配置）

## 2. ドキュメント

- [ ] 2.1 `README.md` の `#### Bundle delivery` 節を 2 チャンネル構成へ書き換える（両チャンネルの URL、`main` へのマージだけでは届かないこと、昇格の手順）
- [ ] 2.2 `README.md` の `guestManifestUrl` / `manifestUrl` の例（2 箇所）が prod チャンネルを指していることを確認し、必要なら dev チャンネルの用途を補足する

## 3. 検証

- [ ] 3.1 変更した 2 本の workflow が YAML として解釈でき、`on` と job の構造が意図どおりであることを確認する
- [ ] 3.2 昇格前後の manifest が同一のバイト列であること、および公開済みの Ed25519 公開鍵で署名検証を通ることを、配信中の manifest を対象に確認する
- [ ] 3.3 promote の検査ロジックが、署名のない manifest と `null` でない base URL を持つ manifest を実際に拒否することを確認する
