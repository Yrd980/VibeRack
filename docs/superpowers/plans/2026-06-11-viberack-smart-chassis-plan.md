# VibeRack Smart Chassis Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 LCSC Android ERP 从普通库位 + 盒层两套账，演进为符合 VibeRack v1.0 与 BLE v0.1 的统一容器、智能底盘、数字孪生、硬件恢复、找料点灯和 BOM pick-to-light 应用。

**Architecture:** 先建立兼容迁移层：新增统一 `Container + ContainerSlot + StockItem + StockOperation` 模型，把旧 `storage_location` 视为 1 槽容器，把旧 `component_box/box_layer/layer_material` 迁移为 N 槽容器。BLE 先做 byte-accurate codec 和 fake client，再接 Android GATT transport；智能底盘写操作必须以硬件成功响应为准，本地缓存只在 `table_seq`/CRC 校验后更新。

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, Android BLE GATT, Android NFC reader mode, Retrofit/Jsoup, existing Gradle wrapper.

---

## Assumptions And Tradeoffs

- 当前代码库已实现扫码入库、搜索、BOM 解析/匹配、NFC app URI、P0/Yinlifang 打印、盒/层表；但 BLE GATT 智能底盘协议层尚不存在。
- 当前数据模型存在两套位置账：`storage_location + inventory_item` 和 `component_box + box_layer + layer_material`。P0 必须优先统一，否则数字孪生、硬件恢复、BOM 点灯会继续分叉。
- 默认不新增测试、fixtures、snapshots 或 test-only helper，遵守仓库工作协议。字节协议、迁移和排序算法本应有 JVM 单测；本计划把这些列为风险和可选授权项，不作为默认执行步骤。
- BLE v0.1 规格尚未给出自定义服务/特征的具体 128-bit UUID。GATT transport 实现前必须补齐并同步更新 `docs/智能底盘BLE接口规格_v0.1.md`；codec/fake client 可先做。
- 手动物料当前代码生成 `C0...` 本地料号；BLE 规格要求手动物料 `part_id` 为 `M` 开头。计划采用新增 `protocol_part_id` 兼容旧数据，避免立刻大范围改 UI 展示键。
- `quantity = 0` 必须改为明确的台账 0，不再显示为未知。未知数量要用显式状态表达，智能底盘写入前必须让用户确认数量。
- 计划不删除旧表。第一轮迁移后旧表只做兼容来源，主流程切到新容器账；确认稳定后再做清理迁移。

## Success Criteria

- Existing flows still work after each P0 slice: inbound, search, inventory overview, boxes labels, NFC material/location routing, printer screen.
- Existing user data migrates into unified containers without losing stock quantity, component identity, box/layer position, location color, sort mode, or backup import compatibility.
- Smart chassis records encode/decode exactly as BLE v0.1: 16-byte slot record, CRC-8/MAXIM, op/status frames, 17-byte light command, table info, advertisement manufacturer payload.
- Hardware is the source of truth for smart chassis writes: local cache changes only after successful BLE response and `table_seq`/CRC validation or `READ_ALL` refresh.
- Digital twin read-only view can show 25 slots, battery/status/table freshness, heat states, and trigger `FIND`/`STOCK_IN`/`PICK` through fake client first and hardware client later.
- From-hardware restore can read 25 records, create/update components and stock items, enrich LCSC parts online, and create placeholders for manual `M...` parts.
- BOM pick-to-light groups matched rows by smart chassis, sends masks, updates masks when items are checked, and sends `OFF` when a chassis group is complete.
- Verification uses existing checks: `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:assembleDebug`, existing `.\gradlew.bat :app:testDebugUnitTest` only if execution owner chooses to run current tests.

## Current Code Map

