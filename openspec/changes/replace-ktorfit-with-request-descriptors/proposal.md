## Why

FANBOX のリクエスト組み立てとレスポンス解釈が Ktorfit のアノテーション、KSP 生成コード、Ktor の自動デシリアライズに埋め込まれているため、API 変更へ追従するロジックをデータと純関数として持ち運べない。Kotlin/JS と Zipline guest 化の前提を整えつつ、20 弱の既存 API の wire 挙動を保つため、HTTP 実行から endpoint 定義と JSON 解釈を分離する。

## What Changes

- `RequestDescriptor`、endpoint ID、HTTP method、query parameter を Ktor 非依存の transport contract として追加する。
- `FanboxEndpoints` に、各 FANBOX API の path、method、query、JSON body を組み立てる純関数を集約する。
- raw response 文字列を既存 entity と mapper で domain model へ変換する endpoint 別の純関数を追加し、tolerant decode のログを注入可能にする。
- Ktor 実行を descriptor と認証情報から raw response を得る単一 executor に集約し、credential を付与する前に HTTPS、origin allowlist、endpoint ごとの method を検証する。
- 既存 `Fanbox` API の repository 経路を descriptor、executor、response parser へ移行し、既存の query、JSON body、Cookie、CSRF、例外、tolerant decode の挙動を維持する。
- `:fankt:fanbox` から Ktorfit と生成 API を削除し、fanbox module に不要となる KSP task への publication 依存を解消する。Fantia など KSP を引き続き使う module には影響させない。
- **（agent 仮決め）** 変更は additive core、executor/security、endpoint 群の段階移行、Ktorfit/KSP cleanup の独立マージ可能な stage に分割する。

## Capabilities

### New Capabilities
- `portable-request-descriptors`: 全 FANBOX endpoint の request を、Ktor/Room/Napier に依存しない検査可能な descriptor と純関数 builder で表現する。
- `pure-response-parsing`: raw JSON または HTML response を、transport に依存しない純関数で既存 domain model または既存例外へ変換する。
- `credential-safe-request-execution`: descriptor の送信先と method を検証した後だけ Cookie と CSRF token を付与して Ktor で実行する。
- `codegen-free-fanbox-client`: `:fankt:fanbox` が Ktorfit 生成 API に依存せず、既存の公開 API と wire 挙動を維持する。

### Modified Capabilities
- `request-time-csrf`: CSRF token の request-time 解決主体と安定した内部 graph を、Ktorfit generated API から descriptor executor へ置き換える。
- `fanbox-client-lifecycle`: close 対象を generated API client 群から request executor client と download client の構成へ更新する。
- `authenticated-media-download`: Ktorfit 撤去後の production route inventory に合わせ、generated route を前提とする検査表現を更新する。

## Impact

- `fankt/fanbox/src/commonMain`: `Fanbox` の配線、repository、datasource、mapper、cursor/URL 解析、例外変換、HTTP client 構築。
- `fankt/fanbox/src/commonTest` と `src/androidUnitTest`: endpoint descriptor、parser、executor policy、既存 MockEngine/fixture、route drift のテスト。
- `fankt/fanbox/build.gradle.kts`、`build-logic`、`gradle/libs.versions.toml`: fanbox の Ktorfit/KSP と publication task dependency。
- `fankt/fanbox/api`: 既存公開 API と domain model の ABI が変化していないことの確認。descriptor/builder/parser はこの change では internal に保つ。
- `README.md` と OpenSpec: request/response core と Ktor executor の境界、credential 付与条件、段階的移行手順。
- 既存 `Fanbox` 利用者の呼び出し API と domain model は維持する。Ktor は executor の internal implementation dependency として残る。
