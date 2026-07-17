## Context

`FanboxPostRepository.getCreatorPosts` は `cursor == null` の初回取得で `post.paginateCreator` を先に呼び、先頭 cursor を `post.listCreator` の境界に使う。現状は pagination が空でも `first()` を呼ぶため、投稿 0 件という正常状態で `NoSuchElementException` が発生する。また list request の `limit` は選択済み cursor ではなく元の nullable 引数から決まり、初回だけ固定値 `20` になる。

公開 `Fanbox.getCreatorPosts` は repository の tolerant result を unwrap する production entry point であり、修正はこの経路から検証する。

## Goals / Non-Goals

**Goals:**

- 投稿 0 件の初回取得を空ページとして返す。
- 初回と継続取得の両方で、実際に使用する cursor の `limit` を request に反映する。
- pagination decode、repository 分岐、公開 API の unwrap を含む production call path を fixture test で固定する。

**Non-Goals:**

- 公開 API signature、`PageCursorInfo`、`FanboxCursor` の型を変更すること。
- pagination が空のとき `post.listCreator` を推測した固定 limit で呼ぶこと。
- （ユーザー確認済み）PixiView 側の `PagingSource` 生成条件を変更し、同アプリの空 cursor error を解消すること。PixiView は `getCreatorPostsPagination()` を先に直接呼ぶため、別 Issue / change で扱う。

## Decisions

### 1. 空 pagination は repository で即座に空の tolerant result へ変換する

（agent 仮決め）`cursor == null` で取得した pagination の先頭が存在しない場合、`PageCursorInfo(contents = emptyList(), cursor = null)` と mismatch のない `FanboxTolerantResult` を返し、`post.listCreator` は呼ばない。pagination は FANBOX が提示する creator 投稿ページの inventory なので、存在しない境界を固定値で合成するより空状態をそのまま公開契約へ写す。

代替の sentinel cursor は存在しない query 境界を作り、外部 API call を追加して空状態の扱いを不安定にするため採用しない。

### 2. request の limit は選択後 cursor から決める

（agent 仮決め）初回は paginate の先頭 cursor、継続時は caller の cursor を共通の `currentCursor` とし、`currentCursor.limit?.toString() ?: LOAD_SIZE` を使う。これにより pagination が指定するページ境界を優先し、limit が欠落する既存 cursor だけは従来の `20` fallback を維持する。

### 3. fixture test は公開 API と MockEngine を通す

（agent 仮決め）空 pagination scenario は既存の `paginateCreatorEmpty` fixture を再利用して `Fanbox.getCreatorPosts(creatorId, null, null)` を呼び、production ContentNegotiation で decode する。返却値が空かつ cursor null であり、`post.listCreator` が呼ばれないことを確認する。別 scenario で paginate cursor の `limit` が実際の `post.listCreator` query に使われることを確認する。

repository の fake 単体テストだけでは公開 API wiring と HTTP query を証明できないため採用しない。

## Risks / Trade-offs

- [FANBOX が空 pagination と投稿存在を一時的に矛盾させる場合、list endpoint を呼ばず空として扱う] → pagination をページ inventory の正本とする既存フローを維持し、推測 request は行わない。
- [cursor の `limit` が null の legacy input] → 既存の `LOAD_SIZE = 20` fallback を維持する。
- [paginate URL の `limit` は範囲制約のない `Int` であり、極端な値を初回 request に伝播し得る] → issue #25 が求める FANBOX cursor 境界との一致を優先する。library が上流 cursor を信頼する既存方針は維持し、範囲 validation は本 change の保証に含めない。
- [公開 API を使う test fixture の構築量が repository unit test より増える] → production call path の回帰検出を優先し、空 fixture と最小投稿 fixture に限定する。

## Migration Plan

公開 signature と保存データは変わらないため migration は不要。rollback は実装 commit を revert し、従来の例外挙動へ戻す。

## Open Questions

なし。PixiView の stage-out はユーザー確認済み。agent 仮決めと cursor limit の residual risk は PR description の「人間に確認してほしいこと」へ転記する。
