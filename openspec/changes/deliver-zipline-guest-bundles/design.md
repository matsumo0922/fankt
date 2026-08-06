## Context

#90 が残したのは「bundle を受け取って使う側」である。`ZiplineGuestLoader` が `ManifestVerifier` を組み、`loadOnce` で manifest を取得し、失敗すれば同梱 bundle、それも失敗すれば直接経路へ落ちる。この経路は完成している。

存在しないのは「bundle を署名して世に出す側」である。#90 の署名はテストコードの中だけで、`FanboxSignedBundleTest` が `Random` で毎回作り捨ての鍵ペアを生成している。

本 change の設計は、この非対称を埋めることに尽きる。

## D1: 鍵生成と署名を自前実装しない

Zipline plugin のソースを読み、以下を確認した。

| 機能 | 提供元 |
|---|---|
| Ed25519 鍵ペアの生成 | `ZiplinePlugin.createGenerateKeyPairTasks` が `generateZiplineManifestKeyPairEd25519` task を登録し、公開鍵と秘密鍵を hex でログ出力する |
| manifest への署名 | `ZiplineExtension.signingKeys`（`NamedDomainObjectContainer<SigningKey>`）を `ZiplineCompileTask` が読み、Zipline CLI の `--sign ALGORITHM:name:privateKeyHex` へ渡す |

したがって本 change に鍵生成コードも署名コードも要らない。必要なのは秘密鍵を `signingKeys` へ渡す配線だけである。

#90 のテストが `app.cash.zipline.loader.internal.tink.subtle.newKeyPairFromSeed` を internal パッケージから参照しているのは、multiplatform の test source から呼べる公開 API が無いためである（同ファイル 1 行目の `ponytail:` コメントがこの制約を記録している）。production の鍵生成は Gradle task を使うため、この制約は配信基盤には及ばない。

## D2: 秘密鍵が無いビルドは署名なしで成功させる

`signingKeys` に秘密鍵を無条件で要求すると、ローカル開発と PR CI が鍵なしでは通らなくなる。秘密鍵を Secrets に置く方針と両立しない。

Gradle property が未設定なら `signingKeys` を空にする。`ZiplineCompileTask` は `--sign` を渡さず、署名のない manifest を生成する。

この構成は「署名のない manifest が配信されうる」危険を生む。配信 workflow 側で、配置前に manifest が署名を持つことを確認して防ぐ。ロード側は既に署名を要求するため（`ManifestVerifier` は trusted key が空だと構築時に失敗する）、仮に配信されても consumer は実行しない。二重の防御になる。

## D3: 配信先は gh-pages

fankt には `deploy-documents.yml` が `peaceiris/actions-gh-pages@v4` で gh-pages へ配信する実績がある。同じ仕組みを流用し、新しい外部サービスと認証情報を増やさない。

`FanboxGuestDeliveryConfig` は manifest URL に HTTPS を要求する（loopback のみ HTTP を許可）。GitHub Pages は HTTPS で配信されるため要件を満たす。

### 既存の documents 配信が bundle を消す

`peaceiris/actions-gh-pages` の削除範囲は `destination_dir` で決まる。`git-utils.ts` の `setRepo` が `destDir` を導出し、`directorySetup` がそこへ `chdir` してから `git rm -r --ignore-unmatch *` を実行する。

```ts
const destDir = ((): string => {
    if (inps.DestinationDir === '') {
      return workDir          // ブランチのルート
    } else {
      return path.join(workDir, inps.DestinationDir)
    }
})()
```

既存の `deploy-documents.yml` は `destination_dir` を指定していない。したがって `destDir` はブランチのルートであり、**documents 配信は gh-pages 全体を消してから配置する**。bundle を同じブランチへ置けば、次に release を publish した時点で `zipline/v1/` が消える。

新設する配信 workflow 側で `destination_dir` を `zipline/v1` に限れば、その配信は documents を消さない。しかし逆は防げない。2 つの workflow は独立に発火するため、documents が後に走れば bundle が消える。**両方に対処する**。

- 新設する配信 workflow: `destination_dir` を `zipline/v1` に限定する
- 既存の `deploy-documents.yml`: `destination_dir` を指定するか `keep_files: true` を足す

