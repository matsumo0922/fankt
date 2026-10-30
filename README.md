# fankt

<img src="https://img.shields.io/maven-central/v/me.matsumo.fankt/fanbox">

An unofficial API wrapper for pixivFANBOX and Fantia.  
Compatible with Kotlin Multiplatform (KMP) and usable on Android and iOS. The `fanbox-core` artifact
also builds for Kotlin/JS, but that target exists to run the request and response logic as a Zipline
guest rather than to offer a JavaScript client; see the platform table below.

## Status

#### :white_check_mark: pixivFANBOX
  - Article, image, file, text, video, and entry posts are mapped to typed models, including embed,
    URL embed, header, and style information.
  - An unrecognized post type or article block is preserved as an `Unknown` variant carrying the raw
    JSON, so a FANBOX schema change drops no data.
  - A list response tolerates a malformed item: the remaining items decode and each skipped item is
    reported to the caller.
  - Cloudflare challenge responses are not detected or classified. They surface as ordinary HTTP
    failures.
#### :construction: Fantia
  - Currently under development.
  - Please wait for the official release.

## Platforms

| Platform        | Support                                  |
|-----------------|------------------------------------------|
| Android         | :white_check_mark: Supported             |
| iOS             | :white_check_mark: Supported             |
| Desktop         | :x: Not Supported                        |
| Web (Kotlin/JS) | :warning: No request or response API[^1] |

[^1]: `fanbox-core` publishes a Kotlin/JS variant, `fanbox-core-js`, because the Zipline guest that
builds requests and parses responses is compiled from it. Its public API covers the domain models,
the identifier and cursor types, and the exception hierarchy, but **no operation for building a
request or parsing a response**: the endpoint builders and response parsers are `internal`. The
`Fanbox` client class and all HTTP execution are Android and iOS only.

### Zipline guest

