## ADDED Requirements

### Requirement: 通知一覧の取得は未読状態を変更しない

`Fanbox` の通知一覧取得は、呼び出し側が明示的に既読化を要求しない限り、未読通知を既読へ変換してはならない（MUST NOT）。既読化を要求できる引数を公開しなければならない（MUST）。

Trace: Issue #41「`getBells` の `skipConvertUnreadNotification = 0` 固定を見直す」。

#### Scenario: 既定の呼び出しで通知一覧を取得する

- **WHEN** 呼び出し側が既読化を指定せずに通知一覧を取得する
- **THEN** FANBOX への request は未読通知の変換を抑止する値を送り、取得対象の未読状態は保たれる

#### Scenario: 既読化を明示して通知一覧を取得する

- **WHEN** 呼び出し側が既読化を要求して通知一覧を取得する
- **THEN** FANBOX への request は未読通知の変換を許可する値を送る

#### Scenario: 想定外の要素を含む通知一覧を取得する

- **WHEN** 既読化の指定と併せて要素 schema 不一致の通知が報告される
- **THEN** 既読化の指定は要素単位の tolerant decode の報告経路と独立に適用される

### Requirement: 既読化の制御は既存の呼び出し形式を壊さない

既読化を制御する引数は、既存の呼び出し形式をコンパイル不能にしてはならない（MUST NOT）。特に callback を trailing lambda として渡す形式は維持されなければならない（MUST）。

Trace: PR #83 レビューで検出した、既定引数による trailing lambda 解決の破壊。

#### Scenario: callback を trailing lambda で渡す

- **WHEN** 呼び出し側が通知一覧の取得に callback を trailing lambda として渡す
- **THEN** 呼び出しは既読化を要求しない overload へ一意に解決され、未読状態は保たれる

#### Scenario: 既存バイナリを再コンパイルせずに利用する

- **WHEN** 既読化の制御が追加される前の版に対してコンパイルされたバイナリが実行される
- **THEN** 既存の公開 signature は維持されており、再コンパイルなしで解決できる

### Requirement: 対応範囲の記述は既知の未対応点を示す

pixivFANBOX の対応状況を示す利用者向け記述は、対応済みと未対応を区別できる形でなければならず（MUST）、未対応点を伏せて全機能が動作すると表現してはならない（MUST NOT）。

Trace: Issue #41「`pixivFANBOX: All features are fully functional` を実態に合わせる」。

#### Scenario: 対応状況を確認する

- **WHEN** 利用者が README で pixivFANBOX の対応状況を確認する
- **THEN** 対応済みの投稿形式、未知 type を保持する挙動、および Cloudflare challenge を検知・分類しないことが記載されている
