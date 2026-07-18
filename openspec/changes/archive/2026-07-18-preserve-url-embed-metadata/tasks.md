## 1. Transport and public models

- [x] 1.1 Decode nullable url/html/postInfo fields for urlEmbedMap entries
- [x] 1.2 Extend public Link with compatibility-defaulted type/url fields, non-conflicting linkType serialization, and type/trust-boundary KDoc

## 2. Type-directed mapping

- [x] 2.1 Preserve type, url, html, and mapped post in the url_embed mapper branch
- [x] 2.2 Preserve unknown URL embed types without inventing metadata

## 3. Actual-derived fixtures and tests

- [x] 3.1 Build anonymized actual-derived default, html, html.card, and fanbox.post fixture fragments with explicit provenance
- [x] 3.2 Add independent mapper and public production-path assertions for all four types, unknown type passthrough, and updated existing Article B Link expectations
- [x] 3.3 Add Link serialization round-trip, discriminator non-conflict, and legacy decode compatibility tests
- [x] 3.4 Run private-value exclusion scan and independent staged-diff privacy review

## 4. Documentation and validation

- [x] 4.1 Verify README and KDoc references for Link metadata, untrusted HTML, and downstream compatibility
- [x] 4.2 Run full fanbox tests, detekt, and build through the validation lease and record the successful HEAD