- App entry/navigation: `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- DI/container: `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`
- Room database/migrations: `app/src/main/java/com/example/lcsc_android_erp/core/database/AppDatabase.kt`, `app/src/main/java/com/example/lcsc_android_erp/core/database/DatabaseMigrations.kt`
- Legacy location ledger: `StorageLocationEntity`, `InventoryItemEntity`, `InventoryTransactionEntity`, `StorageLocationDao`, `InventoryItemDao`, `InventoryRepositoryImpl`
- Box/layer ledger: `BoxEntity`, `BoxLayerEntity`, `LayerMaterialEntity`, `BoxDao`, `BoxRepositoryImpl`, `feature/boxes`
- NFC URI support: `core/nfc/NfcLabelPayload.kt`, `core/nfc/NfcLabelManager.kt`
- BLE currently used for printer discovery only: `core/printer/P0PrinterManager.kt`; P0 printing connects through classic SPP and must not regress.
- BOM matching: `feature/search/SearchViewModel.kt`, `feature/search/BomSpreadsheetParser.kt`
- Backup/export: `data/repository/InventoryBackupManager.kt`

---

## Phase 0: Alignment And Guardrails

### Task 0.1: Confirm Protocol Constants Before GATT

**Files:**
- Modify: `docs/智能底盘BLE接口规格_v0.1.md`
- Later create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisUuids.kt`

- [ ] Add exact 128-bit UUID values for binding-table service, binding control point, table info, light service, light command, and light status.
- [ ] Keep existing opcodes and payload layouts unchanged.
- [ ] Record that printer P0/Yinlifang remains BLE scan + classic SPP/RFCOMM, separate from smart chassis BLE GATT.
- [ ] Do not start Android GATT discovery code until this document has concrete UUIDs.

**Verification:**

Run:

```powershell
git diff -- docs/智能底盘BLE接口规格_v0.1.md
```

Expected: only UUID/clarification text changed; no opcode, field offset, status, record, or light payload semantics changed.

### Task 0.2: Create A Work Branch And Baseline Check

**Files:** none

- [ ] Create a branch, for example `codex/viberack-smart-chassis`.
- [ ] Run compile before edits.

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: current baseline compiles, or any pre-existing failure is captured before implementation starts.

---

## Phase 1: Unified Container Model

### Task 1.1: Add Domain Models For Containers, Slots, Stock, And Operations

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/ContainerType.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/StockContainer.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/ContainerSlot.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/SlotStockItem.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/StockOperation.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/QuantityState.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/domain/model/SearchInventoryRecord.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/domain/model/StockLocationCell.kt`

- [ ] Define `ContainerType` values: `LEGACY_LOCATION`, `BOX`, `SMART_CHASSIS`.
- [ ] Define `StockContainer` with shared fields: `id`, `code`, `displayName`, `type`, `slotCount`, `colorHex`, `sortMode`, `remark`, `createdAt`, `updatedAt`.
- [ ] Define smart fields as nullable on the same model: `macAddress`, `batchId`, `protoVersion`, `firmwareVersion`, `hardwareVersion`, `batteryPct`, `statusFlags`, `tableSeq`, `tableCrc16`, `lastSeenAt`, `lastSyncedAt`.
- [ ] Define `ContainerSlot`: `id`, `containerId`, `containerCode`, `containerType`, `slotNumber`, `slotCode`, `displayName`, `sortOrder`.
- [ ] Define `SlotStockItem`: `id`, `componentId`, `containerId`, `slotId`, `slotNumber`, `partNumber`, `protocolPartId`, `quantity`, `quantityState`, `safetyStockThreshold`, component display fields, `updatedAt`.
- [ ] Define `QuantityState.KNOWN` and `QuantityState.UNKNOWN`; make UI-facing models stop treating numeric 0 as unknown.
- [ ] Define `StockOperationType` values for current and future flows: `INBOUND`, `ADJUST`, `TRANSFER_OUT`, `TRANSFER_IN`, `DELETE`, `WRITE_ONE`, `CLEAR_ONE`, `INSERT_AT`, `REMOVE_AT`, `MOVE_BLOCK`, `SET_QTY`, `READ_ALL_RESTORE`, `LIGHT_FIND`, `LIGHT_PICK`, `LIGHT_SORT`, `LIGHT_STOCK_IN`.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: new models compile without touching persistence yet.

### Task 1.2: Add Room Entities And Migration v7

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/ContainerEntity.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/ContainerSlotEntity.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/StockItemEntity.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/StockOperationEntity.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/database/entity/ComponentEntity.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/database/DatabaseMigrations.kt`
- Generated: `app/schemas/com.example.lcsc_android_erp.core.database.AppDatabase/7.json`

