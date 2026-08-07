# Design

## Context

#90 で作った 3 段 fallback のうち 2 段目（同梱 bundle）に consumer から到達する経路がない。本 change はその経路を公開 API として開ける。

fankt は Ktor 型を公開境界から完全に排除した実績があり（`ktor-free-public-api`）、`verifyKtorBoundary` が ABI ダンプ・Android の generic signature・Kotlin metadata の type alias を検査している。同じ方針を okio にも適用する。

## 検証済みの事実

設計はここに列挙した実測に依拠する。いずれも Zipline 1.27.0 のソースまたは実際のビルド出力で確認した。

### F1. embedded ディレクトリのレイアウト規約

`FsEmbeddedFetcher` が読むのは 2 種類のファイルだけである。

- manifest: `<applicationName>.manifest.zipline.json`。fankt の `applicationName` は `fanbox-guest` なので `fanbox-guest.manifest.zipline.json` になる
- 各モジュール: `<sha256 の hex>`。拡張子を持たない

根拠は `zipline-loader/src/commonMain/kotlin/app/cash/zipline/loader/internal/fetcher/FsEmbeddedFetcher.kt`（`embeddedDir / sha256.hex()` と `embeddedDir / getApplicationManifestFileName(applicationName)`）および `internal/internalCommon.kt:27`（`"$applicationName.$MANIFEST_FILE_NAME"`）。

### F2. Zipline plugin の出力はこのレイアウトではない

`:fankt:fanbox:compileProductionExecutableKotlinGuestZipline` の出力を実測した。

```text
manifest.zipline.json                    ← applicationName の prefix がない
kotlin-kotlin-stdlib.zipline             ← sha256 hex ではなく可読名
fankt-fankt-fanbox-guest.zipline
（他 7 モジュール）
```

**plugin 出力をそのままコピーしても embedded 経路は動かない。** `FsEmbeddedFetcher.fetchByteString` は該当パスが存在しなければ `null` を返すだけなので、間違ったレイアウトを置いた場合の症状は「例外」ではなく「manifest が見つからず静かに 3 段目へ落ちる」になる。この失敗の静かさが D4 の根拠になる。

### F3. Zipline は変換を公式に提供している

`ZiplineDownloadTask` の KDoc は "Download a Zipline application as part of your build process, such as for embedding into an Android or iOS app to support offline, first-launch, and/or other usage" と述べている。実体の `ZiplineLoader.download` は F1 と同じ 2 つの関数（`getApplicationManifestFileName` と `sha256.hex()`）を使って書き出すため、レイアウトの一致はコード上保証される。

### F4. embedded 経路が使う okio API は 2 つだけ

`FsEmbeddedFetcher` が呼ぶのは `embeddedFileSystem.exists(path)` と `embeddedFileSystem.read(path) { readByteString() }` のみである。`list`、`metadataOrNull`、`sink`、`delete` などは embedded 経路では呼ばれない。

`embeddedFileSystem` は `FsEmbeddedFetcher` にのみ渡り、他の経路へ流れない（`ZiplineLoader.kt:171-174`）。ただし `moduleFetchers` は `listOfNotNull(embeddedFetcher, cachingFetcher ?: httpFetcher)` であり、embedded fetcher が**先頭**に置かれる（同 `:187`）。1 つの loader に `withEmbedded` を適用すると、network から取得した manifest のモジュールも embedded から先に探される。

fankt はこれに依拠しない。`ZiplineGuestLoader.load` は remote 段と embedded 段で別の loader インスタンスを構成し、remote 段は `withEmbedded` を呼ばない（`FanboxGuestHost.kt:253-272`）。本 change はこの分離を維持する。維持しない場合、remote 段が古い同梱モジュールを掴む経路が生まれる。

### F5. local 経路の署名検証失敗は今も例外として素通しする

`ZiplineLoader.loadCachedOrEmbeddedManifest` の末尾で `manifestVerifier.verify` を呼んでおり、これは `loadFromLocal` の `try` の外側にある。#90 の design.md に記録された非対称性は 1.27.0 でも成立する。network 経路は `LoadResult.Failure` を返すが、embedded 経路の検証失敗は `loadOnce` の throw として現れる。

既存の `loadGuestWithFallback` は `Result.failure` と throw の両方を同じ失敗として扱っており、この非対称性は既に吸収されている。本 change はその挙動を変えない。

### F6. Android の asset には okio の公式実装がある

okio は `okio-assetfilesystem` artifact で `AssetManager.asFileSystem(): FileSystem` を提供する。Redwood（Cash App の本番プロダクト）の Android サンプルは `applicationContext.assets.asFileSystem()` を `withEmbedded` に渡している。

つまり案 A（okio 型をそのまま公開）にも実績はある。それを採らない理由は D1 に述べる。

## 設計判断

### D1. 公開 API は読み出し抽象だけにする（ユーザー確認済み）

consumer から受け取るのは次の関数型インターフェースだけとする。

