# Local App Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish local app workflows for smart-slot inbound, unified slot operations, and hardware restore preview without requiring real hardware or cloud sync.

**Architecture:** Add a product-level operation repository over existing Room DAOs and wire it through `AppContainer`. Keep legacy inbound compatible, and use the container feature for smart-slot workflows and restore preview confirmation.

**Tech Stack:** Kotlin, Jetpack Compose, Room, coroutines, existing BLE smart chassis abstractions.

---

## Files

- Modify `domain/repository/StockPlacementRepository.kt` and `data/repository/StockPlacementRepositoryImpl.kt` for slot lookup and operation primitives.
- Add `domain/repository/SlotOperationRepository.kt` and `data/repository/SlotOperationRepositoryImpl.kt`.
- Modify `core/AppContainer.kt` to inject the new repository.
- Modify `core/ble/smart/SmartChassisOperations.kt` to expose restore preview and confirmed import.
- Modify `feature/containers/ContainersUiState.kt`, `ContainersViewModel.kt`, and `ContainersScreen.kt` for smart-slot inbound and restore preview UI.
- Modify `core/database/dao/ContainerDao.kt` / `StockItemDao.kt` only if needed for focused queries.

## Tasks

### Task 1: Slot Operation Repository

- [x] Add operation request/result models and interface.
- [x] Implement local `LEGACY_LOCATION` and `BOX` write/clear/insert/remove/move/set quantity behavior.
- [x] For `SMART_CHASSIS`, support local confirmed writes from app workflows and keep BLE sequencing at the caller boundary.
- [x] Record `stock_operation` rows for every successful operation.

### Task 2: Restore Preview Boundary

- [x] Split READ_ALL restore into preview and confirm operations.
- [x] Preview must count occupied, empty, invalid, and changed records without mutating local stock.
- [x] Confirm must reuse existing `restoreSmartChassisTable` import logic and log table metadata.

### Task 3: Container ViewModel Wiring

- [x] Add selected slot state and dialog state.
- [x] Add actions for smart-slot inbound, slot clear, quantity update, restore preview, and restore confirm/cancel.
- [x] Keep existing scan/connect/find/light-off flows intact.

### Task 4: Container UI

- [x] Add slot actions to smart chassis slots.
- [x] Add local inbound dialog for part ID/number and quantity.
- [x] Add restore preview dialog with summary and confirm/cancel.
- [x] Keep non-smart slot list behavior stable.

### Task 5: Verification

- [x] Run `.\gradlew.bat :app:compileDebugKotlin --no-daemon`.
- [x] Report that hardware behavior is not verified until real device joint testing.
