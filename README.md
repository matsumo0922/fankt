# fankt

<img src="https://img.shields.io/maven-central/v/me.matsumo.fankt/fanbox">

An unofficial API wrapper for pixivFANBOX and Fantia.  
Compatible with Kotlin Multiplatform (KMP) and usable on Android and iOS. The library also builds
for Kotlin/JS, but that target exists to keep the request and response logic portable rather than to
offer a JavaScript client; see the platform table below.

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

[^1]: The Kotlin/JS target compiles and tests the portable core — endpoint builders, response
parsers, and domain models — and a `fanbox-js` artifact is published. Its public API covers the
domain models, the identifier and cursor types, the exception hierarchy, and the authentication
storage contracts, but **no operation for building a request or parsing a response**: the endpoint
builders and response parsers are `internal`, so a separate Gradle module cannot call them. The
target exists to keep that logic compiling and tested on Kotlin/JS, which is the prerequisite for
running it as a [Zipline](https://github.com/cashapp/zipline) guest inside this library. The
`Fanbox` client class and all HTTP execution remain Android and iOS only.

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

```kotlin
val fanbox = Fanbox()

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

Each `Fanbox()` call creates independent `InMemoryFanboxCookieStorage` and
`InMemoryFanboxTokenStore` instances. The default Cookie and CSRF state is isolated from other
`Fanbox` instances and is not restored after process recreation. Applications that require durable
sessions inject a host-owned `FanboxCookieStorage` implementation explicitly:

```kotlin
val cookieStorage: FanboxCookieStorage = applicationCookieStorage
val tokenStore = InMemoryFanboxTokenStore()
val fanbox = Fanbox(
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
val fanbox = Fanbox(cookieStorage = storage)

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
val fanbox = Fanbox(cookieStorage = storage)

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

The v0.1.0 API keeps `Fanbox()` but replaces Ktor-facing constructor and operation types. Use
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

`Fanbox` keeps request builders, serializable descriptors, response parsers, cursor/host extraction,
and failure interpretation in an internal portable core without Ktor, Room, or Napier imports. Every
non-download operation passes one descriptor through one raw-response Ktor executor and then through
its endpoint-specific parser. The `:fankt:fanbox` module contains no Ktorfit endpoint or KSP-generated
API; Ktor remains only inside the internal transport implementation.

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
value, and writes a Napier warning with the endpoint and zero-based `indexPath`. Raw item fragments
are included only when `Fanbox` logging is enabled; they are structurally credential-redacted and
limited to 2,048 characters.

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

`Video.url` reconstructs a URL for YouTube and Vimeo and returns `null` for other providers. Only a
YouTube item is represented in the actual-derived test fragment; Vimeo is covered as a synthetic
helper contract. Provider names, video IDs, reconstructed URLs, and `Unknown.rawJson` remain
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

Pull requests run Detekt, Android unit tests, and Kotlin/JS tests in one Ubuntu job. Documentation-only
changes skip the job. The library release workflow verifies the published FANBOX boundary before
publishing; it remains on macOS because the Kotlin Multiplatform publication includes Apple targets.

## Contributing golden fixtures

The `:fankt:fanbox` golden tests keep anonymized endpoint responses as Kotlin raw strings under
`fankt/fanbox/src/commonTest/kotlin/me/matsumo/fankt/fanbox/fixture`. Tests decode them with the same
`createFanboxJson()` configuration used in production and compare the complete mapped domain object
with an independently written expected value.

Add a fixture with this fail-closed, one-shot procedure:

1. Identify an actual response that contains the required variation and record only its endpoint and
   non-sensitive request parameters. Do not replace an unavailable variation with synthetic response
   data.

   A task-specific exception may use synthetic data only when the issue explicitly approves it and
   the test verifies an internal branch or fallback without claiming compatibility with the remote
   response schema. Mark the fixture as synthetic in source, and state its provenance, limited
   guarantee, and unverified production schema in the pull request description.

   An issue-approved hybrid fixture may combine a response-derived envelope and field representation
   with a composed type-specific fragment when the target variation cannot be captured. Mark the
   response-derived, composed, and unverified parts separately in source and in the pull request.
   Never describe the complete hybrid fixture as response-derived.
2. In the current implementation session, disable shell tracing and HTTP header/body logging, set
   `umask 077`, and create a private temporary directory outside the repository. Inject
   `FANBOXSESSID` through a temporary process environment without echoing it or placing it in a
   command argument, shell history, screenshot, or artifact. The HTTP process reads the cookie only
   from that environment and writes the response body directly to the private directory with mode
   `0600`.
3. Keep the raw body outside the working tree. In the private directory, create a candidate by
   replacing each known identity and free-form value wholesale. This includes user and creator IDs,
   names, post text, titles, excerpts, descriptions, comments, URL hosts and query tokens, file names,
   and CSRF tokens. Use obvious fixture values such as `fixture-creator-*`, sequential numeric user
   IDs, `example.invalid`, and `fixture-token`. Remove unconsumed unknown response fields.
4. Only after anonymization, add any synthetic unknown field required to exercise API drift handling.
   Never label synthetic content as response-derived.
5. Collect source identifiers and tokens only in sanitizer process memory and run a fixed-string
   quiet scan against the candidate and staged fixture. Record only the pass/fail result, then discard
   the in-memory values without creating another exact-value file. A match, an unavailable scan, or
   uncertain anonymization fails the gate: unstage the candidate and return to step 3 without
   committing it.
6. Obtain an independent privacy review of the sanitized staged diff. The reviewer receives neither
   the credential, raw response, nor exact-value list, and checks placeholder consistency, identity
   fields, URL queries, high-entropy strings, and wholesale replacement of free-form fields. A
   rejection or missing review fails the gate.
7. Delete the raw response and private temporary directory, then unset the temporary environment
   variable whether the capture succeeds or fails. If a credential or raw value reaches output,
   logs, the repository, a screenshot, or an artifact, stop, clean up, report the exposure, and decide
   whether credential rotation is required before continuing.
8. Add the sanitized Kotlin raw string and a full expected domain object that does not derive values
   from the fixture at runtime. Run:

   ```shell
   ./gradlew :fankt:fanbox:allTests :fankt:fanbox:detekt
   ```

The repository contains no reusable capture component, script, or module. Artifact review reduces
privacy risk but cannot prove detection of arbitrary personal information in unknown response fields;
known identity and free-form fields therefore always use whole-value replacement.

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