documents 側は `keep_files: true` を採る。`destination_dir` を与えると配信先が `<pages>/<dir>/` へ移動し、既存の documents URL が変わるためである。documents は README から参照される可能性があり、URL を変えない側を選ぶ（agent 仮決め）。

代償として documents の配信が「毎回作り直す」から「上書きのみ」へ変わる。dokka の出力ファイル名が変わった場合に古いファイルが残留する。documents は index から辿るため実害はないと判断する（agent 仮決め）。残留が問題になれば、その時点で `destination_dir` へ移して URL 変更を受け入れる。

## D4: bridge API バージョンをパスに埋める

host と guest は bridge を越える型の schema で契約している。古い host が新しい bundle を読むと decode に失敗する。

manifest URL を `zipline/v1/manifest.zipline.json` の形にし、bridge API を変える change でのみ `v2` へ上げる。consumer は焼き込んだ URL を参照し続けるため、**バージョン不一致が構造的に起きない**。

host 側にバージョン照合コードを追加する案（manifest の metadata にバージョンを入れて照合する）は採らない。パス分離でバージョン不一致が起きないなら、照合コードは守るものがない。

ただしこれは「同一バージョン内で schema が壊れる」場合を防がない。fixture の網羅漏れや、fixture を更新せずに schema を変える human error がそれに当たる。この経路への防御は D7 で扱う。

現時点で guest 化されているのは `post.info` のみでバージョンは 1 つしかない。複数バージョンを並行運用する際の運用ルール（旧バージョンをいつ止めるか、consumer の分布をどう把握するか）は、operation を増やす #100 で決める。本 change はパス構成と「旧バージョンを消さない」規則までとする。

## D5: 鍵ローテーションに公開 API の変更は不要

`FanboxGuestDeliveryConfig` は trusted key を 1 組しか受け取らない。当初これがローテーションの障害になると見ていたが、`ManifestVerifier` の実装を読んで不要と判断した。

```kotlin
for ((keyName, signature) in manifest.signatures) {
    val verifier = verifiers[keyName] ?: continue
    check(verifier.algorithm.verify(...)) { "..." }
    return keyName // Success!
}
```

`ZiplineManifest.signatures` は鍵名から署名への map であり、`verify` は**認識できた最初の鍵で検証して成功を返す**。認識できない鍵名は `continue` で読み飛ばす。

したがって移行期間中は `signingKeys` に新旧 2 つの鍵を登録し、manifest に両方の署名を載せる。

| consumer | 焼き込んだ鍵 | 検証 |
|---|---|---|
| 旧アプリ | 旧鍵のみ | manifest の旧署名で通る |
| 新アプリ | 新鍵のみ | manifest の新署名で通る |

公開 API を複数鍵へ広げる必要が生じるのは、consumer が 2 つの鍵を同時に焼き込む必要がある場合に限られる。上記の方式ではその必要がない。

旧鍵を廃止できるのは、旧鍵を焼き込んだアプリの利用が十分に減った後である。この判断は consumer の分布に依存するため、手順を文書化して運用時に判断する。

## D6: wire schema は golden fixture で固定する

`ziplineApiCheck` が `api/zipline-api.toml` で固定するのは `FanboxGuestService` の関数シグネチャ 3 件だけである。

```toml
[me.matsumo.fankt.fanbox.guest.FanboxGuestService]
functions = [
  # fun buildPostDetailRequest(kotlin.String): me.matsumo.fankt.fanbox.endpoint.RequestDescriptor
  "iFo+/phf",
  ...
]
```

ハッシュの対象は関数の名前と型の完全修飾名であり、`RequestDescriptor` の中身は含まない。field 名を変えても、sealed の discriminator を変えても、このハッシュは変わらない。

対象の 3 型を代表値で serialize し、その JSON を fixture として固定する。`kotlinx.serialization` の `SerialDescriptor` を辿って構造を記録する案も考えたが、serialize 結果の文字列比較で同じ変更を検出できるため採らない。

## D7: decode 失敗は host 側で捕捉する

