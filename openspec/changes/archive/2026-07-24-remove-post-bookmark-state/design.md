## Context

`FanboxPostDetail` は FANBOX API の投稿詳細を表す `@Serializable` model だが、`isBookmarked` だけは API 応答に存在せず、PixiView の Bookmark 状態を保持するために追加されている。mapper は常に `false` を設定し、消費側が後から変更するため、このプロパティによって取得結果が可変かつアプリ依存になる。

ライブラリ内に Bookmark の永続化や同期責務はない。アプリ固有状態は PixiView 側で API モデルと合成する方が、状態の所有者と更新経路を明確にできる。

## Goals / Non-Goals

**Goals:**

- `FanboxPostDetail` を FANBOX API 由来の不変な値だけで構成する。
- ライブラリの mapping と serialization fixture から Bookmark の既定値を除去する。
- PixiView が Bookmark 状態を UI model として所有する移行境界を明示する。

**Non-Goals:**

- fankt に Bookmark repository や UI model を追加しない。
- PixiView の BookmarkDataStore の保存形式や同期方式を規定しない。
- 他の ID 型や `uniqueValue` はこの変更では扱わない。
- バイナリ互換のために deprecated な `isBookmarked` を残さない。

## Decisions

### 1. `isBookmarked` を互換 shim なしで削除する（ユーザー確認済み）

プロパティを `val` に変えたり常に `false` を返す deprecated getter として残す方法では、API に存在しない状態がドメインモデルの契約として残り続ける。v0.1.0 の破壊的変更として削除し、所有者を消費側へ移す。

### 2. mapper は API 応答だけから `FanboxPostDetail` を構築する（agent 仮決め）

`FanboxPostMapper` の `isBookmarked = false` を削除し、同じ API 応答からは等価な不変モデルが得られる状態にする。新たな state provider や callback は注入しない。

### 3. PixiView は API model と Bookmark state を合成する（agent 仮決め）

PixiView では `FanboxPostDetail` 自体を書き換えず、画面に必要な場合だけ BookmarkDataStore の値と組み合わせた UI model を使う。具体的な UI model 名や配置は PixiView の設計に従う。

### 4. fankt と PixiView の変更をリリース境界で連携する（agent 仮決め）

fankt の公開 API 削除後は旧 PixiView がコンパイルできないため、PixiView の連動 PR を準備してから新バージョンへ更新する。fankt 内に一時的な二重表現は作らない。

## Risks / Trade-offs

- [PixiView の参照漏れでビルドまたは Bookmark 表示が壊れる] → PixiView 全体で `isBookmarked` の参照を検索し、UI model への合成と更新経路を連動 PR で検証する。
- [Navigation serialization の schema が変わる] → プロパティ削除を破壊的 schema 変更として扱い、保存状態からの復元テストまたは状態破棄方針を PixiView 側で確認する。
- [API dump の更新漏れ] → Android/KLIB の dump を再生成し、ABI 検証を必須にする。
- [変更を分けたことで連動リリースが必要になる] → fankt と PixiView の PR を相互参照し、消費側が準備できるまで fankt のリリースを行わない。

## Migration Plan

1. PixiView で `isBookmarked` の全参照と保存・更新経路を特定する。
2. PixiView に API model と Bookmark state を合成する UI model を用意し、旧プロパティへの依存を除去する。
3. fankt で model、mapper、fixture / serialization test から `isBookmarked` を削除する。
4. API dump を更新し、fankt の unit test と ABI 検証を実行する。
5. PixiView を新しい fankt version へ更新し、Bookmark 表示・追加・解除を実機確認する。
6. ロールバック時は fankt version と PixiView の連動変更を同時に戻す。

## Open Questions

なし。
