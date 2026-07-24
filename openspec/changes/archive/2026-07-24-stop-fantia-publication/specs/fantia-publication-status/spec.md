## ADDED Requirements

### Requirement: 未リリースの Fantia はリリース対象から除外される
Fantia が正式リリースされるまで、release workflow は Fantia artifact を Maven Central へ publish してはならず（MUST NOT）、利用者向け Download 案内は Fantia dependency を利用可能な artifact として提示してはならない（MUST NOT）。

Trace: Issue #40 受け入れ条件「次回リリースで fantia が publish されない」。

#### Scenario: release workflow を実行する
- **WHEN** GitHub release の publish または `workflow_dispatch` により library release workflow が実行される
- **THEN** workflow は `:fankt:fantia:publishAndReleaseToMavenCentral` を実行せず、FANBOX の公開対象 artifact だけを publish する

#### Scenario: Download 手順を確認する
- **WHEN** 利用者が README の Download 節で Maven Central の依存追加方法を確認する
- **THEN** `me.matsumo.fankt:fantia` の dependency example は存在せず、Fantia が未リリースであることが明記されている