当初この設計は「schema 不一致は `GuestFailure` として扱われ sticky に既存経路へ落ちるため、CI の fixture で足りる」と書いていた。これは誤りだった。2 つの異なる故障を混同していた。

| 故障 | 経路 |
|---|---|
| response body の内容が期待と違う | guest が `GuestParseResult.SchemaMismatch` を**値として**返す。host の `when` 分岐が扱う |
| bridge の wire schema 自体が合わず decode できない | 値が返る前に例外。**どの分岐にも到達しない** |

後者を追う。`FanboxGuestService` の 2 関数はいずれも非 suspend であるため `OutboundCallHandler.callInternal` を通り、`CallCodec.decodeResult` が `endpoint.json.decodeFromStringFast(serializer, ...)` を呼ぶ。この decode に try/catch はない。したがって `SerializationException` が素で throw される。

`FanboxGuestHost.getPostDetail` の catch は `CancellationException` と `ZiplineException` の 2 つだけである。`SerializationException` はどちらでもない。`ZiplineException` は guest の関数**内部**で投げられた例外を host 側でラップする型であり、host 側の decode 失敗はその経路を通らない。

この非対称が起こりうる理由は、encode と decode を別の主体が行うことにある。`InboundService.call` は guest 側で `runCatching { function.call(...) }` してから**guest 自身の serializer で** encode する。だから guest 側は成功する。失敗するのは host 側が**自分の古い serializer で** decode するときである。

結果として、fixture の網羅に漏れがあると `Fanbox.getPostDetail()` の外へ `SerializationException` が漏れる。`Fanbox` の公開契約は `FanboxException` しか宣言しておらず、consumer はこれを捕捉しない。fallback の安全機構が破れる。

**したがって CI の fixture だけでは閉じない。** host 側で捕捉して `disableAndFallback` へ流す。fixture は「意図しない schema 変更を配信前に止める」ため、host 側の catch は「それでも漏れた場合にクラッシュさせない」ためであり、役割が違う。

### 捕捉は型の列挙ではなく broad catch にする

当初は `SerializationException` を catch 節に足す形を考えたが、型を列挙する方式は採らない。

同じ穴を持つ型が他にもある。`OutboundCallHandler` は `callInternal` / `callSuspendingInternal` の末尾で `callResult.result.withApiMismatchMessage(function).getOrThrow()` を呼び、bridge API の関数レベルの不整合では `ZiplineApiMismatchException` が throw される。この型は `Exception` を直接継承しており、`RuntimeException` を継承する `ZiplineException` のサブタイプではない。したがって既存の catch にも掛からない。

型を並べても追いかけっこになる。`ZiplineException` / `SerializationException` / `ZiplineApiMismatchException` の 3 つを書いても、Zipline の次のバージョンで 4 つ目が増えれば同じ穴が開く。**D7 が decode 失敗について認めた「CI 検証は網羅を保証しない」という理由は、catch する型の列挙にも同じく当てはまる。**

したがって `guest.service.*` の呼び出しは `CancellationException` を再 throw したうえで、残る `Exception` を退避として扱う。guest は信頼できない入力を解釈する境界であり、そこから来る故障の型を事前に列挙できるという前提を置かない。

`Throwable` ではなく `Exception` にする。対象の 3 型はいずれも `Exception` の子孫であり（`SerializationException` は `IllegalArgumentException` 経由、`ZiplineApiMismatchException` は直接、`ZiplineException` は `RuntimeException` 経由）、型を列挙しないという目的は保たれる。一方 `Throwable` は `OutOfMemoryError` や `LinkageError` まで拾う。これらは JVM が資源枯渇または破損した状態にあるシグナルであり、握って継続すると別の場所で未定義動作を招く。退避して直接経路を試みても同じ理由で失敗するため、拾う利点もない。

catch の範囲は `guest.service.*` の呼び出しに限る。`requestExecutor.execute` は guest の外であり、既存の例外契約を変えない。`CancellationException` の catch は既存コードと同じく最初の catch 節に置き、cancel 経路を素通しにする。

broad catch は本来なら避ける形だが、ここでは「guest 由来の故障はすべて既存経路へ退避する」という意図と一致する。退避は必ず診断経路へ報告するため、無言で握り潰す形にはならない。

