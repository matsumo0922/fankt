# article-embed-blocks Specification

## Purpose

article 投稿の embed 参照を公開 block と provider URL へ変換し、未知・解決不能な埋め込みを raw JSON 付きで欠落なく返す。

## Requirements

### Requirement: embed 参照を公開 block に変換する

システム SHALL article の `embed` block が参照する `embedMap` entry を解決し、service provider と正規化した content ID を持つ公開 `Article.Block.Embed` を block 順序どおりに返さなければならない。Trace: Issue #28「embedMap / embedId / Embed variant を追加する」。

#### Scenario: contentId を持つ既知 provider を変換する

- **WHEN** `embedId` が `contentId` を持つ既知 provider の entry を参照する article 投稿を取得する
- **THEN** システムは同じ provider と content ID を持つ `Article.Block.Embed` を該当位置に返す

#### Scenario: videoId を content ID に正規化する

- **WHEN** `embedId` が `videoId` を持つ既知 provider の entry を参照する
- **THEN** システムは `videoId` の値を `Article.Block.Embed.contentId` として返す

#### Scenario: videoId と contentId が共存する

- **WHEN** referenced embed entry が異なる `videoId` と `contentId` を同時に持つ
- **THEN** システムは一次資料の precedence に従い `videoId` を `Article.Block.Embed.contentId` として返す

#### Scenario: embedMap entry の id がない

- **WHEN** `embedId` が `id` field を持たない既知 provider の entry を参照する
- **THEN** システムは map key で entry を解決し、同じ provider と正規化した content ID を持つ `Article.Block.Embed` を返す

### Requirement: 既知 provider の URL を復元する

公開 `Article.Block.Embed` SHALL twitter、youtube、vimeo、soundcloud、google_forms、fanbox の content ID から provider 固有 URL を決定的に復元しなければならない。fanbox は HTTP redirect の source URL を返す。Trace: Issue #28 の provider 別 URL 復元受け入れ条件。

#### Scenario: 主要 provider の URL を復元する

- **WHEN** 各既知 provider と content ID を持つ `Article.Block.Embed` の URL helper を評価する
- **THEN** twitter、youtube、vimeo、soundcloud、google_forms、fanbox について Issue #28 と設計で定義した URL を返す

#### Scenario: 公開 constructor で未知 provider を作る

- **WHEN** 未知 provider を持つ `Article.Block.Embed` の URL helper を評価する
- **THEN** helper は URL を捏造せず null を返す

### Requirement: 未知または解決不能な embed を raw fallback にする

システム SHALL 未知 provider、content ID 欠落、または `embedMap` 参照欠落を黙って破棄せず、原因を調査できる raw JSON を持つ `Article.Block.Unknown` として返さなければならない。Trace: Issue #28「未知の serviceProvider は Block.Unknown(raw)」および本文 block 欠落を防ぐ非退行 invariant。

#### Scenario: 未知 provider を変換する

- **WHEN** `embedId` が未知 provider の entry を参照する
- **THEN** システムは referenced embed entry の raw JSON を持つ `Article.Block.Unknown` を該当位置に返す

#### Scenario: embedMap の参照先がない

- **WHEN** embed block の `embedId` に対応する entry が `embedMap` にない
- **THEN** システムは block の raw JSON を持つ `Article.Block.Unknown` を該当位置に返す

#### Scenario: content ID がない

- **WHEN** referenced embed entry が `contentId` と `videoId` のどちらも持たない
- **THEN** システムは referenced embed entry の raw JSON を持つ `Article.Block.Unknown` を該当位置に返す

#### Scenario: service provider がない

- **WHEN** `embedId` が `serviceProvider` を持たない entry を参照する
- **THEN** システムは referenced embed entry の raw JSON を持つ `Article.Block.Unknown` を該当位置に返す

#### Scenario: 既知の非 embed block の参照先がない

- **WHEN** image、file、または url_embed block の参照 ID に対応する map entry がない
- **THEN** システムは block の raw JSON を持つ `Article.Block.Unknown` を該当位置に返す

### Requirement: synthetic fixture の保証範囲を限定する

synthetic fixture test SHALL entity decode、embedId 参照、provider 別 URL 復元、raw fallback、および production call path の内部契約だけを証明し、本番 `embedMap` schema 互換性を証明したものとして扱ってはならない。Trace: Issue #28 の synthetic fixture 使用に関するユーザー承認 comment。

#### Scenario: synthetic fixture を production path で変換する

- **WHEN** synthetic embed response を公開 `Fanbox.getPostDetail` から取得する test を実行する
- **THEN** ContentNegotiation、entity、mapper を通って主要 provider と fallback の内部契約を満たし、fixture source と PR に provenance と未検証範囲を記録する
