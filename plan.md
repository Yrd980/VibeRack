# Smart Chassis MVP Implementation Plan

## Context

Baseline documents:

- `CONTEXT.md`
- `docs/adr/0001-prefer-protocol-first-viberack.md`
- `docs/智能物料管理系统_项目技术文档_v1.0.md`
- `docs/智能底盘BLE接口规格_v0.1.md`

Product rule: smart chassis binding table is the source of truth. App-side `stock_item`, `container.tableSeq/tableCrc16`, and `stock_operation` must follow successful BLE operations or verified `READ_ALL` restore.

## Current State

- Room already has unified container primitives: `container`, `container_slot`, `stock_item`, `stock_operation`.
- Smart chassis cache fields exist on `ContainerEntity`: MAC, batch, proto version, battery, status flags, table sequence, CRC, seen/synced timestamps.
- BLE protocol layer has codecs, GATT client, operations facade, scanner, fake client, table-info refresh, READ_ALL validation, and light commands.
- Protocol test coverage exists for advertisement parsing, slot records, CRCs, table info, light command encoding, NFC device URI parsing, and fake chassis write/read/reflow behavior.
- READ_ALL end frame is documented as `op=0x02,status=0x00,payload=0xFF`, and the app validates record count, slot numbers, table slot count, and CRC before restore.
- Smart chassis slot operations now use a hardware-first boundary for `WRITE_ONE`, `CLEAR_ONE`, `SET_QTY`, `INSERT_AT`, `REMOVE_AT`, and `MOVE_BLOCK`: BLE succeeds first, then local `stock_item`, `container.tableSeq/tableCrc16`, and `stock_operation` are updated with returned table info.
- Scan advertisements treat `table_seq` as a low-16 hint. A low16 mismatch marks cache as possibly stale by clearing sync state until full Table Info / READ_ALL validation.
- Protocol part ID generation and validation are centralized in `ProtocolPartIdStrategy` and shared by inbound, slot operations, hardware restore, backup import, box migration, and BLE write validation.
- Containers screen can scan/register chassis, connect, read restore preview, confirm restore, light a slot, write/clear/set quantity, and render a 25-slot digital twin grid with occupied/empty/low-stock/active-light states.
- Search supports find-by-light for smart chassis stock records.
- BOM has an initial pick-to-light path grouped by chassis MAC.
- NFC device URI parsing and routing exist for `lcscerp://device?mac=...&batch=...&ver=...`.

## Remaining Critical Gaps

1. Smart chassis reflow is hardware-first but still refreshes local cache by deterministic app-side transforms after successful `INSERT_AT`, `REMOVE_AT`, and `MOVE_BLOCK`. This is acceptable as the current protocol-tied fallback, but the stronger target is success -> READ_ALL -> CRC/table_seq validation before trusting cache.
2. The main Inbound tab still writes through the legacy location-oriented `InventoryRepository.addInbound` path. Containers has a hardware-first slot inbound dialog, but stock-in is not yet a first-class smart chassis workflow from scan/enter component -> choose chassis/slot -> green `STOCK_IN` guidance -> `WRITE_ONE` -> local ledger.
3. BOM pick-to-light is still an initial one-shot grouped light command. It does not yet maintain a pick session with per-target done state, remaining mask recomputation, repeated `PICK`, and `OFF` on complete/cancel.
4. Digital twin exists inside Containers, but it is not yet the app's primary smart chassis entry from Home/Inventory/NFC resume flows. It also lacks polish around slot menu anchoring and richer physical guidance states.
5. Legacy `inventory_item` / `inventory_txn` compatibility mirrors still coexist with `stock_item` / `stock_operation`, so legacy location assumptions continue to leak into inbound, search, and BOM workflows.

## Completed Implementation Batch

### 1. Protocol Test Coverage - Complete

Implemented JVM tests for:

- `SmartChassisCodec` advertisement parsing.
- 16-byte slot record encode/decode.
- CRC-8/MAXIM and CRC-16/CCITT-FALSE.
- `SmartChassisTableInfo` parse.
- 17-byte light command encode.
- NFC device URI round trip.
- `FakeSmartChassisClient` write/read/reflow/table-seq behavior.

Target files:

- `app/src/test/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisCodecTest.kt`
- `app/src/test/java/com/example/lcsc_android_erp/core/ble/smart/FakeSmartChassisClientTest.kt`
- `app/src/test/java/com/example/lcsc_android_erp/core/nfc/NfcLabelPayloadCodecTest.kt`

Verify:

- `./gradlew :app:testDebugUnitTest`

### 2. Fix Protocol Edge Cases - Complete

Implemented before product UI work:

- Decided and documented `READ_ALL` end frame as `op=0x02,status=0x00,payload=0xFF`.
- Changed light no-response handling so write-start success proceeds to light status read without depending on `onCharacteristicWrite`.
- Preserved scan/NFC identity across GATT connect so connected device retains batch, proto version, battery, status, table-seq low16, and advertised name.
- Added protocol version guard in NFC/scan open path: unsupported future protocol blocks writes and shows upgrade message.

Target files:

- `docs/智能底盘BLE接口规格_v0.1.md`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisGattClient.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisManager.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`

### 3. Create Hardware-First Commit Boundary - Complete

Added a smart chassis application service/repository path that:

- Executes BLE operation first.
- Receives `SmartChassisTableInfo`.
- Updates local cache only after success.
- Writes real `tableSeqBefore`, `tableSeqAfter`, `tableCrc16`, `bleOpcode`, and `bleStatus`.
- Does not mutate local stock on BLE failure.

Operations covered:

- `WRITE_ONE`
- `CLEAR_ONE`
- `SET_QTY`
- `INSERT_AT`
- `REMOVE_AT`
- `MOVE_BLOCK`

Acceptance:

- Smart chassis local stock changes happen only after BLE success.
- Failure leaves `stock_item` unchanged.
- Operation log reflects returned table sequence.

### 4. Protocol-Driven Reflow - Partial

For smart chassis:

- Do not independently renumber slots before hardware success.
- Send `INSERT_AT`, `REMOVE_AT`, or `MOVE_BLOCK` first.
- On success, update cache by one shared deterministic transform explicitly tied to the successful opcode and returned table info.

Current status: smart chassis reflow sends hardware `INSERT_AT`, `REMOVE_AT`, or `MOVE_BLOCK` first, then applies shared deterministic local transforms and records returned table info. Next hardening step is to refresh via READ_ALL after successful reflow or add explicit transform regression coverage tied to each opcode.

Legacy `BOX` may continue using local transforms until box model is migrated.

### 5. Stale Cache State - Complete

Implemented scan-to-cache freshness:

- Treat advertisement `table_seq` as low16 hint, not full sequence.
- If low16 differs from local `tableSeq & 0xFFFF`, mark chassis as possibly stale.
- On connect, read full Table Info and CRC.
- If full table info differs from local cache, show stale state and offer/read `READ_ALL`.

### 6. Centralize Protocol Part ID Strategy - Complete

Extracted one service for protocol part id rules:

- Catalog component: use valid `C...` id when available.
- Custom/manual component: generate stable `M...` id.
- Enforce `^[CM][A-Z0-9]{0,9}$`.
- Use the same rule in inbound, slot operations, hardware restore, backup import, and box/location migration.

Target starting points:

- `app/src/main/java/com/example/lcsc_android_erp/data/repository/InventoryRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`

### 7. Digital Twin MVP - Complete

Made smart chassis visible as an MVP workflow:

- 25-slot grid.
- Occupied/empty/low-stock visual states.
- Battery, status flags, table sequence, CRC/stale status.
- Tap slot to FIND.
- Slot action menu for stock-in, clear, set quantity.
- Restore preview entry from the same screen.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersScreen.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`

## Next Implementation Batch

### 8. Smart Chassis Inbound Flow

Add a first-class stock-in workflow:

- Scan/enter component.
- Select smart chassis and slot.
- Send `STOCK_IN` green light.
- User confirms placement.
- Send `WRITE_ONE`.
- Only after success, update local stock ledger.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/feature/inbound/InboundViewModel.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/inbound/MaterialInboundDialog.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`

Acceptance:

- The main Inbound tab can complete a smart chassis stock-in without going through a legacy storage location.
- BLE `WRITE_ONE` is the only binding-table mutation for smart chassis stock-in.
- Local `stock_item` and operation log update only after BLE success and returned table info.

### 9. BOM Pick-To-Light Completion Loop

Complete the pick session:

- Show each pick target with chassis, slot, part id, and row context.
- Let user mark each item done.
- Recompute remaining slot mask per chassis.
- Resend `PICK` mask after each completion.
- Send `OFF` when complete or canceled.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/feature/search/BomWorkflow.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchViewModel.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchScreen.kt`

### 10. Reflow Cache Hardening

For smart chassis reflow operations:

- Prefer READ_ALL after successful `INSERT_AT`, `REMOVE_AT`, or `MOVE_BLOCK`.
- Validate record count, slot numbers, `slot_count`, CRC, and returned `table_seq` before updating cache.
- If READ_ALL is unavailable or too slow, keep deterministic transforms but add focused tests that prove app cache matches fake hardware after each opcode.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisOperations.kt`
- `app/src/test/java/com/example/lcsc_android_erp/data/repository/SlotOperationRepositoryImplTest.kt`

### 11. Digital Twin Entry Polish

- Route Home / Inventory / NFC resume flows into the selected smart chassis digital twin when hardware identity is known.
- Improve slot action menu anchoring and compact controls.
- Keep the first screen focused on the usable twin, not a legacy container list, when the user arrives from NFC or chassis scan.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/home/HomeScreen.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryScreen.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersScreen.kt`

## Deferred Work

- Migrate legacy `inventory_item/inventory_txn` into compatibility mirrors of `stock_item/stock_operation`.
- Replace box fixed ID offset mapping with a real relation or full `container(type=BOX)` model.
- Add catalog enrichment after hardware restore.
- Add BLE GATT simulator/server or firmware trace replay tests.
- Implement connection parameter tuning if Android API/device support is adequate.

## Suggested Next Session Prompt

Read `plan.md`, `CONTEXT.md`, `docs/adr/0001-prefer-protocol-first-viberack.md`, `docs/智能物料管理系统_项目技术文档_v1.0.md`, and `docs/智能底盘BLE接口规格_v0.1.md`. Current priority is batch 8: make the main Inbound flow smart-chassis-first. The flow should scan/enter a component, choose chassis and slot, send `STOCK_IN` green guidance, wait for user confirmation, send `WRITE_ONE`, then update local `stock_item` / `stock_operation` only after BLE success. Verify with `./gradlew :app:compileDebugKotlin` and focused JVM tests if repository logic changes.