- [ ] Add `ComponentEntity.protocolPartId` with a unique index if SQLite accepts nullable uniqueness for the migration shape. Migration value:
  - LCSC parts: `protocol_part_id = part_number`.
  - Legacy manual `C0...` parts: `protocol_part_id = 'M' || printf('%09d', id)`.
  - Other local/unknown parts: `protocol_part_id = upper(substr(part_number, 1, 10))` only when already protocol-safe; otherwise use the `M` form.
- [ ] Add `container` table with one row per legacy storage location and one row per box.
- [ ] Add `container_slot` table:
  - each legacy storage location becomes one slot with `slot_number = 1`, `slot_code = code`;
  - each `box_layer` becomes one slot with `slot_number = sortOrder`, `slot_code = layer_code`;
  - smart chassis rows later always have 25 slots.
- [ ] Add `stock_item` table keyed by `container_slot_id`, not by old `location_id`; include `quantity_state`, `safety_stock_threshold`, and timestamps.
- [ ] Migrate old `inventory_item` into `stock_item` through the generated slot for each storage location.
- [ ] Migrate `layer_material` into `stock_item` through each box layer slot.
- [ ] Add `stock_operation` table for timestamped local/hardware operations and future sync.
- [ ] Keep old tables in place for compatibility during the migration window; do not drop `storage_location`, `inventory_item`, `component_box`, `box_layer`, or `layer_material` in v7.
- [ ] Increment database version from 6 to 7 and append `MIGRATION_6_7` to `DatabaseMigrations.ALL`.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Expected: Room compiles and schema `7.json` is generated. Inspect the schema for all new indices and foreign keys.

### Task 1.3: Add DAOs And Repository Bridge

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/dao/ContainerDao.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/dao/StockItemDao.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/dao/StockOperationDao.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/model/ContainerSummaryProjection.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/database/model/ContainerSlotStockProjection.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/repository/ContainerRepository.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`

- [ ] Implement container summary queries for home/container lists.
- [ ] Implement slot stock queries for a container detail/digital twin grid.
- [ ] Implement component search projection from `stock_item`, preserving current `SearchInventoryRecord` data needs.
- [ ] Implement `findByProtocolPartId(protocolPartId)` and `findOrCreateManualPlaceholder(protocolPartId)` support for hardware restore.
- [ ] Expose `ContainerRepository` from `AppContainer`.
- [ ] Do not remove `InventoryRepository` or `BoxRepository`; keep them compiling while the UI is migrated slice by slice.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: app compiles; old UI still uses old repositories until subsequent tasks switch screens.

### Task 1.4: Move InventoryRepository Reads/Writes To StockItem

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/data/repository/InventoryRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/domain/repository/InventoryRepository.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/data/repository/ComponentEnrichmentManager.kt` only if its callback needs slot-based profile refresh.

- [ ] Change dashboard/search/location inventory reads to use `stock_item` projections.
- [ ] Keep current `StorageLocation` APIs as compatibility views over `ContainerType.LEGACY_LOCATION`.
- [ ] Change `addInbound` to create or update a `stock_item` in a legacy 1-slot container when the inbound dialog still supplies a plain location code.
- [ ] Change adjust/transfer/delete operations to write `stock_operation` rows in addition to updating `stock_item`.
- [ ] Refresh inbound category profiles from slot-based stock rows, not old `inventory_item`.
- [ ] Keep current user-facing inventory screens stable before exposing smart containers.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke on emulator/device:

- Existing location inbound still creates visible stock.
- Search finds the inbound component.
- Inventory quantity edit still updates the row.
- Existing printer screen still connects/prints as before.

