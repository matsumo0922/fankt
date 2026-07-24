## Context

現在の `Fanbox.addComment` は `rootCommentId` と `parentCommentId` を非 null で要求し、ルートコメントでは呼び出し側が `FanboxCommentId.EMPTY`（`"0"`）を渡す。repository は値の意味を区別せず両プロパティを常に JSON へ出力するため、公開 API が実際の FANBOX API の要求より強い制約を持っている。

この変更は Issue #37 のうちコメント投稿だけを独立させる。FANBOX 実環境での確認が必要なため、ID 型全体の移行や PixiView のモデル移行とは別の変更として検証可能にする。

## Goals / Non-Goals

**Goals:**

- ルートコメントを `rootCommentId = null`、`parentCommentId = null` で表現する。
- null の ID を JSON へ出力せず、返信時の ID は従来どおり送信する。
- リクエスト形状の自動テストと FANBOX 実環境の確認手順を持つ。
- 後続変更で `FanboxCommentId.EMPTY` を安全に削除できる状態にする。

**Non-Goals:**

- `FanboxCommentId.EMPTY` 自体の削除は行わない。
- コメント ID 型の value class 化は行わない。
- FANBOX のコメントモデルやレスポンス mapping は変更しない。
- PixiView 以外の消費者に対する互換レイヤーは提供しない。

## Decisions

### 1. 2 つの親 ID を nullable な公開引数にする（ユーザー確認済み）

`Fanbox.addComment` から repository まで `FanboxCommentId?` をそのまま伝播する。ルート投稿は両方 null、返信投稿は両方 non-null を標準の呼び出し方とする。

代案として overload を追加する方法は、ルートと返信の組み合わせを型で分けられる一方、既存 API を残して番兵値利用を温存する。Issue #37 は v0.1.0 の破壊的整理を目的とするため、単一 API の nullable 化を選ぶ。

### 2. null のプロパティだけを JSON から省略する（高リスク・要人間確認）

`buildJsonObject` 内で各値が non-null の場合だけ `put` する。`JsonNull` を送る方法はサーバーが「未指定」と異なる値として扱う可能性があるため採用しない。

公開 API では不正な片側 null を新たに例外化しない。クライアントは指定された各値を忠実に送信し、標準シナリオとして「両方 null」と「両方 non-null」をテストする。追加の組み合わせ制約は FANBOX API の実測根拠が得られた場合に別途定める。

### 3. 自動テストと実環境確認を分離する（agent 仮決め）

MockEngine 等を用いた repository test で送信 JSON を検査し、ルートでは両プロパティが存在しないこと、返信では両値が存在することを固定する。サーバーがその形状を受理するかは自動テストでは保証できないため、認証済みの FANBOX 実環境でルート投稿と返信投稿を確認する。

### 4. ABI dump を同じ変更で更新する（agent 仮決め）

nullable 化は公開署名を変更するため、Android/KLIB の API dump を再生成し、通常の ABI 検証を通す。互換 shim は設けない。

## Risks / Trade-offs

- [FANBOX がルート投稿で ID 省略を受理しない] → 実装前後に実環境で確認し、受理されない場合は推測で代替値を固定せず API 観測結果に基づいて設計を更新する。
- [片側だけ null の呼び出しがサーバーエラーになる] → 公開 API では未検証の制約を追加せず、標準の 2 シナリオだけをサポート契約として明記する。
- [公開署名変更で既存消費者がコンパイルできない] → v0.1.0 の破壊的変更としてリリースし、PixiView の呼び出しを `EMPTY` から null へ連動移行する。
- [実環境テストがコメントを作成する] → テスト用投稿を使用し、作成したコメントを確認後に削除できる運用で実施する。

## Migration Plan

1. repository test でルートと返信の期待する JSON 形状を追加する。
2. `Fanbox.addComment` と repository の 2 引数を nullable にし、null を省略する。
3. API dump を更新し、unit test と ABI 検証を実行する。
4. テスト用 FANBOX 投稿でルートコメントと返信コメントを実行し、双方の成功を記録する。
5. PixiView のルート投稿呼び出しを `FanboxCommentId.EMPTY` から null へ移行する。
6. 問題がある場合はこの変更を戻し、従来の番兵値送信へロールバックする。

## Open Questions

- （高リスク・要人間確認）FANBOX 実環境は両 ID の省略をルートコメントとして受理するか。実装完了条件として確認し、成功が記録されるまで merge しない。
