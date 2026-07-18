# creator-profile-items Specification

## Purpose

FANBOX creator profile item を画像・動画・未知形式へ安全に分類し、公開 model、失敗境界、serialization、URL と raw data の trust boundary を一貫して提供する。

## Requirements

### Requirement: profile item を受信 type で分類する

システム SHALL `creator.get` および creator list response の profile item を受信 `type` に基づいて公開 `Image` / `Video` / `Unknown` variantへ変換し、元の list 順序と受信値を保持しなければならない。Trace: Issue #31 の public ProfileItem sealed化とimage/video混在fixture受け入れ条件。

#### Scenario: image item を分類する

- **WHEN** profile item が `type = image` と string id、nullable imageUrl、nullable thumbnailUrl を持つ
- **THEN** システムは同じ値を持つ `ProfileItem.Image` を返す

#### Scenario: complete video item を分類する

- **WHEN** profile item が `type = video` と string id、serviceProvider、videoId、およびnullable thumbnailUrlを持つ
- **THEN** システムは同じ値を持つ `ProfileItem.Video` を返す

#### Scenario: thumbnail のない video item を分類する

- **WHEN** complete video item に thumbnailUrl field が存在しない
- **THEN** システムは thumbnailUrlがnullのVideoを返し、creator response全体を失敗させない

#### Scenario: unknown type を保持する

- **WHEN** profile item が image/video以外のstring typeとobject JSONを持つ
- **THEN** システムはoriginal typeと追加fieldを含むraw JSONを持つUnknownを同じ位置に返す

#### Scenario: incomplete video を保持する

- **WHEN** `type = video` だがserviceProviderまたはvideoIdが欠落/nullである
- **THEN** システムはcreator response全体を失敗させずoriginal typeとraw JSONを持つUnknownを返す

#### Scenario: non-object または required field の非 primitive/nullを拒否する

- **WHEN** profile itemがJSON objectでないかid/typeがobject、array、nullである
- **THEN** システムは既存のcreator schema mismatch boundaryを維持し、値をknown/unknown variantへ捏造しない

#### Scenario: lenient primitive coercion を維持する

- **WHEN** required idまたはtypeがnumber/boolean JSON primitiveである
- **THEN** システムは既存formatterと同じstring coercionを維持し、profile itemだけにstrict policyを導入しない

### Requirement: profile item decode failure の endpoint boundary を維持する

システム SHALL raw profile itemのinner decodeをmapperへ移した後も、direct creator.get、strict creator.search、tolerant creator listで既存のpublic failure semanticsを維持しなければならない。Trace: profile item raw保持が例外発生位置をHTTP scope外へ移すことに対する非退行invariant。

#### Scenario: direct creator.get の inner decode が失敗する

- **WHEN** creator.getのobject profile itemでrequired id/typeがobject、array、nullである
- **THEN** public APIはstatus 200・endpoint `creator.get`のFanboxException.SchemaMismatchを返し、生のSerializationExceptionを公開しない

#### Scenario: strict creator.search の inner decode が失敗する

- **WHEN** creator.search結果のcreatorにinner decode不能なprofile itemがある
- **THEN** public APIはstatus 200・endpoint `creator.search`のFanboxException.SchemaMismatchを返し、生のSerializationExceptionを公開しない

#### Scenario: tolerant creator list の inner decode が失敗する

- **WHEN** following/Pixiv/recommended listの1 creatorにinner decode不能なprofile itemがある
- **THEN** システムはそのcreatorだけをdropしてindex mismatchを返し、他のcreatorを保持する

### Requirement: video 埋め込み URL を復元する

公開 `ProfileItem.Video` SHALL known service providerに対してvideoIdを保持したURLを返し、unknown providerに対してURLを捏造してはならない。Trace: Issue #31 のyoutube/vimeo埋め込みURL復元helper要件。

#### Scenario: YouTube URL を復元する

- **WHEN** serviceProviderが`youtube`である
- **THEN** helperは`https://www.youtube.com/watch?v=<videoId>`を返す

#### Scenario: Vimeo URL を復元する

- **WHEN** serviceProviderが`vimeo`である
- **THEN** helperは`https://vimeo.com/<videoId>`を返す

#### Scenario: unknown provider の URL を捏造しない

- **WHEN** serviceProviderがyoutube/vimeo以外である
- **THEN** helperはnullを返し、別providerのURLを生成しない

#### Scenario: blank provider または videoId の URL を捏造しない

- **WHEN** typeがvideoだがserviceProviderまたはvideoIdがblankである
- **THEN** システムはUnknownへ分類し、Video URLを生成しない

### Requirement: sealed ProfileItem の serialization contract を固定する

公開 ProfileItem SHALL Image / Video / Unknownをsealed serializerでround-tripでき、Unknownのoriginal typeとclass discriminatorを衝突させてはならない。Trace: Issue #31 が明示するbreaking sealed migrationに対するserializable public model invariant。

#### Scenario: 全 variant を round-trip する

- **WHEN** Image、Video、UnknownをProfileItem serializerでencodeしてdecodeする
- **THEN** subtype、全公開field、Unknown raw JSONを保持した同じ値を返す

#### Scenario: Unknown original type を保持する

- **WHEN** Unknownをsealed ProfileItemとしてserializeする
- **THEN** class discriminatorとoriginal item typeを別keyで保持し、decode後にoriginal typeを返す

#### Scenario: discriminator のない sealed value を拒否する

- **WHEN** class discriminatorを持たないJSONをProfileItem serializerでdecodeする
- **THEN** decodeは失敗し、variantを推測しない

### Requirement: actual-derived mixed fixture を公開経路で検証する

fixture test SHALL 保存済みactual responseからwhole-value anonymizeしたimage 2件とthumbnailなしYouTube video 1件のfield presence/type/orderを保持し、完全な実値やcredentialをrepositoryに含めず、公開`Fanbox.getCreatorDetail`経路を検証しなければならない。Trace: Issue #31 のimage/video混在fixture受け入れ条件。

#### Scenario: mixed profileItems を production path で変換する

- **WHEN** actual-derivedな匿名化mixed fixtureを公開`Fanbox.getCreatorDetail`から取得するtestを実行する
- **THEN** ContentNegotiation、entity decode、mapperを通ってImage 2件とVideo 1件が元の順序と値で返る

#### Scenario: synthetic coverage を実測由来と混同しない

- **WHEN** unknown type、incomplete video、Vimeo helperのprobeをfixture/testへ追加する
- **THEN** sourceはsyntheticと明記され、productionで観測済みとは主張しない

### Requirement: raw と URL の trust boundary を公開する

公開KDoc SHALL Unknown raw JSONを未信頼network dataとして扱い、Video URLをnavigationに使う前にcallerがprovider、ID、URL policyを検証する責務を記載しなければならない。Trace: public raw/URL surface追加に対するsecurity非退行invariant。

#### Scenario: caller が Unknown raw JSON を利用する

- **WHEN** callerがUnknown rawJsonをparse、表示、または処理する
- **THEN** KDocは内容を検証・sanitizeしてから使う責務を示す

#### Scenario: caller が video URL を開く

- **WHEN** callerがVideo helper URLをnavigationに使う
- **THEN** KDocはprovider、videoId、許可URL policyを事前に検証する責務を示す
