## Why

The current download API reconstructs FANBOX media URLs with a hard-coded `.jpg` suffix, so it cannot represent PNG/GIF images or ZIP/PSD/MP4 files and is bypassed by the only known consumer. That bypass uses the authenticated raw client without constraining the destination host, so the replacement must accept API-provided URLs while preventing authenticated requests to arbitrary origins.

## What Changes

- **BREAKING**: remove `FanboxDownloadApi`, `FanboxDownloadRepository`, `downloadPostFile`, `downloadPostImage`, and `downloadPostThumbnailImage`.
- Add one public `Fanbox.download(url, onProgress)` operation that creates a deferred GET request for an API-provided media URL using a `Fanbox`-owned shared download client.
- Reject non-HTTPS URLs and hosts outside the observed FANBOX media allowlist before a request is created or redirected.
- Preserve progress callback and deferred `HttpStatement` behavior so PixiView-KMP can replace its current raw-client request without changing its platform download loops.
- Remove the obsolete route-diagnostic inventory for synthesized download routes and document the current download contract.

## Capabilities

### New Capabilities

- `authenticated-media-download`: Defines validated authenticated media download requests, progress reporting, and shared-client execution. This traces to issue #32 acceptance criteria.

### Modified Capabilities

- `request-time-csrf`: Preserve request-time token resolution on FANBOX API/raw requests while preventing the download client from attaching that token to external CDN hosts.

## Impact

- Public `Fanbox` download methods and KDoc.
- `fankt/fanbox` download-client construction, generated Ktorfit API inventory, and tests; the existing owned-client lifecycle contract remains unchanged.
- README download guidance and the repository sample request-name inventory.
- PixiView-KMP can migrate from `getHttpClient().prepareGet(...)` to `Fanbox.download(...)`; changing that separate repository is not part of this PR.
- No database, cookie-matching, CSRF token state/lifetime, URL-response mapping, or thumbnail URL transformation changes; the download transport stops forwarding the token outside the `fanbox.cc` boundary.