### これは新しい方式ではなく既存パターンとの不整合の解消である

同じ形が既に `FanboxGuestHost` にある。初期化経路の `guestOrNull()` はこう書かれている。

```kotlin
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Throwable) {
    diagnosticSink.report("Zipline guest initialization failed; using direct path (...)")
    null
}
```

`CancellationException` を再 throw し、残る `Throwable` を退避として扱う。本 change が呼び出し経路へ適用するのはこれと同一のパターンである。

つまり #90 は**初期化の失敗には broad catch を、呼び出しの失敗には型の列挙を**適用していた。同じ「guest が信頼できない」という前提に対して扱いが揺れていた。本 change はその不整合を解消する。

既存側は `Throwable` であり、そのままにする。当初は「同じ関心に 2 つの書き方を残さない」ため `guestOrNull()` も `Exception` へ揃える方針だったが、独立レビューの指摘を受けて撤回した。

理由は 2 つある。1 つは、その変更が本 change のどの受け入れ条件にも Requirement にも紐付かないこと。もう 1 つは、それが既存の挙動を可用性の下がる方向へ変えることである。`guestOrNull()` は初期化経路であり、Zipline engine の起動と bundle のロードを含む。ここで `OutOfMemoryError` が起きた場合、`Throwable` なら直接経路へ退避するが、`Exception` にすると consumer へ伝播する。「OTA の導入が既存の可用性を下げない」という前提に反する。

結果として、呼び出し経路は `Exception`（`Error` は捕捉しない）、初期化経路は `Throwable`（`Error` も退避する）で非対称に残る。非対称の理由は経路の違いにある。呼び出し経路は本 change が新設する catch であり、そこで `Error` を握らない判断は新しい選択として取れる。初期化経路は既存の挙動であり、変える理由が本 change にない。

なお `buildPostDetailRequest` の戻り値 `RequestDescriptor` も同じ経路で decode されるため、両方の呼び出しに適用する。

## D8: gh-pages への push が競合しうる

配信 workflow（main への push）と documents 配信（release published）は独立に発火し、どちらも gh-pages へ push する。両者が短時間に重なると `peaceiris/actions-gh-pages` の非 force push が non-fast-forward で失敗しうる。

対処しない。失敗は CI が red になる形で現れ、成果物の欠落や破損にはならない。再実行すれば解消する。頻度は release publish と main への push が重なる場合に限られる。

`workflow_dispatch` を用意するため、手動での再配信も可能である（残存リスクとして記録）。

## D9: 配信の起動条件

`deploy-library.yml` は release published で発火し Maven Central へ publish する。bundle 配信を同じ trigger に載せると、bundle の修正だけを配りたい場合にライブラリのリリースが必要になり、OTA の利点が失われる。

配信 workflow は main への push で発火させる。`workflow_dispatch` も併せて用意し、手動での再配信を可能にする。

これは「main に入った変更が即座に全ユーザーへ届く」ことを意味する。#104 が OTA の用途を「API 変更追従のバグ修正相当」に限定している前提と整合させるため、guest bundle の内容に影響する変更のレビューはこの性質を前提に行う。

## 検証

実測で確認する項目。推測で設計を確定させない。

- V1: `generateZiplineManifestKeyPairEd25519` を実行し、Ed25519 鍵ペアが得られることを確認する
- V2: 秘密鍵を渡したビルドで manifest に署名が載り、渡さないビルドでは署名が無く、いずれもビルドが成功することを確認する
- V3: 配信された manifest を実際の URL から取得し、公開鍵を信頼する host でロードして `post.info` が guest 経路で成功することを確認する
- V4: 新旧 2 鍵で署名した manifest が、いずれか一方の鍵だけを信頼する host で検証を通ることを確認する
- V5: gh-pages への配置が既存の documents を削除しないことを確認する
- V6: host が decode できない値を返す guest、および bridge API が一致しない guest で `post.info` が直接経路で成功し、例外が公開契約の外へ漏れないことを確認する。あわせて cancel で `CancellationException` が伝播することを確認する
- V7: documents 配信が `zipline/v1/` を削除しないことを確認する。既存 workflow は現在ルート全体を削除するため、この方向が本体である
