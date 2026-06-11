# Architecture Review 2026-06-12

## Scope

This document records the architecture review generated from the current VibeRack product direction.

Current product truth:

- `docs/智能物料管理系统_项目技术文档_v1.0.md`
- `docs/智能底盘BLE接口规格_v0.1.md`

No code change is proposed here. This document is an alignment artifact for choosing the next architecture-deepening candidate.

## Architecture Vocabulary

- Module: anything with an interface and an implementation.
- Interface: everything a caller must know to use the module correctly.
- Implementation: the code inside a module.
- Depth: leverage at the interface.
- Deep module: a small interface hiding substantial behavior.
- Shallow module: an interface nearly as complex as the implementation.
- Seam: where a module's interface lives.
- Adapter: a concrete thing satisfying an interface at a seam.
- Leverage: what callers get from depth.
- Locality: what maintainers get from depth.

## Product Contract

The app is moving from a generic offline warehouse ERP toward a smart component management system:

- scanned inbound
- NFC wake-up
- digital twin container and slot view
- find-by-light
- BOM pick-to-light
- local/cloud stock ledger
- label printing for non-smart containers
- hardware restore from smart chassis binding tables

The key contract is the unified container model:

- legacy storage locations are 1-slot containers
- boxes are N-layer containers
- smart chassis are 25-slot BLE/NFC containers
- stock points to `(containerId, slotId)`
- hardware is the binding-table source of truth for smart chassis flows

## Findings

### 1. Deepen Stock Placement

Recommendation strength: Strong

Files:

- `app/src/main/java/com/example/lcsc_android_erp/domain/repository/InventoryRepository.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/repository/BoxRepository.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/repository/ContainerRepository.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/InventoryRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/InventoryBackupManager.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/dao/DashboardDao.kt`

Problem:

The schema already has the P0 shape: `container`, `container_slot`, `stock_item`, and `stock_operation`. The caller-facing modules still expose three mental models: storage location, box layer, and container slot. Inbound, transfer, delete, box binding, hardware restore, backup, and dashboard logic must know which ledger they are touching.

Solution:

Make stock placement the deep module: one interface for container, slot, stock item, quantity state, safety threshold, and operation log. Legacy locations and boxes should become adapters behind that seam or be retired once the migration is complete.

Benefits:

- locality: stock ledger invariants live in one module
- leverage: digital twin, BOM pick-to-light, restore, backup, and dashboard share one interface
- tests hit the same interface as callers
- backup and dashboard stop being legacy-location-only
- box and smart chassis flows stop duplicating placement rules

### 2. Deepen Smart Chassis Operations

Recommendation strength: Strong

Files:

- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisClient.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisManager.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisCodec.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchViewModel.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`
- `hardware/PartRack-Hardware/firmware/nrf52/app/src/binding_table.c`

Problem:

The packet and opcode implementation is mostly deep: constants and byte layouts live in `SmartChassisProtocol` and `SmartChassisCodec`. Product operations still leak into callers. `ContainersViewModel` and `SearchViewModel` manually check connection state, compare MAC addresses, build slot masks, choose colors/timeouts, send light commands, and perform READ_ALL restore.

Solution:

Add a smart chassis operations module above the GATT adapter. It should own connect-if-needed, find-by-light, stock-in guide, pick mask, READ_ALL restore, cache freshness, `table_seq`/CRC policy, and operation error modes.

Benefits:

- locality: smart chassis workflow rules live in one module
- leverage: screens call product operations, not packet operations
- fake adapter can test workflow without BLE hardware
- GATT adapter remains byte-accurate
- `table_seq` freshness stops leaking into UI code

### 3. Extract BOM Workflow

Recommendation strength: Worth exploring

Files:

- `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchViewModel.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/search/BomSpreadsheetParser.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchUiState.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/datastore/UserPreferencesRepository.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/model/SearchInventoryRecord.kt`

Problem:

`SearchViewModel` mixes search, BOM parsing state, persistent BOM bindings, temporary bindings, ignored rows, passive footprint matching, direct inbound, empty box assignment, smart chassis find-by-light, and BOM pick-to-light.

Solution:

Move BOM matching and pick-plan creation behind one workflow interface. That module should produce match rows, assignment candidates, and pick groups. Execution should use stock placement and smart chassis operations.

Benefits:

- locality: BOM matching rules live together
- leverage: BOM can target any container type
- pick plan can be tested without Compose or BLE
- UI stops building smart chassis masks
- passive footprint/value matching becomes a focused test surface

### 4. Route Physical Targets Once

Recommendation strength: Worth exploring

Files:

- `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/nfc/NfcLabelPayload.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryOpenRequest.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesOpenRequest.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersUiState.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryScreen.kt`

Problem:

`LcscApp` knows how to route NFC labels and open requests for locations, materials, box layers, and smart chassis devices. The interface is spread across feature-specific request objects and signal counters. Inventory opening is timing-sensitive because the request is received in one place and completed later by screen effects.

Solution:

Create a physical target routing module. It should map NFC labels, search results, and inbound results to one target interface, then use route adapters for location, box layer, material, and device targets.

Benefits:

- locality: target parsing and routing rules live together
- leverage: one interface covers NFC, search, and inbound jumps
- Compose timing is isolated
- new physical targets add adapters, not app-level branching
- tests avoid recreating the navigation graph

### 5. Make Protocol Constants a Contract

Recommendation strength: Speculative

Files:

- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisUuids.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisProtocol.kt`
- `hardware/PartRack-Hardware/protocol/viberack_protocol.h`
- `hardware/PartRack-Hardware/firmware/nrf52/app/src/app_ble.c`
- `hardware/PartRack-Hardware/tools/ble_gatt_smoke_test.py`
- `docs/智能底盘BLE接口规格_v0.1.md`

Problem:

Android, firmware, smoke tests, and protocol docs repeat UUIDs, opcodes, sizes, and record facts. The current interface is social memory: every adapter must be updated together.

Solution:

Keep a small generated or validated protocol contract artifact for UUIDs, opcodes, sizes, and record fields. Android, firmware, and smoke tests can import or verify against it.

Benefits:

- locality: protocol constants change in one place
- leverage: adapters stay smaller
- tests catch spec drift
- docs become verifiable
- low runtime risk

## Top Recommendation

Start with Deepen Stock Placement.

Reason: it unlocks the P0 product contract first. Digital twin, hardware restore, find-by-light, BOM pick-to-light, backup, dashboard, and stock ledger all need the same container + slot interface before the other modules can stay deep.

## Open Decision

Choose one candidate for the next design pass:

1. Deepen Stock Placement
2. Deepen Smart Chassis Operations
3. Extract BOM Workflow
4. Route Physical Targets Once
5. Make Protocol Constants a Contract

After a candidate is chosen, the next step is a focused design discussion to define constraints, module responsibilities, adapters, migration scope, and verification.
