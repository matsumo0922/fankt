## Context

現在の article block transport は `type` と plain `text` だけを decode し、mapper は `p` と `header` を同じ `Article.Block.Text(text)` に変換する。さらに空文字を `takeIf(String::isNotEmpty)` で除外するため、見出し、style / inline-link span、空段落の順序が公開 model から失われる。

保存済み private capture の集計では header 68件、空文字 p 353件、styles を持つ block 47件（48 span）、links を持つ block 6件（8 span）を観測した。style object の key は `type` / `offset` / `length`、link object は `offset` / `length` / `url` である。raw response、identity、本文、実 URL は repository、OpenSpec artifact、reviewer input に含めない。

## Goals / Non-Goals

**Goals:**

- p / header の text と block 順序を空文字を含めて保持する。
- style type、inline-link URL、offset、length を変更せず公開値に保持する。
- header semantics を downstream が判定できるようにする。
- unknown style type と将来の span 組み合わせを forward-compatible に保持する。
- 旧 Text constructor と serialized value の decode 互換を維持する。
- actual-response-derived な匿名化 fixture と公開 production call path で配線を証明する。

**Non-Goals:**

- AnnotatedString、heading typography、リンク click 処理を実装すること。
- span range の補正、切り詰め、結合、重複解消を行うこと。
- URL scheme の許可、リンク先 fetch、redirect 解決を行うこと。
- style type を閉じた enum として拒否すること。

## Decisions

### 1. 既存 Text を末尾 default field で拡張する

（agent 仮決め）公開 `Text` は既存の `text: String` を先頭に保ち、`styles: List<StyleSpan> = emptyList()`、`links: List<LinkSpan> = emptyList()`、`isHeader: Boolean = false` を末尾へ追加する。既存の positional / named source constructor と旧 serialized Text の decode を維持でき、通常 paragraph の call site は変更不要になる。

`Heading` sealed subtype を追加する案は型で意味を表せる一方、downstream の exhaustive `when` と serialized discriminator に新 variant を追加し、Issue が必要とする以上の source break を生むため採用しない。JVM の既コンパイル済み1引数 constructor ABI は field 追加で変わり得るため、binary compatibility は保証せず downstream の再compileを要求する。

### 2. span は Text に名前空間を閉じた serializable value とする

（agent 仮決め）`Text.StyleSpan(type, offset, length)` と `Text.LinkSpan(offset, length, url)` を公開 serializable data class とする。style type は文字列のまま保持し、`bold` 以外の未知値も拒否しない。offset / length は FANBOX が返した UTF-16 code-unit coordinate を加工せず保持する。

span を `IntRange` へ変換する案は inclusive end と API の offset + length 表現がずれ、空 span と overflow の扱いも暗黙化するため採用しない。style を enum にする案は未知 type の decode を壊すため採用しない。

### 3. transport は nullable list、public model は empty list に正規化する

（agent 仮決め、独立反証 F1 により修正）transport `Block` に `styles: List<Style>? = null` と `links: List<Link>? = null` を追加し、欠落/null を public empty list へ正規化する。transport span の inner field も nullable/default null とし、全 required value が揃う span だけを公開 value へ変換する。

span list / inner field の型が不一致で full block decode に失敗した場合は、同じ raw block から `styles` / `links` だけを除いて再decodeする。再decode が成功すれば text、header semantics、block 順序を保持し span list だけ empty に縮退する。span 以外の field も不正なら再decodeも失敗し、既存の known-block `SchemaMismatch` 契約を維持する。これにより decorative metadata の drift だけで投稿全体を開けなくなる退行を避ける。

### 4. p と header は text が存在すれば空文字でも Text を返す

（agent 仮決め）mapper は `block.text?.let` を使用し、`p` は `isHeader = false`、`header` は `isHeader = true` とする。空文字は p / header のどちらでも Text として元の位置に残す。text が null の known p/header は現行どおり値を生成しないため、この change では新しい failure policy を導入しない。公開 KDoc は consumer が `isHeader` を分岐に使用することを明記する。

### 5. span metadata は未信頼入力として保持し、適用は caller が検証する

（agent 仮決め、独立反証 F4 により補足）library は負値、範囲外、overlap、URL scheme を検査・補正せず受信値を保持する。KDoc は offset / length が FANBOX payload の UTF-16 code-unit coordinate であることと、caller が text boundary と URL policy を検証してから描画・click 処理へ使う責務を記載する。これにより transport fidelity と UI security policy を分離する。

### 6. actual response 由来 fragment を匿名化して既存 envelope に配置する

（ユーザー確認済み）保存済み response の header / bold / link / empty-p field presence と scalar representation を基に、text、URL、identity を whole-value placeholder へ置換した最小 fragment を既存の匿名化済み article envelope に配置する。fixture は complete original response ではなく、actual-derived fragment shape の production decode 保証に限定する。

private-value fixed-string scan と raw を見ない独立 staged-diff privacy review を commit 前 gate にする。

## Risks / Trade-offs

- [公開 data class field 追加で binary consumer が再compileを要する] → PR に breaking impact を明記し、既存 source / serialized decode は trailing default で維持する。
- [不正 span を caller がそのまま適用すると range error や危険な URL を扱う] → KDoc で text boundary と URL policy の事前検証を要求し、library 内で描画・clickしない。
- [span schema drift が decorative metadata だけで投稿全体を失敗させる] → incomplete span を除外し、span-only decode failure は span field を除いた同一 block の再decodeで本文へ縮退する。
- [空段落保持で blocks 数が増え downstream の spacing が変わる] → Issue #30 の意図した構造復元として明記し、既存 actual-derived fixtures の順序期待を更新する。
- [header flag は将来の heading level を表せない] → FANBOX の現行 `header` は level を持たないため boolean に限定し、未知 field を捏造しない。
- [actual fragment を別 envelope へ配置する] → provenance を明記し、完全 response compatibility の証明とは扱わない。

## Migration Plan

1. entity、public model、mapper、KDoc、fixture tests を同一 PR に含める。
2. downstream の再compile、span / URL validation、PixiView UI non-goal を PR に記載する。
3. Issue #30 はユーザー指定により Opus APPROVE の時点で停止し、merge / sync / archive は行わない。

Rollback は未merge branch / PR を閉じるか commit を revert する。DB、network endpoint、persistent migration はない。

## Open Questions

なし。model shape は独立反証を通してから実装する。
