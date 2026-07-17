## Context

現在の article body entity は `blocks` を field ごとの nullable 値として decode するが、`embedId` と `embedMap` を持たない。mapper も nullable field の有無で分岐し、どの既知 field もない block を warning とともに破棄する。Issue #28 は embed の復元に加え、未知 provider を raw 保持する `Article.Block.Unknown` に落とすことを要求する。

FANBOX home の認証済み探索では article 104 件を含む 109 投稿を確認したが、embed block と embedMap は得られなかった。gallery-dl の FANBOX extractor は `serviceProvider` と `contentId` または `videoId` を読み、twitter / youtube / vimeo / soundcloud / google_forms / fanbox を URL に変換している。本 change の fixture は Issue comment で承認された synthetic data であり、本番 schema 互換性を証明しない。

## Goals / Non-Goals

**Goals:**

- article block の `type` を正本にして既知 block を map する。
- `embedId` から `embedMap` を解決し、既知 provider と正規化した content ID を公開 `Embed` で返す。
- provider 別 URL を純粋な helper で復元する。
- 未知 provider、未知 block type、解決不能な参照を raw JSON 付き `Unknown` として順序を保って返す。
- 公開 `Fanbox.getPostDetail` から ContentNegotiation、entity decode、mapper を通る経路を test する。

**Non-Goals:**

- FANBOX 本番の `embedMap` schema 互換性を synthetic fixture から主張すること。
- helper 内で network request を実行して FANBOX redirect の最終 URL を取得すること。
- 埋め込み先の取得、表示、HTML 展開、provider 固有 metadata の公開。
- PixiView-KMP など downstream consumer の同時変更。

## Decisions

### 1. block type を分岐の正本にし、decode 前の JsonObject を保持する

（agent 仮決め）`PostBody.blocks` は decode 前の `JsonObject` の list とし、mapper が各要素を nested `Block` entity へ decode する。`Block` entity には Issue 指定の `embedId` を追加する。これにより `p` / `header` / `image` / `file` / `url_embed` / `embed` を `block.type` で一意に分岐しながら、unknown field を `ignoreUnknownKeys` で失う前の JSON を fallback に渡せる。

custom serializer で typed field と raw JSON を同居させる案は、entity 専用 serializer と descriptor を増やす割に公開契約の利点がないため採用しない。従来の nullable field 優先順を維持する案は、type と payload が競合したとき別 variant を選ぶため採用しない。

### 2. embed entity は contentId と videoId を受け、domain では contentId に正規化する

（agent 仮決め）`embedMap` entry は `id`、`serviceProvider`、nullable `contentId`、nullable `videoId` を decode する。mapper は `contentId ?: videoId` を domain `Embed.contentId` に正規化する。Issue 本文は contentId を指定する一方、一次資料の gallery-dl は一部 provider の `videoId` fallback を実装しているためである。両方がない entry は解決不能として `Unknown` にする。

### 3. Unknown は fallback 原因の情報を含む raw JSON を1つ保持する

（agent 仮決め）公開 `Article.Block.Unknown(rawJson: String)` を追加する。未知 block type または map 参照欠落では block の raw JSON、未知 provider または content ID 欠落では referenced embed entry の raw JSONを保持する。JSON を合成せず API 由来の fragment をそのまま正規化して保持するため、呼び出し側は原因に応じた payload を調査できる。

既知 block の空 text は従来どおり出力しない。既知 image / file / url_embed の参照欠落は従来は消えていたが、block 欠落を防ぐ invariant に合わせて `Unknown` へ変換する。

### 4. URL helper は pure nullable property とする

（agent 仮決め）`Embed.url: String?` は provider が既知なら以下を返し、それ以外は null を返す。mapper は未知 provider を `Embed` にしないが、公開 constructor から未知値を作れるため nullable とする。

- twitter: `https://twitter.com/_/status/{contentId}`
- youtube: `https://www.youtube.com/watch?v={contentId}`
- vimeo: `https://vimeo.com/{contentId}`
- soundcloud: `https://soundcloud.com/{contentId}`
- google_forms: `https://docs.google.com/forms/d/e/{contentId}/viewform?usp=sf_link`
- fanbox: `https://www.pixiv.net/fanbox/{contentId}`

`fanbox` は gallery-dl と同じ redirect source URL を返す。library model の getter から network I/O を起こさず、実際の HTTP client が必要に応じて redirect を解決する。

### 5. synthetic fixture は production path の内部契約に限定する

（ユーザー確認済み）既存の actual-response-derived article fixture を test 内で synthetic embed body に置換し、source 上に provenance を記載する。主要6 provider、unknown provider、unknown block、参照欠落、`videoId` fallback を確認する。少なくとも主要 provider scenario は公開 `Fanbox.getPostDetail` から通し、mapper 単体だけで wiring を証明したことにしない。

## Risks / Trade-offs

- [synthetic fixture は実 `embedMap` schema を証明しない] → PR と fixture source に限定保証を明記し、実 response 入手時に actual-response-derived fixture を追加または置換する。
- [公開 sealed subtype 追加で exhaustive consumer が source break する] → breaking impact を PR に明記し、consumer 追従は release 後の dependency update と同時に行う。
- [rawJson の意味が fallback 原因で block / embed entry のどちらかになる] → KDoc で選択規則を明記し、合成 JSON を API raw と誤認させない。
- [fanbox helper は redirect 後 URL ではない] → redirect source URL であることを KDoc と PR に明記し、network I/O は helper の責務外とする。
- [contentId の path/query 文字を percent-encode しない] → gallery-dl と Issue の復元形式に合わせて opaque fragment として連結する。実 response で reserved character が確認された場合に encoding 契約を再検討する。

## Migration Plan

1. entity、domain model、mapper、serialization test、synthetic production-path fixture test を同一 PR で追加する。
2. breaking impact、fixture provenance、fanbox redirect source URL、本番 schema 未検証を PR description に記録する。
3. release 後、exhaustive `Article.Block` consumer は dependency update と同時に `Embed` / `Unknown` 分岐を追加する。

Rollback は本 PR を revert する。DB や persisted migration はなく、旧 consumer が新 subtype の serialized value を読む forward compatibility は保証しない。

## Open Questions

- 実 response を取得できていないため、provider ごとの `contentId` / `videoId` 使用実態と追加 field は未確認である。これは synthetic fixture の保証縮退として残し、実 response 取得時に検証する。