### Task 1.5: Move BoxRepository To Container Slots

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/data/repository/BoxRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/domain/repository/BoxRepository.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesViewModel.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/boxes/BoxesScreen.kt`

- [ ] Make box creation create `ContainerType.BOX` plus N `container_slot` rows.
- [ ] Make layer binding write `stock_item`, not `layer_material`.
- [ ] Keep `ComponentBox` and `ComponentBoxLayer` domain models as compatibility wrappers for the existing Boxes UI.
- [ ] Make `observeAllLayers()` and `observeEmptyLayers()` read from `container_slot` + `stock_item`.
- [ ] Keep NFC and label payloads with `box`/`layer` fields readable.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Create a box.
- Assign a component to a layer.
- Layer appears occupied.
- Existing 10 mm label generation still shows `BOX-Lxx` and part number.

---

## Phase 2: Operation Semantics Layer

### Task 2.1: Add Slot Operation API

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/SlotOperationRequest.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/SlotOperationResult.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/domain/repository/ContainerRepository.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`

- [ ] Add local operations: `writeOne`, `clearOne`, `insertAt`, `removeAt`, `moveBlock`, `setQuantity`.
- [ ] For `LEGACY_LOCATION`, allow only `writeOne`, `clearOne`, and `setQuantity` on slot 1.
- [ ] For `BOX`, implement insert/remove/move in local DB by adjusting `sortOrder`/`slotNumber` transactionally.
- [ ] For `SMART_CHASSIS`, route operations through a smart chassis client; do not update local DB until the client reports success.
- [ ] Write one `stock_operation` row per operation with `tableSeqBefore`, `tableSeqAfter`, BLE opcode when applicable, raw payload/status, and timestamp.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- A box insert/remove operation leaves contiguous slot numbers.
- A legacy location still behaves as one slot.

### Task 2.2: Make Inbound Use Container/Slot Selection

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/inbound/InboundViewModel.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/inbound/InboundUiState.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/inbound/MaterialInboundDialog.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/domain/model/InboundRecord.kt`

- [ ] Add target slot state: container id/code/type, slot id/number/code, and quantity state.
- [ ] Keep old location-code picker available by showing legacy 1-slot containers.
- [ ] For smart chassis slots, call `STOCK_IN` light command before final write when a client is connected.
- [ ] On final confirmation:
  - non-smart target uses local `writeOne`/upsert;
  - smart chassis target sends BLE `WRITE_ONE`, waits for success, then updates local cache.
- [ ] Require known quantity for smart chassis because BLE record stores `uint16 qty`.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Existing scan inbound to a normal location still works.
- Inbound to a box slot creates one stock item.
- Inbound to fake smart chassis slot sends a fake `STOCK_IN` then fake `WRITE_ONE`.

---

## Phase 3: BLE Protocol Layer And Fake Client

### Task 3.1: Implement BLE v0.1 Byte Codec

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisProtocol.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SlotBindingRecord.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisAdvertisement.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisLightCommand.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/Crc8Maxim.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/Crc16.kt`

- [ ] Encode/decode 16-byte slot records exactly:
  - byte 0: slot `1..25`, `0` invalid/empty frame;
  - bytes 1..10: ASCII `part_id`, null padded;
  - bytes 11..12: `qty` little endian uint16;
  - byte 13: flags bitfield;
  - byte 14: reserved `0x00`;
  - byte 15: CRC-8/MAXIM over bytes 0..14.
- [ ] Reject invalid CRC, invalid slot range, non-ASCII part ids, overlong part ids, and quantity outside `0..65535`.
- [ ] Encode op frames: `READ_ONE`, `READ_ALL`, `WRITE_ONE`, `CLEAR_ONE`, `INSERT_AT`, `REMOVE_AT`, `MOVE_BLOCK`, `SET_QTY`, `FACTORY_RESET`.
- [ ] Decode notify responses as `op + status + payload` with status mapping `OK`, `ERR_PARAM`, `ERR_FULL`, `ERR_FLASH_BUSY`, `ERR_CRC`.
- [ ] Encode 17-byte light command: mode, mask_a, mask_b, RGB A, RGB B, timeout little endian.
- [ ] Parse advertisement manufacturer payload: company id, proto version, batch id, battery percent, status flags, table_seq low 16.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual byte inspection:

- Encode a `WRITE_ONE` record for slot 1, part `C17710`, qty 100 and verify total payload length is `1 + 16`.
- Encode `FIND` for slot 1 and verify light command length is exactly 17 bytes.

### Task 3.2: Add Smart Chassis Client Interface And Fake Implementation

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisClient.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/FakeSmartChassisClient.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisManager.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`

- [ ] Define client operations matching v0.1: scan, connect, disconnect, readTableInfo, readOne, readAll, writeOne, clearOne, insertAt, removeAt, moveBlock, setQty, sendLightCommand.
- [ ] Fake client stores 25 records in memory, increments `table_seq` after every successful write op, returns `ERR_FULL` when inserting into a full table, and simulates `ERR_FLASH_BUSY` for a configurable retry path.
- [ ] Manager exposes flows for discovered chassis, connection state, active table info, and last operation errors.
- [ ] AppContainer injects fake client behind a single switch while hardware GATT is not ready.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Fake `READ_ALL` returns 25 records.
- Fake `INSERT_AT` shifts later records and increments `table_seq`.
- Fake `SET_QTY` updates only quantity and increments `table_seq`.

### Task 3.3: Implement Android BLE Scanner And Advertisement Cache

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisScanner.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Use Android BLE scan APIs without touching printer SPP code.
- [ ] Filter scan response names matching `VBRK-XXXX` and parse manufacturer data with company id `0xFFFF` during development.
- [ ] Update or create `ContainerType.SMART_CHASSIS` rows from advertisement data: MAC, batch id, proto version, battery, status flags, table_seq low 16, `lastSeenAt`.
- [ ] If proto version is higher than app-supported `0x01`, expose upgrade-required state; if lower, expose firmware-upgrade state.
- [ ] Add `uses-feature android.hardware.bluetooth_le` as not required unless product decides BLE is mandatory for install.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- With fake data, chassis list can render battery and stale/fresh status.
- Printer screen still discovers P0 and connects through SPP.

### Task 3.4: Implement Android GATT Client After UUIDs Are Final

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisGattClient.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisUuids.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/ble/smart/SmartChassisManager.kt`

- [ ] Request MTU >= 64 after connection while keeping all single commands <= 17 bytes.
- [ ] Discover binding table service and light service by the documented UUIDs.
- [ ] Subscribe to Control Point notify and Table Info notify.
- [ ] Implement encrypted-link requirement for write ops; if Android reports unbonded/unencrypted state, initiate bonding or surface an action for pairing.
- [ ] Retry `ERR_FLASH_BUSY` with 100 ms backoff and a bounded retry count.
- [ ] After successful structured write op, refresh table info and either apply the equivalent local transform or run `READ_ALL`.
- [ ] Treat `table_seq` mismatch or CRC mismatch as stale cache and force `READ_ALL`.
- [ ] Disconnect cleanly after short find/update flows.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Hardware smoke when available:

- NFC wake or scan sees `VBRK-XXXX`.
- Connect discovers expected services/characteristics.
- `READ_ALL` returns 25 records and a valid table CRC.
- `FIND` lights the target slot and times out independently.

---

## Phase 4: NFC Device Routing

### Task 4.1: Parse `lcscerp://device` NFC URIs

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/nfc/NfcLabelPayload.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/core/nfc/NfcLabelManager.kt` only if scan result type needs richer payloads.
- Modify: `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`

- [ ] Add `NfcLabelKind.DEVICE`.
- [ ] Parse `lcscerp://device?mac=AA:BB:CC:DD:EE:FF&batch=1001&ver=1`.
- [ ] Validate MAC shape, batch uint16, proto version byte.
- [ ] On scan, navigate to the smart chassis digital twin route and start targeted connect/scan.
- [ ] Preserve existing `location` and `material` payload parsing.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Existing material NFC still opens box layer or inventory item.
- Device URI opens the chassis route with the expected MAC/batch.

---

## Phase 5: Digital Twin Read-Only View

