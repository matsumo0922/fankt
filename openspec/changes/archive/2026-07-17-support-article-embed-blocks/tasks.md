## 1. Entity and public model

- [x] 1.1 Decode article blocks as raw JsonObject values, decode only known block types, and add typed block `embedId` plus tolerant `embedMap` entries with `contentId` / `videoId`
- [x] 1.2 Add serializable public `Article.Block.Embed` and `Article.Block.Unknown` values with provider URL restoration and KDoc
- [x] 1.3 Add public-model serialization and URL helper tests, including the unknown-provider null contract

## 2. Type-based mapping

- [x] 2.1 Map article blocks strictly by `block.type` for text, image, file, url_embed, and embed variants
- [x] 2.2 Preserve unknown block types and unresolved map references as `Block.Unknown` raw JSON without dropping following blocks
- [x] 2.3 Normalize `videoId ?: contentId` and preserve missing/unknown provider or missing content ID as referenced embed raw JSON

## 3. Fixture and production-path evidence

- [x] 3.1 Add a source-marked synthetic article embed fixture covering six known providers, optional entry id, videoId fallback, missing/unknown provider, malformed unknown block fields, and unresolved reference
- [x] 3.2 Add mapper tests for block-type precedence, raw fallback selection, reference misses, malformed known blocks, and block ordering
- [x] 3.3 Add a public `Fanbox.getPostDetail` test proving ContentNegotiation-to-mapper wiring for embed blocks and URL restoration

## 4. Documentation and validation

- [x] 4.1 Confirm README fixture guidance remains current and add KDoc for the public embed and raw fallback contracts
- [x] 4.2 Run documentation grep, `openspec validate support-article-embed-blocks`, and full `:fankt:fanbox` test / lint / build validation through the validation lease
