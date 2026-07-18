## 1. Transport and public models

- [x] 1.1 Decode nullable style and inline-link lists with nullable/defaulted inner fields on known text blocks
- [x] 1.2 Extend public Text with compatibility-defaulted StyleSpan, LinkSpan, and isHeader values plus range and URL trust-boundary KDoc

## 2. Text block mapping

- [x] 2.1 Map p and header blocks by block type while preserving complete span order and unknown style strings
- [x] 2.2 Preserve empty-string paragraphs in their original block positions without changing null-text policy
- [x] 2.3 Degrade incomplete or malformed span metadata to omitted/empty spans without hiding non-span known-block schema errors

## 3. Actual-derived fixtures and tests

- [x] 3.1 Build anonymized actual-derived header, bold, inline-link, combined-span, and empty-paragraph fixture fragments with explicit provenance
- [x] 3.2 Add mapper and public production-path assertions for text metadata, malformed-span degradation, and updated Article A/B golden ordering/header expectations
- [x] 3.3 Add Text serialization round-trip and legacy decode compatibility tests
- [x] 3.4 Run private-value exclusion scan and independent staged-diff privacy review

## 4. Documentation and validation

- [x] 4.1 Verify README and KDoc references for Text metadata, span/URL validation, downstream compatibility, and PixiView non-goal
- [x] 4.2 Run full fanbox tests, detekt, and build through the validation lease and record the successful HEAD
