---
name: check-fanbox-api
description: ブラウザ相当の curl fingerprint を使い、pixivFANBOX API とダウンロード endpoint の疎通を安全に確認する。FANBOX の接続確認、認証、Cloudflare の 403、post.info の失敗、FANBOXSESSID の有効性、api.fanbox.cc やその他の fanbox.cc 配下の response を調査するときに使う。
---

# FANBOX API 疎通確認

リポジトリルートの `scripts/check_fanbox_api.py` を使う。このスクリプトは
`curl_cffi` を介して GET request を1回だけ送り、認証情報や response body を
表示せず、status、Content-Type、必要に応じて JSON の構造を報告する。

## 安全規則

- `FANBOXSESSID` は mode `600` のファイルから読む。コマンドへ直接書かない。
- secret の値を表示、ログ出力、hash 化、報告への記載をしない。
- ユーザーが指定した request だけを送る。事前確認の endpoint を勝手に呼ばない。
- 許可なく別 fingerprint や標準 `curl` で再試行しない。
- 既定の `chrome136` fingerprint を優先する。標準の macOS `curl` では、同じ
  session が `curl_cffi` で成功しても Cloudflare challenge を受ける場合がある。
- ユーザーがテスト用だと説明した場合も、session は secret として扱う。

## 疎通を確認する

`https://api.fanbox.cc/` 配下の endpoint はパスだけで指定する。

```bash
scripts/check_fanbox_api.py post.info \
  --param postId=12244258
```

別の FANBOX host や query 付きURLは完全URLで指定する。

```bash
scripts/check_fanbox_api.py \
  'https://downloads.fanbox.cc/path/to/file?size=original' \
  --param download=1
```

`--param KEY=VALUE`を繰り返すとquery parameterを追加できる。URLに含まれる既存の
query parameterは維持する。session fileが既定の`/tmp/fankt-fanboxsessid`と
異なる場合は`--session-file`を指定する。

CSRF tokenが必要なendpointでは、別のmode`600`のファイルを指定する。

```bash
scripts/check_fanbox_api.py some.endpoint \
  --csrf-file /tmp/fankt-fanboxcsrf
```

`--impersonate PROFILE`は、ユーザーが別のfingerprintを明示的に求めた場合だけ使う。

## 依存関係を導入する

`curl_cffi`が利用できない場合は、導入前にユーザーへ確認する。依存関係は
リポジトリ外へ配置する。

```bash
python3 -m pip install \
  --target /tmp/fankt-fanbox-api-check/deps \
  curl_cffi
```

導入後は次のprefixを付けて実行する。

```bash
PYTHONPATH=/tmp/fankt-fanbox-api-check/deps \
scripts/check_fanbox_api.py post.info --param postId=12244258
```

## 結果を判定する

- `2xx`: endpointへ到達できている。JSONの場合は報告されたkey構造も確認する。
- `401 application/json`: FANBOXへ到達したが、認証に失敗している。
- `403 application/json`: FANBOXがアクセスを拒否したか、認証accountからresourceを
  利用できない。
- `403 text/html`かつCloudflare page: edge challengeがclientを拒否している。
  この結果だけでは`FANBOXSESSID`が無効だと判断しない。
- JSON以外の`2xx`: downloadまたはpage requestが成功している。bodyは表示しない。

正確なrequest回数、fingerprint、status、Content-Type、JSON構造の有無を報告する。
認証情報とresponseの内容は報告に含めない。
