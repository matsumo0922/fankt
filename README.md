# fankt

<img src="https://img.shields.io/maven-central/v/me.matsumo.fankt/fanbox">

An unofficial API wrapper for pixivFANBOX and Fantia.  
Compatible with Kotlin Multiplatform (KMP) and usable on Android and iOS.

## Status

#### :white_check_mark: pixivFANBOX
  - All features are fully functional.
#### :construction: Fantia
  - Currently under development.
  - Please wait for the official release.

## Platforms

| Platform | Support                      |
|----------|------------------------------|
| Android  | :white_check_mark: Supported |
| iOS      | :white_check_mark: Supported | 
| Desktop  | :x: Not Supported            |
| Web      | :x: Not Supported            |

## Usage

### Download

Fankt is available on Maven Central.  
Add the library to your project using the following code:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("me.matsumo.fankt:fanbox:$version")
    implementation("me.matsumo.fankt:fantia:$version")
}
```

### API Reference
[API Reference 🔎](https://matsumo0922.github.io/fankt/)

### pixivFANBOX

To use the pixivFANBOX API, you need a session ID called `FANBOXSESSID`.  
You can obtain this session ID from the cookies after logging in via a browser.  
Refer to [PixiView-KMP](https://github.com/matsumo0922/PixiView-KMP) for details about this approach.  
Set the session ID using `fanbox.setSessionId(sessionId: String)` before using the API.

Additionally, you need to obtain a CSRF token (X-CSRF-Token) for operations like POST requests.  
You can acquire this token by calling `fanbox.updateCsrfToken()`.  
Make sure to retrieve the token before using the API.  
Since the CSRF token expires after a certain period, it is recommended to update it as needed before API calls.

```kotlin
val fanbox = Fanbox()

// Set the session ID and CSRF token before using the API
fanbox.setSessionId("your_session_id")
fanbox.updateCsrfToken()

// Example: Retrieve posts from a creator
fanbox.getCreatorPosts(creatorId = FanboxCreatorId("creator_id"))
```

### Fantia

WIP (Work in Progress)

## Samples

A [sample app](./composeApp/) with a Swagger UI-like interface is available.  
You can test API results by inputting the required parameters.

## Contributing golden fixtures

The `:fankt:fanbox` golden tests keep anonymized endpoint responses as Kotlin raw strings under
`fankt/fanbox/src/commonTest/kotlin/me/matsumo/fankt/fanbox/fixture`. Tests decode them with the same
`createFanboxJson()` configuration used in production and compare the complete mapped domain object
with an independently written expected value.

Add a fixture with this fail-closed, one-shot procedure:

1. Identify an actual response that contains the required variation and record only its endpoint and
   non-sensitive request parameters. Do not replace an unavailable variation with synthetic response
   data.
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
