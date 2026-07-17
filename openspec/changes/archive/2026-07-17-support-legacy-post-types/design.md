## Context

`FanboxPostMapper` は #26 で `post.type` を本文 variant の正本にしたが、`text` / `video` / `entry` は現在も `Body.Unknown(type, rawBodyJson)` にする。Issue #27 は `body.text`、nested `body.video`、`body.html` を公開 variant へ昇格し、旧形式投稿の本文を consumer が表示できるようにする。

認証済み `post.listHome` 50ページと、その古い側から150件の `post.info` を4秒間隔で探索して全200 requestがHTTP 200だったが、詳細は article 141件 / image 9件で対象3 typeは得られなかった。原 response はユーザー指示によりGit管理外・owner-onlyで永続化している。gallery-dl の現行 FANBOX extractor は `content_body["video"]` を embed object として処理し、`serviceProvider` と `videoId` または `contentId` を読むため、Issue本文の nested video shape と整合する。

公開 sealed subtype の追加は PixiView-KMP など複数 consumer の exhaustive `when` に影響するため、実装前に独立反証を行う。

## Goals / Non-Goals

**Goals:**

- `text` / `video` / `entry` を `post.type` に従って `Body.Text` / `Body.Video` / `Body.Html` へ変換する。
- schemaを実測できたbodyだけを専用variantへ変換し、未検証shapeは既存のraw fallbackを保って投稿詳細の全損を防ぐ。
- YouTube / Vimeo の video URL helper と、entry HTML の caller-side sanitization boundary を公開 API に示す。
- actual-response-derived な envelope / field source と合成部分を区別した hybrid fixture で、production call path を固定する。

**Non-Goals:**

- PixiView-KMP の dependency bump、描画、HTML sanitizer の選定・実装。
- FANBOX から取得できなかった `text` / `video` / `entry` 完全 payload の本番 schema 互換性を主張すること。
- unknown provider の video URL を推測すること、または video をダウンロードすること。
- private capture、SESSID、有料本文をリポジトリへ追加すること。

## Decisions

### 1. type 選択後に既存 `PostBody` を decode し、branch ごとに変換可否を判定する

（agent 仮決め、独立反証 F1・F4 により修正）`post.body` は引き続き `JsonElement?` で保持し、`FanboxPostDetailEntity.Body.PostBody` に nullable `video` と `html` を加える。nested `Video` の `serviceProvider` / `videoId` / `contentId` もnullable Stringとし、mapperは有効な文字列fieldが揃った場合だけ専用variantへ変換する。

video content ID はgallery-dlと既存article embed mapperに合わせて `videoId ?: contentId` で正規化する。両方が存在する場合は `videoId` を優先する。typeごとの別 body classはfield重複を減らす一方、article / image / file が共有する既存decodeとfixtureを分断するため採用しない。

（agent 仮決め、独立反証 F4 PARTIAL により明確化）`text` / `video` / `entry` だけは `post.type` 分岐後の mapper 内で `PostBody` decode と必須値抽出を行い、`SerializationException` をその branch 内で捕捉して元の `JsonElement` を持つ `Body.Unknown` へ変換する。誤JSON型をrepository境界まで漏らして `SchemaMismatch` に変換しない。`article` / `image` / `file` は既存のbranch decodeとrepository-level `SchemaMismatch` を維持する。

### 2. 未検証3 typeのshape mismatchは raw fallback にする

（agent 仮決め、独立反証 F4 により修正）`text` / `video` / `entry` はtype自体は既知でも完全な本番body schemaが未検証である。bodyがnull、必須property欠落、型不一致、またはnested shape不一致なら、投稿詳細全体を `SchemaMismatch` で失敗させず、typeとraw bodyを持つ `Body.Unknown` を返す。

実response由来schemaを持つ `article` / `image` / `file` は従来どおりfail-closedの `SchemaMismatch` を保つ。この非対称性は、未検証shapeをknown schemaとして扱って現行のgraceful fallbackより退行させないための非退行invariantである。

### 3. Video は provider文字列を保持し、URL helper は youtube / vimeo だけを復元する

（agent 仮決め）公開 `Body.Video(serviceProvider, videoId)` はIssue指定どおり生のprovider文字列を保持し、computed `url: String?` は `youtube` と `vimeo` だけに対応する。未知providerをrejectすると投稿詳細全体が失敗し、URLを推測すると誤リンクになるため、variantは返しつつURLだけnullにする。

