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
- BLE protocol layer has codecs, GATT client, operations facade, scanner, fake client, and light commands.
- Containers screen can scan/register chassis, connect, read restore preview, confirm restore, light a slot, and do a basic slot inbound write.
- Search supports find-by-light for smart chassis stock records.
- BOM has an initial pick-to-light path grouped by chassis MAC.
- NFC device URI parsing and routing exist for `lcscerp://device?mac=...&batch=...&ver=...`.

## Critical Gaps

1. `SlotOperationRepositoryImpl` still directly mutates local `stock_item` for smart chassis operations and only records BLE opcode as metadata.
2. `stock_operation.tableSeqAfter` often equals the old local table sequence instead of the BLE-returned `SmartChassisTableInfo.tableSeq`.
3. Smart chassis reflow is still computed by app-side local stock copying. For smart chassis, MCU must execute `INSERT_AT`, `REMOVE_AT`, and `MOVE_BLOCK`.
4. `READ_ALL` end frame is assumed as payload `0xFF`, but the protocol document does not define that frame precisely.
5. Light command uses `WRITE_TYPE_NO_RESPONSE` but still waits for `onCharacteristicWrite`, which may not be reliable.
6. GATT connection rebuilds `SmartChassisDevice` with zeroed identity metadata instead of preserving scan/NFC identity.
7. Broadcast `table_seq` low 16 bits are stored as full `tableSeq`, with no stale-cache state or full uint32 validation after connection.
8. Protocol part id generation is duplicated across repositories.
9. Digital twin is not yet a first-class MVP entry with 25-slot status, stale state, and physical guidance.
10. Inbound and BOM workflows still carry legacy location/box assumptions.

## First Implementation Batch

### 1. Protocol Test Coverage

Add JVM tests for:

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

### 2. Fix Protocol Edge Cases

Implement before product UI work:

- Decide and document `READ_ALL` end frame. Prefer updating spec to define `op=0x02,status=0x00,payload=0xFF` if firmware agrees.
- Change light no-response handling so write-start success proceeds to light status read without depending on `onCharacteristicWrite`.
- Preserve scan/NFC identity across GATT connect so connected device retains batch, proto version, battery, status, table-seq low16, and advertised name.
- Add protocol version guard in NFC/scan open path: unsupported future protocol should block writes and show upgrade message.

Target files:

- `docs/智能底盘BLE接口规格_v0.1.md`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisGattClient.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisManager.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`

### 3. Create Hardware-First Commit Boundary

Add a smart chassis application service/repository path that:

- Executes BLE operation first.
- Receives `SmartChassisTableInfo`.
- Updates local cache only after success.
- Writes real `tableSeqBefore`, `tableSeqAfter`, `tableCrc16`, `bleOpcode`, and `bleStatus`.
- Does not mutate local stock on BLE failure.

Operations to cover:

- `WRITE_ONE`
- `CLEAR_ONE`
- `SET_QTY`
- `INSERT_AT`
- `REMOVE_AT`
- `MOVE_BLOCK`

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisOperations.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/domain/repository/SlotOperationRepository.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/StockPlacementRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/StockOperationEntity.kt`

Acceptance:

- Smart chassis local stock changes happen only after BLE success.
- Failure leaves `stock_item` unchanged.
- Operation log reflects returned table sequence.

### 4. Protocol-Driven Reflow

For smart chassis:

- Do not independently renumber slots in app code.
- Send `INSERT_AT`, `REMOVE_AT`, or `MOVE_BLOCK`.
- On success, update cache by either:
  - `READ_ALL` refresh, preferred for correctness; or
  - one shared deterministic transform explicitly tied to the successful opcode and returned table info.

Legacy `BOX` may continue using local transforms until box model is migrated.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisOperations.kt`

### 5. Stale Cache State

Implement scan-to-cache freshness:

- Treat advertisement `table_seq` as low16 hint, not full sequence.
- If low16 differs from local `tableSeq & 0xFFFF`, mark chassis as possibly stale.
- On connect, read full Table Info and CRC.
- If full table info differs from local cache, show stale state and offer/read `READ_ALL`.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/ContainerEntity.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersUiState.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersScreen.kt`

### 6. Centralize Protocol Part ID Strategy

Extract one service for protocol part id rules:

- Catalog component: use valid `C...` id when available.
- Custom/manual component: generate stable `M...` id.
- Enforce `^[CM][A-Z0-9]{0,9}$`.
- Use the same rule in inbound, slot operations, hardware restore, backup import, and box/location migration.

Target starting points:

- `app/src/main/java/com/example/lcsc_android_erp/data/repository/InventoryRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/SlotOperationRepositoryImpl.kt`
- `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`

### 7. Digital Twin MVP

Make smart chassis visible as an MVP workflow:

- 25-slot grid.
- Occupied/empty/low-stock visual states.
- Battery, status flags, table sequence, CRC/stale status.
- Tap slot to FIND.
- Slot action menu for stock-in, clear, set quantity.
- Restore preview entry from the same screen.

Target files:

- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersScreen.kt`
- `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`
- Optionally route Home/Inventory to Containers as the smart chassis digital twin entry.

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

## Deferred Work

- Migrate legacy `inventory_item/inventory_txn` into compatibility mirrors of `stock_item/stock_operation`.
- Replace box fixed ID offset mapping with a real relation or full `container(type=BOX)` model.
- Add catalog enrichment after hardware restore.
- Add BLE GATT simulator/server or firmware trace replay tests.
- Implement connection parameter tuning if Android API/device support is adequate.

## Suggested Next Session Prompt

Read `plan.md`, `CONTEXT.md`, `docs/adr/0001-prefer-protocol-first-viberack.md`, `docs/智能物料管理系统_项目技术文档_v1.0.md`, and `docs/智能底盘BLE接口规格_v0.1.md`. Start with batch 1 and 2: add protocol tests, fix the light no-response handling, and clarify READ_ALL end-frame behavior. Verify with `./gradlew :app:testDebugUnitTest`.
