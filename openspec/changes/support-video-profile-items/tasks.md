## 1. Transport and public model

- [x] 1.1 Preserve raw profile item objects and decode nullable serviceProvider/videoId fields without weakening required id/type schema errors
- [x] 1.2 Replace the public flat ProfileItem with serializable Image/Video/Unknown variants, retain Unknown id/raw data, and document the raw/URL trust boundary
- [x] 1.3 Add YouTube/Vimeo URL reconstruction with null for unknown providers

## 2. Mapping behavior

- [x] 2.1 Map image/video/unknown items by received type while preserving values and list order
- [x] 2.2 Degrade missing/blank video metadata to Unknown raw JSON without failing the creator response
- [x] 2.3 Convert mapper SerializationException back to endpoint-specific SchemaMismatch for direct creator.get and strict creator.search
- [x] 2.4 Preserve tolerant following/Pixiv/recommended list item-drop behavior and existing lenient primitive coercion

## 3. Fixtures and tests

- [x] 3.1 Add a separate whole-value-anonymized actual-derived mixed image/video fragment with explicit hybrid provenance without changing the existing single-capture fixture
- [x] 3.2 Add mapper and public Fanbox.getCreatorDetail production-path tests for the mixed actual-derived fixture
- [x] 3.3 Add explicitly synthetic tests for unknown/incomplete/blank items, YouTube/Vimeo/unknown URL helpers, direct/list/search failure boundaries, lenient coercion, and sealed serialization round-trip/missing discriminator
- [x] 3.4 Run private-value exclusion scan and independent Claude Opus staged-diff privacy review

## 4. Documentation and validation

- [x] 4.1 Update README/KDoc for the breaking migration, consumer branching, raw/URL validation, observed provider scope, and PixiView non-goal
- [x] 4.2 Grep README/docs/KDoc for stale flat ProfileItem usage and run OpenSpec strict validation
- [x] 4.3 Run full fanbox tests, detekt, and build through the validation lease and record the successful HEAD
