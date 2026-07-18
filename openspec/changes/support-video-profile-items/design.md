## Context

`FanboxCreatorDetailEntity.Body.ProfileItem` は `id` / `imageUrl` / `thumbnailUrl` / `type` だけを decode し、public `FanboxCreatorDetail.ProfileItem` も同じ flat data class である。2026-07-18 に保存した authenticated `creator.get` response では、同じ `profileItems` に image 2件と video 1件が共存し、video は string の `id` / `type` / `serviceProvider` / `videoId` を持つ一方、`imageUrl` / `thumbnailUrl` を持たなかった。既存 PixiView 0.0.20 consumer は全 item の `thumbnailUrl` を画像 pager に渡すため、現行 public model では video へ到達できない。

この変更は public serializable model の breaking migration、transport decode、mapper、fixture、production call path にまたがる。raw authenticated response は repository 外に mode 600 で保存し、設計・実装・reviewer input には field presence/type の安全な summary だけを使う。

## Goals / Non-Goals

**Goals:**

- image / video / unknown profile item を public sealed variant として順序どおり返す。
- video の provider/id と nullable thumbnail を保持し、YouTube / Vimeo URL helper を提供する。
- unknown type と incomplete video を creator response 全体の失敗にせず raw JSON 付き Unknown として保持する。
- actual-derived mixed fixture を `Fanbox.getCreatorDetail` の production path で検証する。
- breaking migration、raw/URL trust boundary、実測済み範囲を文書化する。

**Non-Goals:**

- PixiView の pager、thumbnail、video player/navigation、dependency version を変更すること。
- Vimeo profile item が production に存在することを実測済みと主張すること。
- video thumbnail を外部 provider API から合成・取得すること。
- 未知 provider の URL、未知 item の known subtype field を推測すること。

## Decisions

1. **（ユーザー確認済み）public ProfileItem を sealed interface に置き換える。** `Image` は既存 `id` / nullable `imageUrl` / nullable `thumbnailUrl` を保持し、`Video` は `id` / `serviceProvider` / `videoId` / nullable `thumbnailUrl` を持つ。`Unknown` は original `type` と `rawJson` を持つ。flat data class への field 追加だけでは downstream が type 分岐を強制されず、空画像ページ問題を再発させるため採用しない。

2. **（agent 仮決め）transport Body は profile item を `JsonObject` のまま保持し、mapper が同じ formatter で nullable/defaulted ProfileItem entity へ decode する。** これにより entity に `serviceProvider` / `videoId` を追加しつつ、unknown type の追加 field を `rawJson` に欠落なく残せる。custom serializer は同じ情報を得るためのコード量が大きいため採用しない。object でない item、required `id` / `type` の型不一致は従来と同じ schema mismatch とし、隠さない。

3. **（agent 仮決め）classification は受信 `type` を正本とする。** `image` は Image、`video` かつ non-null `serviceProvider` / `videoId` は Video、それ以外は Unknown とする。未知 provider を持つ完全な `video` は Video のまま保持し、URL helper だけ null にする。incomplete video を捨てたり creator 全体を失敗させる案は forward compatibility と受信データ保持を損なうため採用しない。

4. **（ユーザー確認済み）Video URL helper は既存 post Video/Embed と同じ contract に揃える。** exact provider `youtube` は `https://www.youtube.com/watch?v=<videoId>`、`vimeo` は `https://vimeo.com/<videoId>`、それ以外は null を返す。helper は navigation の許可を意味せず、KDoc で caller が provider、ID、生成 URL を検証する責務を示す。

5. **（agent 仮決め）sealed serialization は subtype discriminator と original type を分離する。** subtype に stable `@SerialName` を付け、Unknown の original type は serialized key `itemType` とする。default class discriminator `type` との衝突を避け、全 variant の round-trip test で固定する。旧 flat ProfileItem serialized value の decode compatibility は提供しない。Issue #31 が breaking release coordination を明示しており、互換 shim は sealed exhaustiveness と二重 surface を増やすため採用しない。

6. **（ユーザー確認済み）fixture は新しい actual response の mixed `profileItems` fragment を whole-value placeholder に置換し、既存匿名化 creator envelope に合成する。** image 2件 + thumbnailなし YouTube video 1件という field presence/type/order だけを保持する。complete original response 互換性、Vimeo production shape、未知 type は実測済みと主張せず、unknown/incomplete/Vimeo helper は明示的 synthetic test に分離する。

7. **（ユーザー確認済み）downstream migration は別 repository で行う。** 現在の PixiView checkout は fankt 0.0.20 を固定参照しており、この PR は dependency bump を行わないため既存 build を変更しない。次の fankt breaking releaseへ bumpするPRでは `when (item)` による Image/Video/Unknown UI 分岐が必要である。

## Risks / Trade-offs

- **[Public source/ABI break]** flat constructorと共通 property accessが消える → PR/README/KDocで breakingを明示し、downstream bump前にsealed branch対応を要求する。旧版とのbinary共存は保証しない。
- **[Raw JSON is untrusted]** Unknownが将来fieldやURLを含みうる → `rawJson` は表示・実行用でなくdiagnostic/forward handling用とKDocに記載し、consumerにparse/render/navigation前のvalidationを要求する。
- **[Vimeo is unobserved]** 今回の実測はYouTubeのみ → Vimeoは既存post helper contractとsynthetic testに限定し、actual-derived fixtureとは明確に分離する。
- **[Incomplete video policy]** 欠落fieldをUnknownへ縮退するとvideo UIには出ない → rawを保持してcreator全体を成功させ、schemaが確定するまで値を捏造しない。
- **[List endpoint mapping]** following/recommended/search由来のBodyも同じmapperを通る → direct creator.getとlist mapperの両方をtargeted testで固定し、inner decode failureの既存tolerant boundaryを維持する。

## Migration Plan

1. fanktでsealed ProfileItemとtestsをリリースする。rollbackはこのPRのrevertで旧flat modelへ戻せるが、新variantでserializeした値との互換は保証しない。
2. PixiView側は新fankt versionへbumpする同一PRでImage/Video/Unknownを分岐し、video navigationを実装する。fankt bump前の0.0.20利用中は変更不要。
3. OpenSpec deltaはmerge後にmain specへsync/archiveする。このautopilot run内ではarchiveしない。

## Open Questions

なし。Vimeo実測responseの欠如は保証範囲をsynthetic helper testに限定することで閉じる。
