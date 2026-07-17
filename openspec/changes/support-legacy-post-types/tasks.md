## 1. Public and transport models

- [ ] 1.1 Add nested video and HTML fields to the post body entity with tolerant videoId/contentId decoding
- [ ] 1.2 Add serializable Text, Video, and Html public body variants, URL restoration, untrusted-HTML KDoc, and exhaustive helper handling

## 2. Type-directed mapping

- [ ] 2.1 Map text, video, and entry strictly from post.type and their required body fields
- [ ] 2.2 Preserve raw Unknown fallback for malformed or unverified text, video, and entry shapes while retaining SchemaMismatch for article, image, and file

## 3. Evidence and fixtures

- [ ] 3.1 Replace the issue #26 branch-only video and entry payloads with clearly documented hybrid text, video, and entry fixtures that separate actual-derived and composed parts
- [ ] 3.2 Add independent mapper expectations, serialization and URL tests, raw-fallback tests, and public Fanbox.getPostDetail production-path tests for all three variants
- [ ] 3.3 Run the private-value exclusion scan against fixture changes without adding captures or credentials to the worktree

## 4. Documentation and validation

- [ ] 4.1 Update the golden fixture contributor contract for hybrid provenance and verify README/KDoc references to the changed body variants
- [ ] 4.2 Run full fanbox tests, detekt, and build through the validation lease and record the successful HEAD