### Task 5.1: Add Container/Chassis Feature Route

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersUiState.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersScreen.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/containers/SmartChassisTwinGrid.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/ui/LcscApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] Add a top-level route for containers or replace the current Boxes nav label with a broader container screen.
- [ ] Render container list: legacy locations, boxes, smart chassis, battery, freshness, slot usage count.
- [ ] For smart chassis detail, render a stable 5 x 5 grid with slot numbers 1..25.
- [ ] Slot states:
  - empty: neutral/gray;
  - quantity at or below safety threshold: red;
  - MSD flag or future wet-sensitive warning: orange;
  - stale hardware cache: outlined/warning state;
  - selected/active light: highlighted.
- [ ] Tapping occupied slot opens component summary and `FIND` action.
- [ ] Tapping empty slot offers inbound/stock-in action.
- [ ] Do not add drag-sort or heavy edit interaction in this first read-only slice.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Manual UI smoke:

- Existing boxes appear as containers.
- A fake smart chassis renders 25 fixed cells.
- Long part numbers/names do not resize the grid or overlap.

### Task 5.2: Add Find-By-Light From Search And Twin

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchUiState.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/containers/ContainersViewModel.kt`

- [ ] Add `canLight`/container metadata to search result rows.
- [ ] For smart chassis search hits, expose a `FIND` action using mask bit `slotNumber - 1`.
- [ ] After user adjusts stock from find flow, call smart `SET_QTY` when connected; fall back to local adjust only for non-smart containers.
- [ ] Surface connection/protocol errors without changing local quantity.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Search result in fake chassis sends one `FIND` command.
- Quantity edit after find sends fake `SET_QTY` and updates UI only after success.

---

## Phase 6: From-Hardware Restore

### Task 6.1: Add Restore Workflow In Settings

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/settings/HardwareRestoreUiState.kt`
- Create: `app/src/main/java/com/example/lcsc_android_erp/feature/settings/HardwareRestoreViewModel.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/data/repository/ContainerRepositoryImpl.kt`

- [ ] Add Settings entry: hardware restore / 从智能底盘恢复.
- [ ] Flow: wait for NFC device URI or scanned chassis -> connect -> `READ_ALL` -> validate each record CRC -> validate table CRC -> preview changes -> import.
- [ ] For `C...` parts, find existing component or fetch/enrich through `LcscCatalogRepository`.
- [ ] For `M...` parts, create placeholder component if not present.
- [ ] For empty/invalid slot records, clear corresponding local slot stock.
- [ ] Store `table_seq`, CRC, `lastSyncedAt`, and operation log entry.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Fake chassis with three records restores three slots.
- Re-running restore is idempotent.
- Invalid CRC blocks import and shows error.

---

## Phase 7: BOM Pick-To-Light

### Task 7.1: Build Pick Session Model

**Files:**
- Create: `app/src/main/java/com/example/lcsc_android_erp/domain/model/BomPickSession.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchUiState.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchViewModel.kt`

- [ ] Convert matched BOM rows into pick tasks with component id, required designators, matched stock slots, chassis id, slot number, and done state.
- [ ] Group tasks by smart chassis container.
- [ ] Generate `mask_a` per chassis from incomplete tasks.
- [ ] Keep BOM quantity as usage count, not inventory quantity.
- [ ] Rows without resolved stock remain unresolved and are not included in masks.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- A BOM with two components on the same fake chassis generates one mask with two bits.
- Checking one item clears only that bit and resends `PICK`.
- Completing the group sends `OFF`.

