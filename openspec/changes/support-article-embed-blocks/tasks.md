## 1. Entity and public model

- [ ] 1.1 Decode article blocks as raw JsonObject values and add typed block `embedId` plus `embedMap` entries with `contentId` / `videoId`
- [ ] 1.2 Add serializable public `Article.Block.Embed` and `Article.Block.Unknown` values with provider URL restoration and KDoc
- [ ] 1.3 Add public-model serialization and URL helper tests, including the unknown-provider null contract

## 2. Type-based mapping

- [ ] 2.1 Map article blocks strictly by `block.type` for text, image, file, url_embed, and embed variants
- [ ] 2.2 Preserve unknown block types and unresolved map references as `Block.Unknown` raw JSON without dropping following blocks
- [ ] 2.3 Normalize `contentId ?: videoId` and preserve unknown provider or missing content ID as referenced embed raw JSON

## 3. Fixture and production-path evidence

- [ ] 3.1 Add a source-marked synthetic article embed fixture covering six known providers, videoId fallback, unknown provider, unknown block, and unresolved reference
- [ ] 3.2 Add mapper tests for block-type precedence, raw fallback selection, and block ordering
- [ ] 3.3 Add a public `Fanbox.getPostDetail` test proving ContentNegotiation-to-mapper wiring for embed blocks and URL restoration

## 4. Documentation and validation

- [ ] 4.1 Confirm README fixture guidance remains current and add KDoc for the public embed and raw fallback contracts
- [ ] 4.2 Run documentation grep, `openspec validate support-article-embed-blocks`, and full `:fankt:fanbox` test / lint / build validation through the validation lease
