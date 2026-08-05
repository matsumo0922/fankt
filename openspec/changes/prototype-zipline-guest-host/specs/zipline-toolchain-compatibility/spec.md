## MODIFIED Requirements

### Requirement: Zipline 1.27.0 compiler plugin is compatible
Issue #90 の「`app.cash.zipline` 1.27.0 を production branch へ恒久適用する」に対応する。fankt は `app.cash.zipline` 1.27.0 の Gradle plugin を `:fankt:fanbox` へ恒久的に適用し、guest bridge API の固定を伴う状態で build できなければならない（SHALL）。plugin は適用先 project の全 compilation へ compiler plugin を入れる。これは host 側の `Zipline.take` が compiler plugin による書き換えを前提とするために必要である。

plugin は Kotlin/JS IR target 全体の compiler options を `es2015` と UMD module kind へ強制する。したがって既存の publish 用 Kotlin/JS target の compile 条件が変わる。この変更が publication を壊さないことを確認しなければならない（SHALL）。

#### Scenario: Production build applies the plugin permanently

- **WHEN** production branch で `app.cash.zipline` 1.27.0 を適用した full build を実行する
- **THEN** Gradle plugin と compiler plugin が解決・適用され、既存 compilation と test が失敗せず完了する

#### Scenario: Publication survives the compiler options change

- **WHEN** plugin 適用後に `:fankt:fanbox` の Kotlin/JS publication metadata を生成する
- **THEN** 従来どおり生成され、既存の consumer 向け klib が失われない

#### Scenario: Guest bridge API is fixed and verified

- **WHEN** guest bridge API の検証を実行する
- **THEN** 記録済みの API 定義と照合され、実際に検査対象を持ったうえで差分がなければ成功する