### Task 7.2: Add BOM Pick UI

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/example/lcsc_android_erp/feature/containers/SmartChassisTwinGrid.kt`

- [ ] Add “开始拣料” action when at least one matched row is on a smart chassis.
- [ ] Show grouped chassis sessions with progress and per-row done controls.
- [ ] Send `PICK` mode on session start and after each done toggle.
- [ ] Show current pick mask on the twin grid if the selected chassis is visible.
- [ ] Send `OFF` when a session is cancelled or completed.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Manual smoke:

- BOM match screen starts fake pick session.
- Twin grid highlights the same slots as the mask.
- Cancelling session turns fake lights off.

---

## Phase 8: Backup, Import, And Compatibility

### Task 8.1: Export Backup Schema v2

**Files:**
- Modify: `app/src/main/java/com/example/lcsc_android_erp/data/repository/InventoryBackupManager.kt`

- [ ] Keep current v1 import supported.
- [ ] Export `schemaVersion = 2`.
- [ ] Add sheets: `containers`, `container_slots`, `stock_items`, `stock_operations`.
- [ ] Continue exporting `components` with preview images.
- [ ] Include `protocolPartId`, `quantityState`, `safetyStockThreshold`, smart chassis MAC/batch/proto/table fields.
- [ ] For compatibility, either omit old sheets in v2 or keep old `storage_locations`/`inventory_items` sheets as derived compatibility sheets; choose one and document it in code comments.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Manual smoke:

- Export v2 workbook.
- Import v2 into a clean app DB.
- Import old v1 workbook still succeeds and migrates to containers.

---

## Phase 9: Polish And Release Gate

### Task 9.1: Permission And Error Copy Pass

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: screens touched above.

- [ ] Add clear copy for BLE scan/connect permission, unsupported protocol version, stale table cache, CRC mismatch, full chassis, flash busy retry failure, bonding required, and hardware restore summary.
- [ ] Keep printer permission copy separate from smart chassis copy.
- [ ] Avoid claiming physical truth; call quantities ledger/accounting quantities where appropriate.

**Verification:**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

### Task 9.2: End-To-End Verification Matrix

**Files:** none

- [ ] Compile: `.\gradlew.bat :app:compileDebugKotlin`
- [ ] Debug APK: `.\gradlew.bat :app:assembleDebug`
- [ ] Existing local tests if desired: `.\gradlew.bat :app:testDebugUnitTest`
- [ ] Manual existing flows:
  - scan/manual inbound to legacy location;
  - search existing part;
  - edit quantity/source URL;
  - create box and bind layer;
  - print P0 10 mm label;
  - read/write existing material NFC.
- [ ] Manual smart fake flows:
  - fake chassis appears;
  - twin grid renders 25 slots;
  - stock-in writes a slot;
  - find-by-light sends `FIND`;
  - restore reads 25 slots;
  - BOM pick session sends `PICK` and `OFF`.
- [ ] Hardware flows when chassis firmware is available:
  - advertisement parse;
  - NFC wake targeted connect;
  - `READ_ALL`;
  - `WRITE_ONE`;
  - `SET_QTY`;
  - `FIND`;
  - `PICK`;
  - table_seq/CRC stale-cache behavior.

---

## Recommended Execution Order

1. Phase 0: settle missing UUID constants and baseline compile.
2. Phase 1: migrate to unified containers while preserving old app behavior.
3. Phase 2: add operation semantics and make inbound slot-aware.
4. Phase 3.1-3.2: byte codec and fake smart chassis client.
5. Phase 5.1: digital twin read-only view on fake data.
6. Phase 4: NFC device route into the twin view.
7. Phase 6: hardware restore using fake client, then real GATT after UUIDs/firmware are ready.
8. Phase 3.3-3.4: real scanner/GATT client.
9. Phase 5.2 and Phase 7: find-by-light and BOM pick-to-light.
10. Phase 8-9: backup/import and release verification.

## Risks And Scope Boundaries

- Highest-risk change is the Room migration. Keep v7 additive and leave legacy tables in place until user data is verified.
- Byte protocol without tests is risky. If user authorizes tests later, add focused JVM tests for CRC-8/MAXIM, 16-byte record codec, light command length/masks, advertisement parse, and insert/remove/move transforms.
- Full sorting mode with LIS/minimal block operations belongs after v1 demo flows. This plan only establishes `MOVE_BLOCK`/`INSERT_AT` operation plumbing and leaves drag-sort UI for v1.5.
- Secure DFU entry is in firmware scope for v1 but should not block app-side inbound/find/restore/pick demo unless firmware update UX is explicitly requested.
- Shared take-out dock, cloud sync, conflict merging, and MSD timed management are outside this execution plan.
