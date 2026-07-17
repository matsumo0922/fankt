## Context

現在の `FanboxPostDetailEntity` は `post.body` を全 type 共通の構造へ decode し、`FanboxPostMapper` は `blocks`、`images`、`files` の非空判定を独立した `if` で順に評価する。このため、レスポンスが明示する `post.type` と異なる variant が選ばれる余地があり、未対応 type は payload のない `Body.Unknown` になる。

Issue #26 は公開 sealed subtype の破壊的変更を含み、fankt と PixiView-KMP の複数 consumer に影響する。PixiView-KMP の変更は別対応とすること、実レスポンスを用意できない `video` / `entry` には synthetic fixture を使い保証範囲を PR に明記することはユーザー確認済みである。

## Goals / Non-Goals

**Goals:**

- `post.type` だけで `Article` / `Image` / `File` / `Unknown` を選択する。
- 未対応・未知 type の本文を lossless なJSON構造として受け取り、公開 model では再利用可能な文字列として保持する。
- 6 type の decode-to-map 契約と、未知 type の fail-safe fallback を自動テストで固定する。
- 公開 `Fanbox.getPostDetail` から ContentNegotiation、entity、mapper を通る production call path を少なくとも1つの Unknown scenario で確認する。

**Non-Goals:**

- `text`、`video`、`entry` の専用 domain variant と表示機能。
- `embedMap`、`urlEmbedMap`、header/styles の追加対応。
- PixiView-KMP の dependency bump、コンパイル修正、fallback UI。
- 実レスポンスが未取得の `video` / `entry` body schema との互換性保証。

## Decisions

### 1. entity は `post.body` を `JsonElement?` として保持する

（agent 仮決め）`FanboxPostDetailEntity.Body.body` は現在の型付き `Body?` ではなく `JsonElement?` を受け取る。type 判定前に全形式を1つの data class へ押し込めず、未知フィールドを含む任意のJSON object/array/primitiveを ContentNegotiation で受け取れるためである。本文の型付き構造は別名の internal serializable model として残す。

代替案の custom serializer は親 object の sibling である `post.type` を body property serializer から参照しにくく、type 判定責務が entity と mapper に分散するため採用しない。

### 2. mapper に production と同じ Json 設定を注入して既知 type だけ decode する

（agent 仮決め）`FanboxPostMapper` は `createFanboxJson()` で得る `Json` を依存として持ち、`article` / `image` / `file` の分岐内だけで `JsonElement` を typed body model へ decode する。variant 選択は単一の `when (post.type)` とし、payload の非空状態を type 選択には使わない。

既知 type の body が null の場合は、従来の「本文を解釈できない」挙動を保ち `Unknown(type, null)` とする。既知 type の非null bodyが必要フィールドを欠く場合、`FanboxPostRepository.getPostDetail` はmapperの `SerializationException` を捕捉し、成功済みresponseのstatus 200、endpoint `post.info`、sanitize・上限処理したbody fragmentを持つ `FanboxException.SchemaMismatch` へ変換する。これにより、decode位置をmapperへ移しても公開例外契約を維持する。

### 3. rawBodyJson は JsonElement の正規化表現とする

（agent 仮決め）`rawBodyJson` は `post.body` の元の空白・key順・escape表現を含むHTTPバイト列ではなく、decode 済み `JsonElement.toString()` とする。JSONとしての値は保持でき、KMP共通コードで追加のraw response capture機構を導入せずに済む。JSON null は Kotlin null とする。

### 4. `Unknown.type` はJSON field名を分離し、旧objectをdefault値で読む

（agent 仮決め）公開Kotlin propertyはIssueどおり `type` とする一方、serialization上は `@SerialName("postType")` を付ける。sealed `Body` が既定で使うclass discriminator `type` とpayload fieldの衝突を避け、既存variantのdiscriminator形式は変えない。

`type` のdefaultを `unknown`、`rawBodyJson` のdefaultをnullとする。これにより、payload fieldを持たない旧 `data object Unknown` のserialized valueを同じsubtype discriminatorからdecodeできる。repoと既知consumer PixiView-KMPを検索した範囲では `FanboxPostDetail` の永続serialize利用はないが、公開libraryの未知consumerに備えてround-tripとlegacy decodeをtestで固定する。

### 5. fixture provenance と保証範囲を分ける

（ユーザー確認済み）`article` / `image` / `file` / `text` は既存の実レスポンス由来 fixture を再利用する。`video` / `entry` は既存 fixture から type と body を明示的に差し替えた synthetic fixture とし、source code 上でも provenance を記載する。未知 type も分岐確認用の synthetic fixture とする。

README の一般規約には、issue で明示承認され、schema compatibility を保証しない分岐テストに限定し、fixture と PR に provenance・保証縮退を記録する例外を現在形で追加する。PR description には `video` / `entry` の実 schema compatibility が未検証であることを記載する。

### 6. consumer migration は別作業にする

（ユーザー確認済み）本 change は公開 `Unknown` のobject-to-class ABIを変更するが、PixiView-KMP は同時に変更しない。fankt のPRで breaking impact を明示し、consumer は新しいfankt versionへ更新する際にobject instance参照とconstructor利用を追従する。既存の `is Body.Unknown` 分岐はsource上そのまま成立するため、consumer影響を「必ずcompile errorで検出できる」とは保証しない。

## Risks / Trade-offs

- [synthetic `video` / `entry` fixture は実 payload schema を証明しない] → 分岐とraw保持だけを保証し、fixtureとPRへ provenance と未検証範囲を明記する。
- [`JsonElement.toString()` は元レスポンスの字句表現を保存しない] → OTAやfallbackが必要とするJSON値の保持に保証を限定し、byte-exact raw captureをnon-goalとする。
- [巨大な未知bodyでは `JsonElement` に加えて全量Stringを保持する] → Issueが要求するraw保持を優先し、単一post detailの返却objectに限定する。client側上限は設けず、巨大payloadの追加メモリをresidual riskとしてPRへ明記する。
- [`Body.Unknown` のobject-to-class変更でconsumer ABIが壊れる] → breaking changeとしてPRへ明記する。serialized dataはfield aliasとdefault値で旧object decodeを維持し、testで固定する。PixiView-KMPのsource追従は別対応とする。
- [entityでdecodeを遅延すると既知 body のschema error発生箇所がmapperへ移る] → repository境界で `SchemaMismatch` に変換し、失敗production call path testで公開例外契約を固定する。
- [Article blockの欠落参照や複数payloadは既存mapperが黙って1つを選ぶ] → 本changeはtop-level `post.type` のvariant選択だけを変更し、block-level schema toleranceは既存挙動を維持する。specは整合した参照に対する保証へ限定する。

## Migration Plan

1. fanktでentity、mapper、domain model、fixture tests、READMEを同一PRで変更する。
2. breaking change、legacy serialized valueの互換範囲、synthetic fixtureの制約、巨大raw payloadのresidual riskをrelease note相当のPR descriptionへ記録する。
3. fankt release後、PixiView-KMPはdependency bumpと同時に `Body.Unknown` の参照を追従する。

Rollback は本PRをrevertし、payloadを持たない `data object Unknown` と本文フィールド推測へ戻す。内部DB・server deploymentのmigrationはない。新modelで保存した `postType` / `rawBodyJson` 付きUnknownを旧modelへ戻してdecodeするforward compatibilityは保証しない。

## Open Questions

なし。