```kotlin
fun interface FanboxEmbeddedGuestBundle {
    suspend fun read(fileName: String): ByteArray?
}
```

`okio.FileSystem` / `okio.Path` を公開しない理由は 2 つある。

1. okio が fankt の公開 ABI と API 依存に現れる。`ktor-free-public-api` で Ktor を排除した方針と衝突する
2. F4 のとおり embedded 経路が使う操作は 2 つしかない。`FileSystem` 全体を公開境界に置くと、実際には呼ばれない操作まで consumer との契約になる

理由 1 について、okio は `zipline-loader` の api 依存である（`zipline-loader-jvm-1.27.0.module` の `jvmApiElements-published` variant に `com.squareup.okio:okio 3.17.0` が入る）。しかし fankt はその `zipline-loader` を `implementation` で宣言しているため（`build.gradle.kts:939`）、okio は現在 consumer のコンパイル classpath に到達しない。案 A を採れば、fankt が okio を初めて公開境界へ持ち出すことになる。

`fileName` は F1 のレイアウトにおけるファイル名（`fanbox-guest.manifest.zipline.json` または sha256 の hex）を受け取る。consumer は自分の格納方式（asset / NSBundle / filesDir）に対応する読み出しを書く。

存在しない場合は `null` を返す契約とする。`FsEmbeddedFetcher.fetchByteString` が `exists` で分岐して `null` を返す形と対応させ、「ファイルがない」を例外にしないため。

### D2. 内部で read-only な FileSystem にアダプトする

`FanboxEmbeddedGuestBundle` を `okio.FileSystem` の実装でラップし、`withEmbedded(fileSystem, directory)` に渡す。`directory` は仮想的な root（consumer からは見えない）とする。

okio 3.17.0 の `FileSystem` は abstract メンバーを 12 個持つ（`canonicalize` / `metadataOrNull` / `list` / `listOrNull` / `openReadOnly` / `openReadWrite` / `source` / `sink` / `createDirectory` / `atomicMove` / `delete` / `createSymlink`）。コンパイル上は全部の override が必要になる。

F4 のとおり embedded 経路が呼ぶのは `exists` と `read` だけで、これらはそれぞれ `metadataOrNull` と `source` を呼ぶ final メソッドである。したがって意味のある実装が必要なのは `metadataOrNull` と `source` の 2 つで、残り 10 個は呼ばれた時点で設計の前提が崩れていることを示すため `error()` で塞ぐ。

`read` は `suspend` だが `okio.FileSystem.source` は同期である。この境界の橋渡しには `runBlocking` を使わない（guest の単一スレッドで実行されるため deadlock の危険がある）。代わりに、consumer から受け取った関数を **loader の呼び出し前に** 使って必要なファイルをすべて読み出し、メモリ上の map として保持する形にする。

読み出すファイルは manifest を先に読めば決まる（`manifest.modules` の各 `sha256`）。つまり次の 2 段になる。

1. `read("fanbox-guest.manifest.zipline.json")` で manifest を読む。`null` なら同梱 bundle なしとして失敗を返す
2. manifest を decode して各モジュールの sha256 を得て、それぞれを `read` で読む

この段階で署名検証は行わない。検証は `ZiplineLoader` の責務であり、host 側で先に検証すると検証ロジックが 2 箇所に分かれる。

読み出した内容は loader が終わるまでメモリ上に保持される。現在の bundle は全モジュール合計で約 800KB（実測: stdlib 213KB、guest 149KB、serialization 130KB、coroutines 118KB、json 99KB、zipline 78KB、他 3 モジュールで 6KB 未満）であり、この規模である限り許容する。guest に大きなロジックを足してこれが桁で増えるなら、`source` を遅延読み出しにする形へ変える。**（agent 仮決め）** 許容上限を数値として強制しない判断。閾値を決めても越えた際の挙動（失敗させるのか退避するのか）に根拠がなく、退化は配信 bundle 側でも同時に起きるため embedded 固有の問題にならない。

**ponytail: manifest を decode するために `ZiplineManifest.decodeJson` を使う。** 独自の JSON 解析は書かない。

### D3. 生成は ZiplineDownloadTask に任せ、fankt は変換コードを持たない（ユーザー確認済み）

F3 のとおり Zipline が公式にこの用途のタスクを提供している。fankt 側に「plugin 出力を embedded レイアウトへ変換する Gradle タスク」を書くことは、既に存在するものの再実装になる（ladder 段 5: 導入済み依存で足りる）。

consumer は自分の build script に次を置く。

```kotlin
val downloadGuestBundle by tasks.creating(ZiplineDownloadTask::class) {
    applicationName = "fanbox-guest"
    manifestUrl = "https://matsumo0922.github.io/fankt/zipline/v1/manifest.zipline.json"
    downloadDir = file("src/androidMain/assets/fanbox-guest")
}
```

