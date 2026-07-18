## Why

`creator.get` の `profileItems` は image と video を返すが、現在の transport/public model は video の `serviceProvider` と `videoId` を捨て、すべてを画像相当として公開する。そのため downstream consumer は video を識別できず、空の画像ページとして扱ってしまう。Trace: Issue #31 の問題記述と受け入れ条件。

## What Changes

- transport profile item に nullable `serviceProvider` / `videoId` を追加し、受信した item type と raw JSON を mapper まで保持する。
- **BREAKING**: 公開 `FanboxCreatorDetail.ProfileItem` を `Image` / `Video` / `Unknown` の sealed model に置き換え、consumer が type-safe に分岐できるようにする。
- `Video` に YouTube / Vimeo の埋め込み先 URL を復元する helper を追加し、未知 provider では URL を捏造しない。
- 保存済み実測 response から whole-value anonymize した image/video 混在 fixture を追加し、公開 `Fanbox.getCreatorDetail` 経路で分類を検証する。
- unknown type、incomplete video、sealed serialization の synthetic regression test を追加する。
- README と公開 KDoc に breaking migration、unknown/raw trust boundary、PixiView 側の別対応を記載する。

## Capabilities

### New Capabilities

- `creator-profile-items`: creator profile の image / video / unknown item 分類、video URL helper、実測由来 fixture と公開経路の保証。

### Modified Capabilities

なし。

## Impact

- 影響コード: `FanboxCreatorDetailEntity`、`FanboxCreatorDetail`、`FanboxCreatorMapper`、creator fixture/tests、README/KDoc。
- **BREAKING**: `ProfileItem` の旧 constructor/property access は利用できなくなるため、downstream binary/source consumer は再コンパイルと sealed branch 対応が必要。
- （ユーザー確認済み）Issue #31 が要求する fankt 側の public model と helper をこの PR に含める。
- （ユーザー確認済み）直前に探索・保存した実測 `creator.get` の field presence/type だけを匿名化 fixture の根拠にし、raw response や identity は repository/Claude input に含めない。
- （agent 仮決め）既存 item ID と nullable image URL semantics は各 known subtype に保持し、未知 item は original type と raw JSON string を保持する。
- PixiView の pager/video UI 変更と dependency bump は別 repository の連動 issue とし、この PR の non-goal とする。