gallery-dl は soundcloud 等も共通embedとして扱うが、Issue #27 がtop-level videoで明示するURLはyoutube / vimeoだけなので、他provider追加は実responseまたは別要件を得てから行う。

### 4. Html は受信文字列を保持し、library内でsanitizeしない

（ユーザー確認済み: Issue本文）`Body.Html(html)` はHTMLを変更せず保持する。公開KDocでuntrustedでありrender前のsanitizeがcaller責務と明記する。fankt側でsanitizeするとconsumerごとのrendering policyを固定し、元データを失うため採用しない。

### 5. hybrid fixture は actual component と composed component を名前とコメントで分ける

（ユーザー確認済み、独立反証 F3・F7 により明確化）既存のactual-response-derived `postInfoImage` envelopeと `body.text` string shape、article `urlEmbedMap.html` のHTML string shapeをsourceとし、top-level typeとtype固有の `text`、nested `video`、`html` 配置だけを明示的に合成する。provenance記録のない `postInfoText` をactual envelopeの根拠には使わない。fixtureはactual responseそのものと呼ばず、コメントに次を記録する。

- actual-response-derived: `postInfoImage` の `post.info` envelopeと `body.text` のstring shape、article `urlEmbedMap.html` のHTML string decode形。
- composed from Issue / primary source: top-level typeとnested `body.video` の配置、entryの `body.html` 配置。
- unverified: FANBOX本番の対象3 type完全payload、optional field、provider集合。

テストは mapper unit だけでなく、MockEngineから公開 `Fanbox.getPostDetail` を呼び、ContentNegotiation→entity→mapperのproduction配線を3 typeすべてで通す。期待値はfixtureから導出せず独立に記述する。

### 6. consumer migration は fankt PR で可視化し、実装は別issueへ残す

（ユーザー確認済み: Issue本文）新しいsealed subtypeはadditiveだが、consumerのexhaustive `when` にはsource-breakingとなる。fanktのPRにbreaking impactを記載し、PixiView-KMPのfallback / renderer対応は連動issueで行う。このPRでconsumer repoを変更しない。

（agent確認済み、独立反証 F6）既知consumerのPixiView-KMPは `FanboxPostDetail` をprocess内 `mutableMap` とCompose stateにcacheするが、durable storeへserializeする経路は確認できない。translation requestでは一時的にserializeするため新variantの変換対応はconsumer追従対象だが、rollback時の永続cache migrationは不要。未知consumerが新subtypeを永続化した場合の旧libraryへのforward compatibilityは保証しない。

## Risks / Trade-offs

- [hybrid fixtureは対象3 typeの完全な実schemaを証明しない] → actual / composed / unverifiedをfixtureとPRに明記し、production call pathの内部契約へ保証を限定する。
- [nullable shared `PostBody` はbranch必須値を型だけで表現できない] → mapperでtypeごとの変換条件を明示し、欠損・型不一致をraw fallback testで固定する。
- [sealed subtype追加でconsumerのexhaustive分岐が壊れる] → PRでBREAKINGと明記し、PixiView-KMP対応とfankt releaseを同期する。
- [untrusted HTMLをそのまま返すとunsanitized描画がXSS相当の表示リスクになる] → KDocとspecでcaller sanitization責務を明示し、fankt内では描画APIを提供しない。
- [未知video providerでは表示URLがない] → provider / videoIdは保持してconsumerのfallback表示を可能にし、URLを捏造しない。

## Migration Plan

1. fanktでentity、mapper、公開model、fixture / production-path / serialization / fallback test、READMEのprovenance規約を同一PRで変更する。
2. PRにsealed subtype追加、hybrid fixtureの保証範囲、HTML trust boundary、PixiView-KMPの追従要否を記載する。
3. fankt releaseとPixiView-KMPの新variant対応を同期し、consumerが未対応のままdependencyを更新しない。

rollbackは本PRをrevertして3 typeを `Body.Unknown` へ戻す。DB migration、server state、保存形式の不可逆変更はない。新subtypeとしてserialize済みの値を旧libraryでdecodeするforward compatibilityは保証しない。

## Open Questions

なし。