`ZiplineGradleDownloader` は `NO_SIGNATURE_CHECKS` で loader を構成する。ダウンロード時点では署名を検証しない。これが許容できる理由は、**ダウンロードした manifest の署名がそのまま保存され、実行時に consumer が焼き込んだ公開鍵で検証される**ためである。改竄された bundle を同梱しても、実行時の検証で弾かれて 3 段目へ落ちる。ビルド時検証がないことは可用性の問題（同梱が無駄になる）であって、コード実行の安全性の問題ではない。

この非対称性は README に明記する。

### D4. レイアウトの誤りが静かに失敗することを診断で可視化する

F2 のとおり、間違ったレイアウトを置いた場合の症状は例外ではなく「静かに 3 段目へ落ちる」である。consumer が同梱したつもりで動いていないことに気づけない。

D2 の段 1 で manifest が `null` だった場合と、段 2 でいずれかのモジュールが `null` だった場合を区別して診断へ報告する。前者は「同梱 bundle が見つからない」、後者は「manifest が参照するモジュールが欠けている」となり、後者はレイアウトの取り違えかコピー漏れを直接示す。

既存の `FanboxDiagnosticSink` を使う。新しい診断経路は作らない。

### D5. 引数は guest コンストラクタの末尾に足す

同梱 bundle は guest 経路が有効なときにしか意味を持たないため、既存の guest 用コンストラクタに既定値付きの引数を 1 つ足す。新しいコンストラクタや builder は追加しない（ladder 段 1: 不要なら書かない）。

位置は**末尾**とする。

```kotlin
constructor(
    guestManifestUrl: String,
    guestTrustedKeyName: String,
    guestTrustedEd25519PublicKey: ByteArray,
    logLevel: FanboxLogLevel = FanboxLogLevel.NONE,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cookieStorage: FanboxCookieStorage = InMemoryFanboxCookieStorage(),
    tokenStore: FanboxTokenStore = InMemoryFanboxTokenStore(),
    embeddedGuestBundle: FanboxEmbeddedGuestBundle? = null,
)
```

guest 設定が `logLevel` 以降の 4 引数に分断されて読みにくくなるが、既存 ABI を保つことを優先する。

この guest コンストラクタは **v0.1.2 として Maven Central に公開済み**である。

- `v0.1.2` タグの `Fanbox.kt:110` に `guestManifestUrl` を受け取るコンストラクタが存在する
- 同タグの `api/android/fanbox.api:2` にその ABI が記録されている
- `https://repo1.maven.org/maven2/me/matsumo/fankt/fanbox/0.1.2/` が実在する（HTTP 200）

したがって既存の引数リストの中間へ挿入すると、位置引数でコンパイルされた既存 consumer のバイナリ互換を破る。README が「opt-in の prototype」と書いていることは、公開済みの ABI を壊してよい根拠にならない。このリポジトリは Kotlin Binary Compatibility Validator（`build.gradle.kts:874` の `abiValidation {}`）で ABI をダンプ管理しており、破壊は検証で顕在化する。

PixiView 自身はこのコンストラクタを使っていないが（現在は `Fanbox(logLevel, ioDispatcher)` のみ）、fankt は Maven Central 上の公開ライブラリであり、他の consumer の不在を証明できない。

### D6. 同梱 bundle の署名検証失敗は既存経路で吸収される

F5 のとおり、embedded 経路の検証失敗は throw として現れ、既存の `loadGuestAttempt` が `Throwable` を捕捉して `null` を返す。本 change で新しい防御は要らない。

ただし #99 の受け入れ条件がこの挙動を要求しているため、**公開 API から構成した場合に** それが成立することをテストで固定する。既存テストは `internal` の `EmbeddedGuestBundle` を直接組み立てており、公開 API 経由の経路を通っていない。

### D7. 同梱 bundle の差し替え運用は文書で定める

同梱 bundle が古いままだと、2 段目に落ちた際に古い挙動で動く。リリースごとに `ZiplineDownloadTask` を実行して差し替える運用を README に書く。

fankt はライブラリであり consumer のリリース手順を強制できない。CI で検証する対象は consumer 側にあるため、fankt 側では文書化に留める。

**（agent 仮決め）** fankt 側に「同梱 bundle の鮮度を実行時に検査して警告する」機構を作らない判断。manifest には `freshAtEpochMs` があるため実装は可能だが、何日で古いと見なすかは consumer のリリース頻度に依存し、fankt が決められない。必要になれば別 change で足す。

## Non-goals

- consumer 側（PixiView-KMP#138）の同梱 bundle 配置と読み出し実装。本 change は fankt 側の公開 API までを対象とする
- 同梱 bundle の鮮度検査（D7）
- 28 operation の guest 化（#100）
- bridge API バージョンが上がった際の同梱 bundle の移行。配信側の versioned path（`zipline/v1`）と同じ規約に従うため、追加の設計を要さない

## Open questions

なし。D1 と D3 はユーザー確認済み、D5 と D7 の仮決めは PR の人間確認事項へ転記する。