The `Fanbox` client builds FANBOX requests and interprets their responses in a
[Zipline](https://github.com/cashapp/zipline) guest, a signed JavaScript bundle delivered over HTTPS,
so that a fix for a FANBOX API change reaches users without an application update. The host keeps
cookies, the session and HTTP execution, and validates every request the guest builds. The guest
sees the CSRF token in the homepage metadata it parses, but never a cookie.

Every `Fanbox` takes the delivery URL, the trusted Ed25519 public keys and a cache. The library embeds
neither a delivery URL nor a trusted key.

```kotlin
Fanbox(
    guestManifestUrl = "https://matsumo0922.github.io/fankt/zipline/v1/manifest.zipline.json",
    guestTrustedKeys = mapOf(
        "fanboxGuest" to primaryPublicKey,
        "fanboxGuestRecovery" to recoveryPublicKey,
    ),
    // On iOS: FanboxGuestCache(directoryPath)
    guestCache = FanboxGuestCache(context, File(context.cacheDir, "fanbox-guest")),
)
```

The guest loads in the background as soon as the `Fanbox` is created, and requests wait for it. There
is no built-in fallback:

| What happens | The request fails with |
|---|---|
| The guest did not load | `FanboxException.GuestFailure`, for every request of this `Fanbox` |
| The guest failed to build the request or to interpret the response | `FanboxException.GuestFailure` |
| The guest rejected the response | `FanboxException.SchemaMismatch` |

A `Fanbox` whose guest did not load does not try again; create a new one. A failed request does not
stop the guest for later requests.

#### Loading and caching

Every load fetches the manifest from the delivery URL. Modules whose SHA-256 is already in the cache
are not downloaded again, so a load whose manifest has not changed downloads only the manifest. When
the manifest cannot be fetched or fails verification, the bundle last verified into the cache loads
instead, without downloading anything. Its signature and module hashes are checked again, and a
cached bundle missing a module does not load.

Each HTTP request of a load gives up after 10 seconds. The cache keeps up to 10 MiB in a subdirectory
per set of trusted keys, so a cache written under a previous set never blocks loading. Give each `Fanbox` its
own cache directory.

#### Observing the guest

Pass `guestEventListener` to follow the guest.

| Event | When |
|---|---|
| `Loaded(version)` | The guest loaded. `version` is the commit the bundle was built from |
| `LoadFailed(errorType)` | The guest could not load; every request of this `Fanbox` fails |
| `SchemaMismatch(endpoint)` | The guest's parser rejected a response |
| `CallFailed(endpoint, operation, errorType)` | A guest call failed |

No event carries a response body or a credential: `errorType` is the name of the exception's type.
The listener runs on a background thread, possibly before the constructor returns and possibly from
several threads at once. Return quickly, and do not close the `Fanbox` from it. An exception it
throws is logged and does not reach the request.

#### Bundle delivery

The bundle is served from GitHub Pages on two channels. `deploy-guest-bundle.yml` builds, signs and
publishes to the dev channel on every push to `main`. `promote-guest-bundle.yml` copies the dev
channel onto the production channel, and runs only when someone starts it from the Actions tab.

| Channel | Manifest URL | Changes when |
|---|---|---|
| Production | `https://matsumo0922.github.io/fankt/zipline/v1/manifest.zipline.json` | a promotion is started |
| Dev | `https://matsumo0922.github.io/fankt/zipline/v1-dev/manifest.zipline.json` | anything lands on `main` |

A consumer embeds the production URL, so merging to `main` does not reach it. Check the change on the
dev channel, then run **Promote Guest Bundle**. Promotion copies the published bytes instead of
building again, so what reaches production is what was checked, and it refuses a manifest that
carries no signature. Modules are named relative to the manifest, so a copy resolves them from
whichever channel it was fetched from.

Not promoting leaves consumers on the last bundle that was promoted.

Both paths name the bridge API version. A consumer keeps reading the version it was built against, so
a bundle built for a newer bridge API never reaches a host that cannot decode it. Raise the version —
and publish under a new path without removing the old one — when the signature of a
`FanboxGuestService` function changes, or when the wire schema of a type passed to or returned from
one changes. Consumers built against the previous version keep reading the previous path until they
are updated.

Builds without the signing key produce an unsigned manifest instead of failing, so that local builds
and pull request CI pass. Neither workflow publishes such a manifest.

#### Signing keys

Two key pairs are in use. `./gradlew :fankt:fanbox-guest:generateZiplineManifestKeyPairEd25519` prints a
new one.

| Key name | Private key kept in | Signs |
|---|---|---|
| `fanboxGuest` | the `ZIPLINE_SIGNING_PRIVATE_KEY_HEX` repository secret, and offline | every delivery |
| `fanboxGuestRecovery` | offline only | deliveries after the primary key is lost |

A consumer passes both public keys in `guestTrustedKeys`, and accepts a manifest signed by either.
Keep the offline copies somewhere other than GitHub, such as a password manager: a repository secret
cannot be read back, and losing both copies of a key means it can never sign again.

Sign with one key at a time. `ManifestVerifier` checks only the first signature whose key name it
recognises, so an invalid signature ahead of a valid one fails the manifest.

**When the primary key is lost**, sign with the recovery key. Set the
`ZIPLINE_SIGNING_PRIVATE_KEY_HEX` secret to the recovery private key and the
`ZIPLINE_SIGNING_KEY_NAME` repository variable to `fanboxGuestRecovery` before the next delivery
runs, then run **Deploy Guest Bundle**, check the dev channel, and promote. Consumers already trust
the recovery key, so delivery continues without an application update. Ship a new primary key under
a new name in the next update of each consumer.

**When a private key leaks**, switching keys does not revoke it: a consumer trusts the leaked key
until an update removes it. A leaked key alone cannot place a bundle at the delivery URL. Ship new
keys in an update.

**To rotate a key on purpose**, ship consumers that trust the new key as well, switch signing to it,
and drop the old key from consumers once the versions that trust only the old key are no longer in
use.

#### Rolling back a delivery

Each channel is a directory on the `gh-pages` branch. A consumer loads the guest when it creates a
`Fanbox`, so a change here reaches it the next time it creates one; a `Fanbox` that has already
loaded keeps running what it loaded.

A delivery cannot be stopped. Deleting a manifest leaves every consumer that has loaded a bundle on
its cached one, and breaks every request of a consumer that has none. Return to an earlier bundle or
deliver a fix instead.

| Goal | Production (`zipline/v1`) | Dev (`zipline/v1-dev`) |
|---|---|---|
| Return to an earlier bundle | Restore the directory from the `gh-pages` history | Revert on `main` |
| Deliver a fix | Merge to `main`, check the dev channel, then promote | Merge to `main` |

To return production to an earlier bundle, start from a worktree of the latest `gh-pages`. Do not run
**Deploy Guest Bundle** or **Promote Guest Bundle** meanwhile. If the push is rejected, another run
moved the branch: remove the worktree and start again from here. Remove it with
`git worktree remove ../fankt-gh-pages` from the source checkout once the push succeeds.

```sh
git fetch origin gh-pages
git worktree add --detach ../fankt-gh-pages origin/gh-pages
cd ../fankt-gh-pages
```

Pick the commit that published the bundle and restore the directory as it was there:

```sh
git log --oneline -- zipline/v1
git rm -r -q zipline/v1
git checkout <commit> -- zipline/v1
git commit -m "Roll back the production guest bundle to <commit>"
git push origin HEAD:gh-pages
```

The restored manifest keeps its original signature, so consumers accept it only if they still trust
the key that signed it. The dev channel still holds the bundle that was rolled back, and the next
promotion would deliver it again: land the fix on `main` before promoting.

## Usage

### Download

Released Fankt libraries are available on Maven Central. Fantia is not released on Maven Central.
Add the released libraries to your project using the following code:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("me.matsumo.fankt:fanbox:$version")
    // Add only when Room-backed FANBOX session persistence is required.
    implementation("me.matsumo.fankt:fanbox-persistence-room:$version")
}
```

`fanbox` depends on `fanbox-core`, which carries the domain models, so it is resolved transitively.

### API Reference
[API Reference 🔎](https://matsumo0922.github.io/fankt/)

### pixivFANBOX

To use the pixivFANBOX API, you need a session ID called `FANBOXSESSID`.  
You can obtain this session ID from the cookies after logging in via a browser.  
Refer to [PixiView-KMP](https://github.com/matsumo0922/PixiView-KMP) for details about this approach.  
Set the session ID using `fanbox.setFanboxSessionId(sessionId: String)` before using the API.

Additionally, you need to obtain a CSRF token (X-CSRF-Token) for operations like POST requests.  
You can acquire this token by calling `fanbox.updateCsrfToken()`.  
Make sure to retrieve the token before using the API. When `updateCsrfToken()` returns, requests
started afterward use the current token without recreating the `Fanbox` instance. The default token
store keeps the token only in memory and belongs to one `Fanbox` instance. It is cleared when that
instance's session is replaced. Refresh it after process startup or a session change and as needed
before later API calls. Do not race a refresh with session or reset-cookie changes.

Every example below passes the guest settings described in [Zipline guest](#zipline-guest).

```kotlin
val fanbox = Fanbox(
    guestManifestUrl = guestManifestUrl,
    guestTrustedKeys = guestTrustedKeys,
    guestCache = guestCache,
)

