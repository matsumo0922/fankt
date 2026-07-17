## 1. Domain and entity contract

- [ ] 1.1 Change `FanboxPostDetail.Body.Unknown` to a serializable data class carrying aliased/defaulted `type` and `rawBodyJson`, with round-trip and legacy-object decode tests
- [ ] 1.2 Preserve `post.body` as `JsonElement?` in the response entity and keep a typed internal body payload model for known types

## 2. Type-directed mapping

- [ ] 2.1 Replace payload-content inference with a single `post.type` branch for article, image, file, unsupported, and unknown types
- [ ] 2.2 Preserve normalized raw body JSON for Unknown and translate known typed-body serialization failures to `FanboxException.SchemaMismatch` at the repository boundary

## 3. Fixture and production-path verification

- [ ] 3.1 Update actual-response-derived article, image, file, and text golden expectations for the new contract
- [ ] 3.2 Add clearly marked synthetic video, entry, unknown-type, and conflicting-field fixtures and mapper scenarios
- [ ] 3.3 Add public `Fanbox.getPostDetail` MockEngine tests proving Unknown fallback and malformed-known-body `SchemaMismatch` through production wiring

## 4. Documentation and validation

- [ ] 4.1 Update the golden fixture contributor contract with the issue-approved synthetic branch-test exception and its limits
- [ ] 4.2 Search README and docs for affected names and remove or update stale statements
- [ ] 4.3 Run targeted tests, full fanbox tests, detekt, build/compile checks, OpenSpec validation, and diff checks through the validation lease
