## Context

`FanboxPostMapper` は #26 で `post.type` を本文 variant の正本にしたが、`text` / `video` / `entry` は現在も `Body.Unknown(type, rawBodyJson)` にする。Issue #27 は `body.text`、nested `body.video`、`body.html` を公開 variant へ昇格し、旧形式投稿の本文を consumer が表示できるようにする。

認証済み `post.listHome` 50ページと、その古い側から150件の `post.info` を4秒間隔で探索して全200 requestがHTTP 200だったが、詳細は article 141件 / image 9件で対象3 typeは得られなかった。原 response はユーザー指示によりGit管理外・owner-onlyで永続化している。gallery-dl の現行 FANBOX extractor は `content_body["video"]` を embed object として処理し、`serviceProvider` と `videoId` または `contentId` を読むため、Issue本文の nested video shape と整合する。

公開 sealed subtype の追加は PixiView-KMP など複数 consumer の exhaustive `when` に影響するため、実装前に独立反証を行う。

## Goals / Non-Goals

**Goals:**

- `text` / `video` / `entry` を `post.type` に従って `Body.Text` / `Body.Video` / `Body.Html` へ変換する。
- 新しい known type の欠損・型不一致を raw fallback にせず、既存の `FanboxException.SchemaMismatch` production contractで返す。
- YouTube / Vimeo の video URL helper と、entry HTML の caller-side sanitization boundary を公開 API に示す。
- actual-response-derived な envelope / field source と合成部分を区別した hybrid fixture で、production call path を固定する。

**Non-Goals:**

- PixiView-KMP の dependency bump、描画、HTML sanitizer の選定・実装。
- FANBOX から取得できなかった `text` / `video` / `entry` 完全 payload の本番 schema 互換性を主張すること。
- unknown provider の video URL を推測すること、または video をダウンロードすること。
- private capture、SESSID、有料本文をリポジトリへ追加すること。

## Decisions

### 1. type 選択後に既存 `PostBody` を decode し、branch ごとに必須値を要求する

（agent 仮決め）`post.body` は引き続き `JsonElement?` で保持し、`FanboxPostDetailEntity.Body.PostBody` に nullable `video` と `html` を加える。nested `Video` の `serviceProvider` / `videoId` は non-null String とし、mapper の `text` / `video` / `entry` branch は対応 property が null なら `SerializationException` を投げる。

typeごとの別 body class は field 重複を減らす一方、article / image / file が共有する既存decodeとfixtureを分断する。この変更ではbranchの必須値検証を明示し、domain variant選択を単一の `when (post.type)` に保つ。

### 2. 新しい6 typeすべてを known body として fail-closed に扱う

（agent 仮決め）`text` / `video` / `entry` の body が null、必須 property 欠落、または型不一致なら `Body.Unknown` に戻さず、既存 repository 境界で `FanboxException.SchemaMismatch(endpoint = post.info, status = 200)` に変換する。既知 schema の破損を「未対応 type」と誤認して本文を黙って失わないためである。

未知 `post.type` だけは既存どおり `Body.Unknown(type, rawBodyJson)` を返す。

### 3. Video は provider文字列を保持し、URL helper は youtube / vimeo だけを復元する

（agent 仮決め）公開 `Body.Video(serviceProvider, videoId)` はIssue指定どおり生のprovider文字列を保持し、computed `url: String?` は `youtube` と `vimeo` だけに対応する。未知providerをrejectすると投稿詳細全体が失敗し、URLを推測すると誤リンクになるため、variantは返しつつURLだけnullにする。

gallery-dl は soundcloud 等も共通embedとして扱うが、Issue #27 がtop-level videoで明示するURLはyoutube / vimeoだけなので、他provider追加は実responseまたは別要件を得てから行う。

### 4. Html は受信文字列を保持し、library内でsanitizeしない

（ユーザー確認済み: Issue本文）`Body.Html(html)` はHTMLを変更せず保持する。公開KDocでuntrustedでありrender前のsanitizeがcaller責務と明記する。fankt側でsanitizeするとconsumerごとのrendering policyを固定し、元データを失うため採用しない。

### 5. hybrid fixture は actual component と composed component を名前とコメントで分ける

（ユーザー確認済み）既存のactual-response-derived `postInfoText` envelope、`postInfoImage` / article / urlEmbed の実測field shapeをsourceとし、type固有の `text`、nested `video`、`html` だけを明示的に合成する。fixtureはactual responseそのものと呼ばず、コメントに次を記録する。

- actual-response-derived: `post.info` envelope、`body.text` のstring shape、HTML stringとしてのdecode形。
- composed from Issue / primary source: top-level typeとnested `body.video` の配置、entryの `body.html` 配置。
- unverified: FANBOX本番の対象3 type完全payload、optional field、provider集合。

テストは mapper unit だけでなく、MockEngineから公開 `Fanbox.getPostDetail` を呼び、ContentNegotiation→entity→mapperのproduction配線を3 typeすべてで通す。期待値はfixtureから導出せず独立に記述する。

### 6. consumer migration は fankt PR で可視化し、実装は別issueへ残す

（ユーザー確認済み: Issue本文）新しいsealed subtypeはadditiveだが、consumerのexhaustive `when` にはsource-breakingとなる。fanktのPRにbreaking impactを記載し、PixiView-KMPのfallback / renderer対応は連動issueで行う。このPRでconsumer repoを変更しない。

## Risks / Trade-offs

- [hybrid fixtureは対象3 typeの完全な実schemaを証明しない] → actual / composed / unverifiedをfixtureとPRに明記し、production call pathの内部契約へ保証を限定する。
- [nullable shared `PostBody` はbranch必須値を型だけで表現できない] → mapperでtypeごとの必須propertyを明示し、各欠損をSchemaMismatch testで固定する。
- [sealed subtype追加でconsumerのexhaustive分岐が壊れる] → PRでBREAKINGと明記し、PixiView-KMP対応とfankt releaseを同期する。
- [untrusted HTMLをそのまま返すとunsanitized描画がXSS相当の表示リスクになる] → KDocとspecでcaller sanitization責務を明示し、fankt内では描画APIを提供しない。
- [未知video providerでは表示URLがない] → provider / videoIdは保持してconsumerのfallback表示を可能にし、URLを捏造しない。

## Migration Plan

1. fanktでentity、mapper、公開model、fixture / production-path / serialization test、READMEのprovenance規約を同一PRで変更する。
2. PRにsealed subtype追加、hybrid fixtureの保証範囲、HTML trust boundary、PixiView-KMPの追従要否を記載する。
3. fankt releaseとPixiView-KMPの新variant対応を同期し、consumerが未対応のままdependencyを更新しない。

rollbackは本PRをrevertして3 typeを `Body.Unknown` へ戻す。DB migration、server state、保存形式の不可逆変更はない。新subtypeとしてserialize済みの値を旧libraryでdecodeするforward compatibilityは保証しない。

## Open Questions

なし。
