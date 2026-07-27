# codegen-free-fanbox-client Specification

## Purpose
TBD - created by archiving change replace-ktorfit-with-request-descriptors. Update Purpose after archive.
## Requirements
### Requirement: Fanbox core does not depend on Ktorfit generation
The `:fankt:fanbox` module SHALL contain no Ktorfit endpoint interface, generated API creation call, Ktorfit runtime dependency, or Ktorfit KSP processor dependency. Ktor SHALL remain an internal implementation dependency of the executor.

#### Scenario: Fanbox dependency graph is inspected
- **WHEN** the fanbox module dependency and source inventory is verified
- **THEN** no Ktorfit dependency, annotation, or generated API symbol is present

#### Scenario: Other modules retain required processors
- **WHEN** Fantia or persistence modules still require Ktorfit or Room KSP processing
- **THEN** their plugins, processors, and generated tasks remain available and unchanged by the fanbox migration

### Requirement: Publication tolerates modules without a common KSP task
Publication wiring SHALL require `kspCommonMainKotlinMetadata` before every sources artifact in a module where that task exists, SHALL skip only the absent task in modules without common KSP processing, and SHALL publish the fanbox sources artifact after its Ktorfit processor is removed. The conditional wiring MUST NOT weaken generated-source ordering for Fantia or persistence modules.

#### Scenario: Fanbox publication is prepared
- **WHEN** the fanbox publication tasks are configured without a common KSP processor
- **THEN** configuration and source artifact creation succeed without referencing a missing task

#### Scenario: KSP module publication is prepared
- **WHEN** Fantia or a persistence module still registers `kspCommonMainKotlinMetadata`
- **THEN** every sources artifact retains a mandatory dependency on that task and includes the generated sources expected by the module

### Requirement: Existing Fanbox behavior remains compatible
Every existing public non-download API operation SHALL preserve its request query and JSON body semantics, request-time authentication, successful domain result, tolerant-list behavior, and typed failure semantics after generated APIs are removed. Existing public operation signatures and domain model signatures MUST NOT be broken by this migration.

#### Scenario: Existing fixture and MockEngine suites run
- **WHEN** the fanbox common, Android unit, boundary, and compatibility tests run after migration
- **THEN** all existing behavior assertions pass through the descriptor executor and pure parser path

#### Scenario: Route drift guard uses descriptors
- **WHEN** the production endpoint inventory is verified
- **THEN** the guard derives coverage from endpoint IDs, descriptor builders, and trusted policies rather than Ktorfit annotations while preserving the 29 declaration and 28 unique-route contract

#### Scenario: Existing ABI is compared
- **WHEN** legacy ABI checks compare the pre-migration public API with the migrated client
- **THEN** existing `Fanbox` operations and domain models remain compatible and internal descriptor/parser symbols do not appear in the public API dump
