## 1. Transport and public models

- [ ] 1.1 Decode nullable url/html/postInfo fields for urlEmbedMap entries
- [ ] 1.2 Extend public Link with compatibility-defaulted type/url fields, non-conflicting linkType serialization, and type/trust-boundary KDoc

## 2. Type-directed mapping

- [ ] 2.1 Preserve type, url, html, and mapped post in the url_embed mapper branch
- [ ] 2.2 Preserve unknown URL embed types without inventing metadata

## 3. Actual-derived fixtures and tests

- [ ] 3.1 Build anonymized actual-derived default, html, html.card, and fanbox.post fixture fragments with explicit provenance
- [ ] 3.2 Add independent mapper and public production-path assertions for all four types, unknown type passthrough, and updated existing Article B Link expectations
- [ ] 3.3 Add Link serialization round-trip, discriminator non-conflict, and legacy decode compatibility tests
- [ ] 3.4 Run private-value exclusion scan and independent staged-diff privacy review

## 4. Documentation and validation

- [ ] 4.1 Verify README and KDoc references for Link metadata, untrusted HTML, and downstream compatibility
- [ ] 4.2 Run full fanbox tests, detekt, and build through the validation lease and record the successful HEAD
