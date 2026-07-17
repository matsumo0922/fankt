## Why

`PersistentCookieStorage.get(requestUrl)` が保存済み Cookie を送信先に関係なく全件返すため、pixiv OAuth Cookie や `FANBOXSESSID` が別 origin へ漏えいし得る。Issue #20 の送信段階の止血として、永続 Cookie storage が Ktor の Cookie 適用規則と expiry を担保する。

## What Changes

- request URL の domain、path、secure scheme に一致する Cookie だけを返す。
- 期限切れ Cookie を返さず、読み取り時に DB から削除する。
- `maxAge` を保存時の絶対 expiry に変換し、永続化後も期限を維持する。
- Cookie の identity を `domain + path + name` の composite key とし、nullable domain に由来する synthetic ID を廃止する。
- DB v1 の既存 Cookie を保持して v2 へ移行し、既存行は fail-safe に HTTPS 限定とする。
- `addCookie` を注入済み `ioDispatcher` 上で実行する。

## Capabilities

### New Capabilities

- `persistent-cookie-safety`: 永続 Cookie の送信先マッチング、期限管理、identity、DB migration を定義する。

### Modified Capabilities

なし。

## Impact

- `fankt/fanbox` の Cookie entity、DAO、storage、Room database version と migration を変更する。
- `Fanbox.getHttpClient()` を含む、共有 `PersistentCookieStorage` を利用するすべての HTTP client に適用される。
- public API の signature は変更しない。

## Decision Attribution

- （ユーザー確認済み）domain / path / secure / expiry のマッチング、expired row の削除、`ioDispatcher` の統一、Cookie identity の見直しは Issue #20 の契約である。
- （agent 仮決め）synthetic ID を composite primary key に置き換える。
- （agent 仮決め）既存 v1 Cookie は値を保持し、漏えいを防ぐ fail-safe として `secure=true` で v2 へ移行する。
