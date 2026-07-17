## Context

現在の Ktorfit response entity は list item を concrete entity の `List` として保持するため、ContentNegotiation が response 全体を一括 decode する。1 件の schema mismatch が `FanboxException.SchemaMismatch` に変換され、同じ response 内の正常要素も失われる。`bell.list` は variation ごとの nullable field を mapper の `!!` で参照しており、decode 後にも一覧全体が失敗し得る。

issue #19 は #15 の golden fixture と #17 の typed `SchemaMismatch` を前提とする。両者は `origin/main` に merge 済みである。

## Goals / Non-Goals

**Goals:**

- 対象 endpoint の item failure を配列要素の境界に閉じ込める。
- 正常要素の順序と pagination metadata を保持する。
- skip の場所を当該 call の caller が機械的に観測でき、明示 logging 時だけ入力断片を credential-safe な診断情報として残す。
- bell の既知 type にある必須フィールド欠落を `!!` なしで扱う。

**Non-Goals:**

- 既存 method の result type に skip metadata を追加すること。
- outer response envelope の破損を部分成功として扱うこと。
- `payment.listPaid`、`payment.listUnpaid`、`newsletter.list`、search/tag/pagination endpoint を対象に広げること。
- item schema を緩和して不正な domain model を合成すること。

## Decisions

### 1. List item の serializable field を `JsonElement` にする

（agent 仮決め）対象 entity の item collection を `List<JsonElement>` に変更し、outer `body`、`nextUrl`、`viewMode` は concrete type のまま維持する。comment の `replies` も再帰的に `List<JsonElement>` とし、top-level index に nested reply index を連結した path で診断する。これにより ContentNegotiation は request 全体の構造を検証しつつ、各配列境界の item failure だけを mapper 境界まで遅延できる。

代替の response 全体を `HttpResponse` / `String` で受ける方式は、endpoint ごとに outer decode と error handling を再実装し、#17 の typed exception 経路を迂回するため採用しない。

### 2. 共有 decoder が decode と schema 由来の map を同じ item 境界で扱う

（agent 仮決め）production の `Json` と `DeserializationStrategy<T>` を受ける内部 helper を 1 つ置く。helper は `SerializationException` と `IllegalArgumentException` を item mismatch として記録して skip する。`CancellationException`、`Error`、その他の programmer failure は捕捉しない。

decode だけを helper に置く方式では、invalid datetime、numeric ID、bell variation の必須値欠落が mapper で一覧全体を落とすため採用しない。逆に全 `Exception` を捕捉する方式は implementation bug を schema drift として隠すため採用しない。

### 3. Repository が endpoint label を mapper に渡す

（agent 仮決め）同じ response entity を複数 endpoint が共有するため、実際の production call path を知る repository が stable endpoint label を mapper に渡す。`creator.listFollowing` と entity を共有する `creator.listPixiv` / `creator.listRecommended` も tolerant path を使い、誤った endpoint label を記録しない。

### 4. bell の必須 variation field は明示的に検証する

（agent 仮決め）`!!` を `requireNotNull` に置き換え、既知 type の欠落を `IllegalArgumentException` として共通 decoder に処理させる。unknown type も同じ item mismatch として skip・診断する。既存の正常 variation の domain mapping は変更しない。

### 5. skip 通知は公開 per-call callback と Napier warning にする

（ユーザー確認済み）`plan.listSupporting` を含む部分成功を caller が識別できる明示シグナルを追加する。（agent 仮決め）各対象 list method に `(FanboxListItemSchemaMismatch) -> Unit` を必須引数とする additive overload を追加する。immutable public event は endpoint と index path だけを持つ。共通 decoder は values と mismatch events を call-local internal result として repository から返し、public `Fanbox` method は suspend call が元の caller coroutine context へ戻った後に callback を同期的に呼び、callback 完了後に value を返す。これにより event loss、並行 request の correlation ambiguity、IO dispatcher 上の UI callback を作らない。既存 method と return type は維持する。

instance-wide `SharedFlow` は bounded buffer の event loss と request correlation ambiguity があるため採用しない。result wrapper は既存 method の return type を破壊するため採用しない。callback 自身が例外を投げた場合は caller code の failure としてそのまま伝播し、schema mismatch として skip しない。

`plan.listSupporting` は active support と未加入の誤認を避けるため、既存 no-callback `getSupportedPlans()` を strict に保つ。同じ `plan.listSupporting` route に concrete item list を返す legacy-strict Ktorfit method と raw item list を返す tolerant Ktorfit method を内部で用意する。既存 public method は strict method を使い、ContentNegotiation / #17 exception pipeline に actual response metadata 付き `SchemaMismatch` を生成させる。callback overload だけが tolerant method を使い、正常 item と callback event を返す。他の list method の既存 overload は issue の主目的どおり tolerant behavior と Napier warning を使い、callback overload は機械的な観測が必要な caller に提供する。

strict entity は domain mapper が要求する numeric user ID も constructor で検証する。これにより JSON 型としては string でも domain mapping できない item を ContentNegotiation 境界で失敗させ、actual response metadata を持つ `SchemaMismatch` へ変換する。

### 6. raw fragment は既存 sanitizer 契約を再利用する

（ユーザー確認済み: issue の raw JSON 断片要件）Napier warning は endpoint と index path を常に含め、raw fragment は `logLevel != NONE` の場合だけ追加する。（agent 仮決め）`JsonElement.toString()` と既存の full-string sanitizer は使用せず、JSON tree を辿って credential key の値を構造的に `[REDACTED]` へ置換し、character を上限まで append する bounded renderer を使う。既存 sanitizer は bounded output に対する defense-in-depth としてだけ適用する。warning と public event に exception message / throwable は含めない。

### 7. pagination の保証は既存挙動の維持に限定する

（agent 仮決め）outer `nextUrl` が JSON string / null として decode できることまでは strict envelope の責務とする。query の `limit` / `page` / `offset` が semantic に妥当かは既存 mapper の挙動を維持し、この change で typed `SchemaMismatch` へ統一しない。item isolation と無関係な pre-existing behavior を新しい invariant にしないためである。

## Risks / Trade-offs

- [callback が例外を投げると list method も失敗する] → caller context 上の consumer code failure として伝播する契約を KDoc に明記する。
- [同じ route の strict/tolerant Ktorfit method が drift し得る] → query と annotation の parity を production API test で固定する。
- [item fragment には credential 以外の FANBOX / user data が残り得る] → raw fragment を明示 logging 時だけ出し、既定 `NONE` では endpoint と path のみ記録する。
- [shared creator entity の変更が listPixiv / listRecommended にも及ぶ] → repository から endpoint label を渡し、既存 golden tests と targeted tests で正常 mapping を維持する。
- [outer schema drift は引き続き一覧全体を失敗させる] → item isolation の境界を明確に保つため意図した挙動とし、production HTTP test で区別する。

## Migration Plan

内部 entity と mapper wiring、public event、callback overload を同一 release で追加する。既存 `getSupportedPlans()` は strict failure を維持するため migration は不要であり、部分成功が必要な consumer だけ callback overload へ opt-in する。他の対象 list method で callback event が必要な consumer は additive overload へ移行する。rollback はこの change の commit を revert し、従来の whole-response decode に戻す。

## Open Questions

なし。per-call callback、`getSupportedPlans()` の strict default、その他の agent 仮決めは PR description の「人間に確認してほしいこと」へ転記する。
