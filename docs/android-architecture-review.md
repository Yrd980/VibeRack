# Android Architecture Review

Generated June 18, 2026. Scope: Android `app/`, Android `domain/`, and Android tests.

## Top Recommendation

Deepen the Slot Binding Workflow first. It aligns with ADR-0001: smart chassis binding table operations are hardware-owned, and app cache plus stock ledger updates should follow successful protocol operations.

## Candidates

### 1. Deepen the Slot Binding Workflow

Files:

- `app/src/main/java/com/viberack/app/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/viberack/app/core/ble/smart/SmartChassisOperations.kt`
- `app/src/main/java/com/viberack/app/data/repository/StockPlacementRepositoryImpl.kt`
- `app/src/main/java/com/viberack/app/data/repository/ContainerRepositoryImpl.kt`
- `domain/src/main/java/com/viberack/app/domain/repository/SlotOperationRepository.kt`

Problem: slot binding is shallow; hardware-owned binding, cache updates, stock ledger changes, and stock operation records leak across several modules.

Solution: deepen the workflow so the interface expresses user intents such as write, clear, insert, remove, move, and set quantity while the implementation owns BLE commit, `table_seq`/CRC checks, app cache update, ledger mutation, and operation recording.

Benefits:

- locality: binding drift logic
- leverage: one test surface
- interface absorbs BLE details
- deletion test removes repeated preflight

### 2. Collapse Physical Guidance

Files:

- `app/src/main/java/com/viberack/app/feature/search/SearchViewModel.kt`
- `app/src/main/java/com/viberack/app/feature/search/BomWorkflow.kt`
- `app/src/main/java/com/viberack/app/feature/containers/ContainersViewModel.kt`
- `app/src/main/java/com/viberack/app/core/ble/smart/SmartChassisOperations.kt`

Problem: Find-by-Light, Pick-to-Light, and Stock-In Guidance each carry slot validation, MAC normalization, grouping, and failure shape in UI-facing modules.

Solution: deepen a Physical Guidance module that consumes stock records or slots and owns light grouping, slot validation, and error reporting.

Benefits:

- locality: light behavior
- leverage: search and containers
- interface hides masks
- tests skip UI state

### 3. Unify Container Read Models

Files:

- `app/src/main/java/com/viberack/app/data/repository/ContainerRepositoryImpl.kt`
- `app/src/main/java/com/viberack/app/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/viberack/app/data/repository/StockPlacementRepositoryImpl.kt`
- `app/src/main/java/com/viberack/app/data/repository/InventoryReadModelMapper.kt`

Problem: read model conversion is shallow and duplicated, so a container or slot shape change spreads across repository implementations.

Solution: deepen one internal mapping module for Room projections, entities, and domain read models.

Benefits:

- locality: model migration
- leverage: all repositories
- interface shrinks reads
- delete duplicate parsing
