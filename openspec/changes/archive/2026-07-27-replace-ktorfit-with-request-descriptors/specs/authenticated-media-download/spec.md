## MODIFIED Requirements

### Requirement: Extension-specific download APIs are removed
The public `Fanbox` API SHALL expose one URL-based chunk-streaming `download` operation instead of `downloadPostFile`, `downloadPostImage`, and `downloadPostThumbnailImage`. Production source SHALL contain no endpoint descriptor, repository route, or generated declaration that reconstructs an extension-specific download URL. Trace: issue #32 download API removal checklist.

#### Scenario: Public download operation inventory
- **WHEN** the production `Fanbox` download API surface and descriptor/route inventory are inspected
- **THEN** only the URL-based public download operation remains and no `.jpg` download route is declared
