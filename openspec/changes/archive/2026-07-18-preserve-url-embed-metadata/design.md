## Context

現在の `urlEmbedMap` entry entity は `id` / `type` / `html` / `postInfo` だけを持ち、`url` を `ignoreUnknownKeys` により破棄する。公開 `Article.Block.Link` も `html` と `post` だけなので、`default` link は値を失う。さらに保存済み actual response の観測では `default` は `url` だけ、`html.card` は `html` だけ、`fanbox.post` は `postInfo` だけを持ち、非該当 field 自体が欠落する。現在の `explicitNulls = false` は欠落した nullable field を null として decode するが、各 nullable field に default null を明示して transport contract も同じ許容性を表す。

保存済み private capture では `default` 2件、`html.card` 15件、`fanbox.post` 18件、`html` 26件を観測した。raw response と identity/free-form values は repository、OpenSpec artifact、reviewer input に含めない。

## Goals / Non-Goals

**Goals:**

- `url_embed` の type、URL、HTML、post を公開 model に保持する。
- type ごとに非該当 field が欠落する actual shape を許容する。
- 未知 type でも既知 metadata を捨てず forward-compatible に返す。
- 公開 HTML trust boundary と旧 serialized Link decode 互換を明示する。
- actual-response-derived な匿名化 fixture と公開 production call path で配線を証明する。

**Non-Goals:**

- HTML の sanitize、parse、render を library 内で行うこと。
- URL 先の fetch、OGP 展開、redirect 解決を行うこと。
- PixiView の Link 描画分岐を変更すること。
- FANBOX が返す type の enum を閉じた集合として拒否すること。

## Decisions

### 1. transport Url は type を正本にし、4 metadata field を独立に保持する

（agent 仮決め）`Url` entity に nullable `url` を追加し、実レスポンスで欠落する `html` / `postInfo` と合わせて default null にする。mapper は `url.type` に基づいて field を消去・合成せず、type、url、html、mapped post をそのまま公開 Link に渡す。未知 type でも API が返した既知 field を保持でき、type ごとの field 排他性を library が捏造しない。

type ごとに sealed subtype を作る案は、未知 type と将来の field 組み合わせを raw fallback なしで表現しにくく、Issue が単一 `Block.Link` の拡張を指定しているため採用しない。

### 2. public Link は既存2 fieldを先頭に保ち、新fieldに compatibility defaultと非衝突 serial nameを置く

（agent 仮決め、独立反証 F1 により修正）primary constructor は既存の `html` / `post` を先頭に保ち、`@SerialName("linkType") val type: String = "unknown"` と `url: String? = null` を末尾へ追加する。sealed `Block` の既定 class discriminator は `type` なので、property の serialized key を分離して衝突を避ける。これにより既存 Kotlin source の positional / named constructor と旧 serialized Link value の decode を維持する。JVM の既コンパイル済み2引数 constructor ABI は field 追加で変わり得るため、release note / PR では binary compatibility を保証せず、downstream の再compileを要求する。

type と url を先頭へ並べる案は Issue の列挙順には近いが、既存 positional constructor を source break させるため採用しない。secondary constructor で旧署名を維持する案は、named/default overload の曖昧さと API surface を増やすため採用しない。

### 3. HTML は未信頼文字列として変更せず保持する

（agent 仮決め）`html` と `html.card` の HTML は parse や sanitize をせず同じ文字列を返す。KDoc は `default`、`html`、`html.card`、`fanbox.post` の意味と、HTML を sanitize してから描画する caller 責務を記載する。library 内 sanitize は platform ごとの差と UI policy を持ち込むため non-goal とする。

### 4. 保存済み actual response から最小 fixture を匿名化する

（ユーザー確認済み）前回の低速探索で private directory に永続保存した response を再利用する。fixture は actual envelope と type-specific field presence を基に、identity、free-form text、URL host/query/path token を whole-value placeholder へ置換し、mapper が消費しない unknown field を除く。private-value fixed-string scan と raw を見ない独立 staged-diff privacy review を通す。

4 type が単一 response に共存することを要件にせず、`default` / `html` / `html.card` / `fanbox.post` の各 actual response 由来 fragment を既存 actual-response-derived article envelope へ分離配置する。各 fragment の type / field presence / scalar-vs-object representation は actual evidence と一致させ、値だけを匿名化する。

## Risks / Trade-offs

- [公開 data class field 追加で binary consumer が再compileを要する] → PR に breaking impact を明記し、PixiView の dependency update と描画対応は別 issue に残す。
- [HTML をそのまま公開する] → KDoc で untrusted と明記し、library 内では描画しない。
- [actual fragment を別 envelope へ配置するため完全な元 response そのものではない] → fixture comment と PR に envelope / fragment provenance を分け、field shape と production decode path の保証に限定する。
- [未知 type の field semantics は不明] → field を消去・合成せず、type と受信 metadata をそのまま返す。
- [private capture の値が fixture に混入する] → source values を出力しない fixed-string scanと独立 privacy reviewを commit 前 gate にする。

## Migration Plan

1. entity / public model / mapper / KDoc と fixture tests を同一 PR に含める。
2. downstream の再compile、HTML sanitize、PixiView UI non-goal を PR に記載する。
3. merge 後に delta spec を main spec へ同期して change を archive する。

Rollback は PR の merge commit を revert する。DB、network endpoint、persistent migration はない。

## Open Questions

なし。observed field presence、privacy boundary、consumer scope は保存済み evidence と Issue #29 で確定している。
