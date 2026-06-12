# Local App Closure Design

## Scope

Implement the app-side flows that do not require physical smart chassis joint testing or a real cloud service:

- smart-slot inbound through the container screen
- a unified local operation semantics layer for container slots
- hardware restore preview before local import
- operation log coverage for local stock changes

Out of scope:

- proving BLE behavior against real hardware
- real cloud sync, multi-user conflict merge, auth, or server APIs

## Contracts

- `container`, `container_slot`, `stock_item`, and `stock_operation` remain the source tables.
- Legacy storage locations stay as 1-slot containers.
- Boxes stay backed by `component_box`/`box_layer` compatibility rows plus `container`/`container_slot` stock.
- Smart chassis writes only update local stock after a successful app-side operation result. Without hardware, local preview/import and fake-client-compatible paths must be complete.
- Hardware restore uses READ_ALL snapshot data to build a preview first; confirmation applies local stock, table metadata, and an operation log entry.

## Implementation Shape

Add a product-level slot operation repository above the raw DAOs. It exposes write, clear, insert, remove, move, and quantity operations using `StockContainer`, `ContainerSlot`, and `StockOperation`.

Extend the container screen with two local app workflows:

- selected smart slot inbound: user enters a part ID/number and quantity, then the app resolves or creates a component and writes that slot through the operation layer
- restore preview: READ_ALL produces a preview summary; user confirms before local import

The existing inbound screen remains compatible and continues writing legacy 1-slot containers through `InventoryRepository`.

## Verification

Minimum verification for this slice is `.\gradlew.bat :app:compileDebugKotlin --no-daemon`. Hardware behavior remains explicitly unverified until device joint testing.
