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
