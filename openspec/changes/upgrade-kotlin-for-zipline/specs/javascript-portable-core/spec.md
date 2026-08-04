## ADDED Requirements

### Requirement: JavaScript portable core remains compatible with the Zipline toolchain
Issue #88 の「JS target を含む全 target の build / test」に対応する。既存の JavaScript portable core は Kotlin 2.3.21 と kotlinx.serialization 1.10.0 の toolchain 上で compile され、既存の portable fixture test を実行しなければならない（SHALL）。

#### Scenario: Portable core survives the toolchain update
- **WHEN** Kotlin 2.3.21 と kotlinx.serialization 1.10.0 で `:fankt:fanbox:jsTest` を実行する
- **THEN** JavaScript compilation が成功し、既存の portable fixture test が失敗なく完了する
