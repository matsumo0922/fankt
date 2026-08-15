## Context

`deploy-guest-bundle.yml` は `main` への push で guest bundle をビルド・署名し、`peaceiris/actions-gh-pages@v4` で gh-pages の `zipline/v1` へ配置している。consumer（PixiView-KMP）はこのパスの manifest URL を焼き込んでおり、起動ごとに取得する。ローカルキャッシュを持たないため、配置された内容がそのまま次回起動時の実行対象になる。

gh-pages には `deploy-documents.yml` も書き込む。こちらは root へ Dokka の出力を置き、`keep_files: true` で `zipline/` を消さないようにしている。

配信中の manifest は次の構造を持つ。署名は `unsigned.signatures` にあり、`unsigned.baseUrl` は `null`、モジュールは相対 URL で参照される。`unsigned` 配下は署名の計算対象外である。

```text
unsigned: { signatures: { fanboxGuest: ... }, freshAtEpochMs: ..., baseUrl: null }
modules:  { ...: { url: "kotlin-kotlin-stdlib.zipline" } }
```

## Goals / Non-Goals

**Goals:**

- `main` へのマージが本番チャンネルを変更しないこと
- 本番へ配置される成果物が、dev チャンネルで検証したバイト列と同一であること
- 昇格が既存の署名検証を通る manifest だけを本番へ置くこと

**Non-Goals:**

- consumer 側でのチャンネルの出し分け（PixiView-KMP#148）
- 昇格の承認履歴（GitHub Environments）
- 昇格した commit を記録するメタデータの併置
- CI 内での Ed25519 署名の検証（後述の Decision 5）

## Decisions

### D1. workflow を 2 本に分ける（ユーザー確認済み）

1 本にまとめて `github.event_name` で分岐する案もあるが、ビルド・署名する job と、コピーするだけの job は共有するステップを持たない。分けることで Actions の画面に名前つきの実行ボタンが並び、押す対象が判別できる。

### D2. promote は再ビルドしない（ユーザー確認済み）

昇格時に Gradle を回し直すと、dev で検証したものとは別のバイト列が本番へ乗る（依存解決の差、ビルドの非再現性）。gh-pages 上の `zipline/v1-dev` をそのまま `zipline/v1` へ配置する。

コピーで成立する根拠は manifest の構造にある。モジュールは相対 URL で参照され、`baseUrl` は `null` であるため、ディレクトリごと別パスへ置いても manifest の URL を基準に解決できる。バイト列が変わらないため署名も有効なまま維持される。

### D3. promote は gh-pages を checkout して peaceiris へ渡す（agent 仮決め）

`actions/checkout` に `ref: gh-pages` を与えて `zipline/v1-dev` を作業ディレクトリへ取得し、それを `publish_dir` として `destination_dir: zipline/v1` へ配置する。

代替として git を直接操作する案（`rm -rf zipline/v1 && cp -r zipline/v1-dev zipline/v1` して commit・push）があるが、配置の手段を既に repository で使っている action に揃える方が、`keep_files` の既定や commit の作法を再実装せずに済む。

`keep_files` は既定の `false` のままとする。`destination_dir` 配下だけが対象になるため、root の Dokka 出力と `zipline/v1-dev` は影響を受けない。旧モジュールが残らないことが目的である（`true` にすると、モジュール名が変わった際に古い `*.zipline` が本番パスへ残り続ける）。

### D4. promote は配置前に 2 点を検査する（agent 仮決め）

1. `manifest.zipline.json` が存在し、`unsigned.signatures` が空でないこと
2. `unsigned.baseUrl` が `null` であること

1 は `deploy-guest-bundle.yml` が既に持つ防護と同じものである。deploy 側の検査はビルド出力に対して働くが、promote が読むのは gh-pages 上の内容であり、deploy を経ずにそこへ置かれた内容を昇格させる経路が残る。両チャンネルで同じ防護が働くことが受け入れ条件であるため、promote 側にも置く。

2 は本 change が新たに作る危険への防護である。`baseUrl` は署名の計算対象外（`unsigned` 配下）であるため、そこに dev チャンネルの絶対 URL が入った manifest は署名検証を通ったまま本番へ乗り、本番の manifest が dev のモジュールを読ませる。チャンネルを分けたことで初めて成立する失敗であり、コピーが成立する前提（D2）が崩れていないことの検査でもある。

### D5. CI では Ed25519 署名そのものを検証しない（agent 仮決め）

受け入れ条件は「promote 後の manifest が公開済みの公開鍵で署名検証を通る」ことを求めるが、これを CI で検査するには公開鍵を fankt の repository に置く必要がある。

本 change で保証するのはバイト列の同一性であり、署名の有効性は deploy 時点で既に成立している性質が保存されることによる。昇格が署名を壊さないことは D2 の根拠から従う。実行時には consumer が焼き込んだ公開鍵で毎回検証しており、検証に失敗した bundle のコードは実行されない。

したがって CI へ鍵を持ち込まず、条件の成立は昇格後の manifest を公開鍵で検証した観測をもって示す（tasks 3.2）。

### D6. gh-pages へ push する 2 つの workflow を同一 concurrency group に置く（agent 仮決め）

本 change で gh-pages の `zipline/` へ書き込む workflow が 2 本になる。同時に走ると後発の push が non-fast-forward で拒否され、run が失敗する。deploy の `group: ${{ github.workflow }}` を両者共通の literal へ変え、`cancel-in-progress: false` のまま直列化する。

`deploy-documents.yml` は本 change の対象外とする。既に concurrency group を持たず、本 change がその状態を作ったわけではない。

### D7. bridge API バージョンは両 workflow に literal で書く（agent 仮決め）

`workflow_dispatch` の input としてバージョンを受ける案があるが、deploy 側は literal で持っており、片方だけ input にすると 2 つの workflow でバージョンの持ち方が食い違う。バージョンを上げる際は既に workflow の編集が必要であり、編集箇所が 1 から 2 へ増えるだけである。

## Risks / Trade-offs

- **promote を忘れると本番が更新されない** → 止まる先は最後に昇格した bundle であり、意図した挙動である。README に手順を記載する
- **緊急時の手数が増える**（マージ → dev で確認 → promote） → 遅くすること自体が目的であり、それでもストア審査より桁違いに速い
- **どの commit が本番に乗っているか分かりにくい** → gh-pages のコミット履歴を辿ることになる。昇格時に元の commit SHA を記録する案は、必要になってから足す
- **CI が署名の有効性を検査しない**（D5） → 昇格後の観測で示す。実行時には consumer が検証する
- **dev チャンネルは誰でも取得できる** → 未検証のコードが公開の URL に置かれる。ただし署名鍵は同一であり、consumer は焼き込んだ URL しか読まない。dev を読ませるのは consumer 側の別 change（PixiView-KMP#148）の責務である

## Migration Plan

本 change の merge 時点で `zipline/v1` の内容は変化しない。最初の `main` push で `zipline/v1-dev` が新規に作られ、以後 `zipline/v1` は promote を実行するまで現在の内容のまま据え置かれる。consumer の参照先は変わらないため、consumer 側の対応は不要である。

rollback は deploy の `destination_dir` を `zipline/v1` へ戻し、promote workflow を削除すれば足りる。gh-pages 上に残る `zipline/v1-dev/` は誰も読まないディレクトリになる。

## Open Questions

なし。