try {
    // Set the session ID and CSRF token before using the API
    fanbox.setFanboxSessionId("your_session_id")
    fanbox.updateCsrfToken()

    // Example: Retrieve posts from a creator
    fanbox.getCreatorPosts(creatorId = FanboxCreatorId("creator_id"))
} finally {
    fanbox.close()
}
```

#### Authentication storage

A `Fanbox` created without `cookieStorage` and `tokenStore` creates independent
`InMemoryFanboxCookieStorage` and `InMemoryFanboxTokenStore` instances. The default Cookie and CSRF state is isolated from other
`Fanbox` instances and is not restored after process recreation. Applications that require durable
sessions inject a host-owned `FanboxCookieStorage` implementation explicitly:

```kotlin
val cookieStorage: FanboxCookieStorage = applicationCookieStorage
val tokenStore = InMemoryFanboxTokenStore()
val fanbox = Fanbox(
    guestManifestUrl = guestManifestUrl,
    guestTrustedKeys = guestTrustedKeys,
    guestCache = guestCache,
    cookieStorage = cookieStorage,
    tokenStore = tokenStore,
)
```

`FanboxCookieStorage` stores normalized `FanboxCookieRecord` values and implements finite
`snapshot()`, current-value `cookies` observation, atomic `replaceAll()`, and conditional
`deleteExpired()`. fankt applies Cookie domain, host-only, path, secure-transport, and expiry
matching uniformly to every backend. Cookie values and CSRF tokens are credentials; storage
implementations must not log them or include them in telemetry.

`Fanbox.setCookies()` accepts `FanboxCookieRecord` values. Each record's required `domain` and
explicit `hostOnly` fields are the only scope authority: a host-only record for `www.fanbox.cc` is
not sent to `api.fanbox.cc`. Use `setFanboxSessionId()` for `FANBOXSESSID`; it creates the required
domain-scoped session Cookie. An expired additive record deletes the matching identity, while an
atomic reset omits expired records.

The application owns injected stores. `Fanbox.close()` closes only the HTTP clients and does not
close or clear a store. Passing the same Cookie or token store instance to multiple `Fanbox` clients
shares that state deliberately; passing different instances keeps accounts isolated.

Room-backed Cookie persistence is provided by the optional
`me.matsumo.fankt:fanbox-persistence-room` artifact. It uses the existing schema-v3 `fankt.db` in
place, including the existing v1 and v2 migrations. Each factory call owns a new database instance;
there is no AndroidX Startup initializer, global `Context`, singleton database, or database-file
deletion API.

The Android and iOS factories are platform APIs and must be called from the corresponding platform
source set rather than common code.

On Android, pass a `Context` explicitly. The factory uses
`context.applicationContext.getDatabasePath("fankt.db")`:

```kotlin
val storage = createRoomFanboxCookieStorage(applicationContext)
val fanbox = Fanbox(
    guestManifestUrl = guestManifestUrl,
    guestTrustedKeys = guestTrustedKeys,
    guestCache = guestCache,
    cookieStorage = storage,
)

try {
    // A restored FANBOXSESSID is available through this Fanbox instance.
    fanbox.updateCsrfToken()
} finally {
    fanbox.close()
    storage.close()
}
```

On iOS, the factory uses `NSDocumentDirectory/fankt.db`:

```kotlin
val storage = createRoomFanboxCookieStorage()
val fanbox = Fanbox(
    guestManifestUrl = guestManifestUrl,
    guestTrustedKeys = guestTrustedKeys,
    guestCache = guestCache,
    cookieStorage = storage,
)

try {
    // Use Fanbox with the restored persistent Cookie state.
} finally {
    fanbox.close()
    storage.close()
}
```

Create one storage for the host lifecycle that needs persistence. Close every `Fanbox` that uses it,
then close the storage. Do not open multiple storage instances for the same `fankt.db`: their Room
invalidation trackers do not propagate `cookies` Flow updates between instances, and concurrent
writes can fail with `SQLITE_BUSY`. `close()` is idempotent; operations started after close fail with
`IllegalStateException`. A later factory call opens a fresh instance over the same database file and
restores committed Cookie rows. The schema stores legacy records as domain Cookies, so Room-backed
records have `hostOnly = false`.

Closing the storage terminates a collection of its `cookies`, directly or through `Fanbox.cookies`,
with `IllegalStateException`. A Flow obtained before close fails the same way when it is first
collected after close, without reaching the database. That cause applies when close is the first
terminal event to reach the collection: cancelling the collecting coroutine surfaces its
cancellation, an exception thrown by the collector propagates unchanged, and a database failure
delivered while the storage is still open propagates unchanged. `close()` returns without waiting for
a collection to unwind.

Because `close()` neither suspends nor preempts a running collector, a collector that throws — or a
cancellation that arrives — after `close()` returns but before that collection observes the close
still determines the observed cause. Reporting close instead would have to discard your own exception
or swallow a cancellation, so do not depend on the cause in that window.

Creating and closing `Fanbox` instances does not close the injected storage, so one storage serves any
number of client lifecycles and stays usable until the host closes it.

Do not call `close()` from a context that cannot make progress concurrently with the storage's query
dispatcher — for example, a single-parallelism dispatcher passed as `ioDispatcher` and then also used
to run `close()`. Room's close barrier waits for in-flight database work to release, and that work
cannot resume on a dispatcher occupied by the `close()` call itself.

The v0.1.0 API replaces Ktor-facing constructor and operation types. Use
`FanboxLogLevel` instead of Ktor `LogLevel`, `FanboxCookieRecord` instead of Ktor `Cookie`, and the
streaming `download()` callback instead of `HttpStatement`. `getHttpClient()` is removed; create and
own a separate host client for networking outside the FANBOX API operations. When converting a Ktor
Cookie, convert relative `maxAge` to absolute `expiresAtEpochMilliseconds`. Ktor-only fields such as
`httpOnly`, extensions, and encoding are not part of the fankt storage record.

Ktor is an implementation dependency and no fankt public signature requires a Ktor type. Android
publication metadata keeps Ktor out of its compile API. Kotlin/Native metadata still carries the
implementation KLib dependencies required for linking, but consumers can explicitly select a
runtime-compatible Ktor version for their own clients. Arbitrarily incompatible Ktor binaries are
not supported in one runtime graph.

The default authentication durability changes from implicit Room persistence to process-memory
storage, and constructor binary signatures change. Consumers must perform a clean rebuild and
inject persistence before upgrading when restart durability is required.

FANBOX model timestamps use the stable `kotlin.time.Instant` API instead of the transitional
`kotlinx.datetime.Instant` compatibility type. Consumers require Kotlin 2.3.21 or newer and do not
need `ExperimentalTime` opt-ins solely for `Instant` or `Clock`. Remove `toStdlibInstant()` calls and
accept model timestamps directly. PixiView's known migration covers payment grouping, the common
formatting extension, and both relative-time extensions. Consumers that still need calendar or
time-zone APIs should select a normal non-compat `kotlinx-datetime` artifact independently; fankt
does not publish that dependency. fankt remains on Kotlin 2.3.x until Zipline supports Kotlin 2.4.

The FANBOX library consists of three modules. `:fankt:fanbox-core` holds the domain models, request
builders, serializable descriptors, response parsers, cursor/host extraction, failure interpretation,
and the Zipline guest service; it has no Ktor, Room, or Napier dependency. `:fankt:fanbox-guest` binds
that service as the guest's entry point and builds the signed bundle; it is not published.
`:fankt:fanbox` holds the `Fanbox` client, the guest host, and the Ktor transport.
Every non-download operation passes one descriptor through one raw-response Ktor executor and then
through the guest's endpoint-specific parser. No module contains a Ktorfit endpoint or KSP-generated
API, and Ktor remains only inside the internal transport implementation.

The declarations that `:fankt:fanbox` and the guest share, such as the guest service and the request
descriptor, are public in `fanbox-core` but annotated with `@InternalFanboxApi`, an opt-in marker at
error level. They are not part of the public API and carry no compatibility guarantee.

Before reading the injected Cookie storage or request-time CSRF token, the executor resolves the
host-owned endpoint policy and validates the method, relative path, exact HTTPS origin, and redirect
destination. The streaming download client remains separate because it accepts complete allowlisted
media URLs and consumes response bodies incrementally. Close `Fanbox` after all requests and downloads
finish. Calls started after `Fanbox.close()` fail with `IllegalStateException`; the HTTP engine may
finish shutdown asynchronously.

#### Media downloads

Pass the complete media URL returned by FANBOX to `Fanbox.download()` instead of reconstructing a
path or filename extension:

```kotlin
fanbox.download(
    url = image.originalUrl,
    onProgress = { progress -> updateDownloadProgress(progress) },
    onChunk = { bytes -> output.write(bytes) },
)
```

Downloads accept HTTPS URLs on `fanbox.cc` and its subdomains, plus the observed external media
hosts `pixiv.pximg.net` and `fanbox.pixiv.net`. The same allowlist applies to redirects. Invalid
initial URLs and disallowed redirects throw `IllegalArgumentException` before the rejected
destination reaches transport. The complete port, path, and query are preserved. `download()` emits
an initial `0f`, then reads one bounded chunk at a time and waits for `onChunk` before reading the
next. Positive known-length responses report progress after each chunk callback; unknown or zero
length never produces a non-finite value.

Network and HTTP failures use `FanboxException`; callback failures and coroutine cancellation
propagate unchanged. A partial output remains the caller's responsibility, so file consumers should
write to a temporary path and promote it only after `download()` returns successfully. The response
is released on success, failure, cancellation, or owner close. A download accepts a coroutine
context without a `Job`; when a caller `Job` exists, cancellation propagates into the download while
`Fanbox.close()` cancels only the download work and does not cancel the caller's surrounding scope.

#### Error handling

FANBOX request failures use the public `FanboxException` hierarchy. Catch a specific subtype when
the application can recover from it, or catch `FanboxException` for shared reporting:

```kotlin
try {
    fanbox.getPostDetail(FanboxPostId("post_id"))
} catch (error: FanboxException.RateLimited) {
    scheduleRetry(error.retryAfter)
} catch (error: FanboxException.Unauthorized) {
    requestLogin()
} catch (error: FanboxException) {
    report(error.message.orEmpty())
}
```

`statusCode` is `null` when no response was received. For library-owned descriptor routes,
`rawBody` contains a credential-redacted and control-normalized diagnostic fragment of at most 2,048
Kotlin characters. It can still contain FANBOX or user data. Each normal request reports its stable
endpoint descriptor ID. Download failures use endpoint `download` and intentionally set `rawBody` to
`null`.

Log only `FanboxException.message` or an explicitly reviewed `rawBody`. The original `cause` is
preserved for debugging, but its messages are not covered by the bounded or redacted diagnostic
contract and must not be logged automatically.

The `Fanbox` constructor treats `FanboxLogLevel.BODY` as effective `INFO` and
`FanboxLogLevel.ALL` as effective `HEADERS`. The HTTP logger never receives a raw response body.
Library-owned descriptor-route errors use a separate path for a sanitized, control-normalized fragment
bounded to 2,048 Kotlin characters; downloads retain no response fragment.

#### Tolerant list responses

Home, supporting, and creator post lists, comments, bells, followed/recommended creator lists, and
creator/supporting plan lists decode and map each item independently. When one item no longer
matches the FANBOX schema, the library skips that item, preserves the other items and pagination
value, and writes a Napier warning with the endpoint and zero-based `indexPath`. The warning does not
include the item itself.

Callback overloads report every skipped item on the caller's coroutine context before returning the
partial result:

```kotlin
val posts = fanbox.getHomePosts(cursor = null) { mismatch ->
    reportSkippedItem(mismatch.endpoint, mismatch.indexPath)
}
```

The callback is call-local, so concurrent calls do not share events. If the callback throws, its
exception is propagated to the caller. The no-callback `getSupportedPlans()` remains strict because
a missing active support plan must not look like an ordinary partial list. Use its callback overload
to opt into per-item tolerant results explicitly.

#### Creator profile items

`FanboxCreatorDetail.profileItems` is a sealed list. Consumers must branch over
`ProfileItem.Image`, `ProfileItem.Video`, and `ProfileItem.Unknown` instead of reading one flat
profile-item shape:

```kotlin
creator.profileItems.forEach { item ->
    when (item) {
        is FanboxCreatorDetail.ProfileItem.Image -> showImage(item.thumbnailUrl ?: item.imageUrl)
        is FanboxCreatorDetail.ProfileItem.Video -> item.url?.let(::openReviewedUrl)
        is FanboxCreatorDetail.ProfileItem.Unknown -> reportUnknownType(item.type)
    }
}
```

`Video.url` reconstructs a URL for YouTube and Vimeo and returns `null` for other providers.
Provider names, video IDs, reconstructed URLs, and `Unknown.rawJson` remain
untrusted network data, so applications must validate them before navigation, parsing, display, or
logging.

The sealed model is a source and serialization compatibility break for consumers of the flat
`ProfileItem` data class. Recompile consumers and migrate exhaustive branches when updating fankt.
PixiView dependency updates and UI support are handled independently of this library change.

### Fantia

WIP (Work in Progress)

## Samples

A [sample app](./composeApp/) with a Swagger UI-like interface is available.  
You can test API results by inputting the required parameters.

## Continuous integration

Pull requests run Detekt, the bridge API check, and the tests that guard guest delivery in one
Ubuntu job: signature verification, loading from the cache, every operation crossing the bridge in
QuickJS, the host's handling of guest failures, request validation before credentials are attached,
and the Cookie database migration. Response parsing and host behavior are checked by loading the
dev channel bundle in an application rather than by tests in this repository. Documentation-only
changes skip the job. The library release workflow runs on macOS because the Kotlin Multiplatform
publication includes Apple targets.

### 静的解析

`./gradlew detekt` と `./gradlew :detekt` は全モジュールと、ルートのビルド定義・`build-logic` を型情報なしで解析する。
結果は各モジュールの `build/reports/detekt/` に出力し、ルートの通常解析に依存する各モジュールの XML を `build/reports/detekt/merge.xml` に統合する。
モジュールを指定した通常解析は、そのモジュールのレポートだけを更新し、統合処理を起動しない。
統合処理は別の解析タスクやコンパイルを起動しない。
型情報ありの解析には `:composeApp:detektDebug`、KMP の Android target には `:fankt:fanbox:detektDebugAndroid` など、各 target のタスクを使う。
生成コードは解析対象から除外する。
CI では通常解析と各モジュールの Android debug 向け型解析を明示的に実行する。

設定は detekt 2.0.0-alpha.6、ktlint wrapper、Compose rules 0.6.8 を使用する。
共通設定は Agents の `rules/lint/detekt.yml` と一致する。
`FunctionSignature` と `ClassSignature` は引数3つ以上の宣言を改行し、行長による1行化は行わない。
デフォルト引数を含む短い宣言、呼び出し側の名前付き引数、プロパティ参照を数えないメソッドチェーンの改行は `AGENTS.md` に従ってレビューする。
関数長・クラス長の閾値やテスト全体の除外は変更せず、大きな期待値・JSON fixture などを一緒に扱う理由がある場合だけ宣言単位の抑制を使用する。
endpoint の集約と、認証操作を独立に差し替える `FanboxDependencies` にも理由付きの宣言単位の抑制を使用する。
外部 API が要求する override は共通設定で関数数から除外する。
公開 API の例外伝播は `AGENTS.md` のプロジェクト規約に従う。

## License

fankt is licensed under CC BY-NC 4.0. Creative Commons does not recommend its licenses for software,
and this project accepts that trade-off to keep the noncommercial restriction rather than adopting a
software-oriented noncommercial license.

```text
Copyright 2025 daichi-matsumoto

Licensed under the Creative Commons NonCommercial License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://creativecommons.org/licenses/by-nc/4.0/

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
