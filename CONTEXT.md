# LCSC Android ERP / VibeRack

This context defines the domain language for the smart component management system. Use these terms when writing issues, PRDs, plans, tests, and implementation notes for this repo.

## Language

**VibeRack**:
An intelligent desktop component management system for makers, electronics labs, and small R&D teams. It combines the mobile app, smart chassis, passive bins, and stock ledger into workflows for finding, stocking, picking, and auditing components.
_Avoid_: LCSC Android ERP, warehouse ERP, rack system

**Component**:
An electronic part, module, or consumable that the user stores, finds, stocks, picks, or counts in VibeRack. A component can come from an LCSC part number or from a user-created custom record.
_Avoid_: Product, goods, inventory item, stock item

**Container**:
A physical storage unit that owns one or more slots where components can be placed. Legacy locations, storage boxes, and smart chassis are all containers.
_Avoid_: Location, shelf, box

**Slot**:
A numbered position inside a container that can hold at most one stock item. In a legacy location the single slot is implicit; in a storage box or smart chassis the slot is visible to the user.
_Avoid_: Layer, cell, position

**Stock Item**:
The ledger record that says a component is currently held in a specific container slot with a quantity and quantity state. It is placement-specific and should not be used as another name for the component itself.
_Avoid_: Component, inventory, material

**Quantity State**:
The trust level of a stock item's quantity, separate from the numeric quantity itself. A quantity of zero means zero; unknown or estimated quantities must be represented by quantity state.
_Avoid_: Zero-as-unknown, missing quantity, quantity flag

**Smart Chassis**:
A 25-slot container with embedded BLE, NFC, lighting, and persistent binding-table storage. It is the hardware source of truth for the slot-to-component bindings it owns.
_Avoid_: BLE device, light strip, smart box, rack

**Passive Bin**:
A removable, non-electronic bin that physically holds a component on a smart chassis slot. It has no identity, sensor, or storage of its own; the smart chassis owns the binding.
_Avoid_: Smart bin, electronic box, drawer, box

**Legacy LCSC App**:
The inherited community Android app used as the starting codebase for VibeRack. It is a reusable implementation base, not a compatibility contract; legacy flows may be removed or replaced when they conflict with the current hardware protocol or domain model.
_Avoid_: Official LCSC app, product baseline, compatibility target

**Protocol-First Development**:
The project rule that VibeRack app behavior follows the current smart-chassis hardware protocol and domain model before legacy app behavior. Existing code is kept only when it serves the protocol-first product direction.
_Avoid_: Legacy compatibility, backward compatibility, preserve old flows

**Storage Box**:
A legacy non-smart multi-slot container concept inherited from the Legacy LCSC App. It may be migrated, simplified, or removed if it does not serve the protocol-first VibeRack model.
_Avoid_: Core container type, smart chassis, passive bin

**Legacy Location**:
A single-slot storage-place concept inherited from the Legacy LCSC App. It can be treated as a degenerate container during migration, but it is not a product constraint for VibeRack.
_Avoid_: Primary location model, shelf, official location

**Binding Table**:
The persistent smart-chassis table that maps slots to minimal component identifiers and quantities. It is the hardware-owned source of truth for smart-chassis slot bindings, while the app stores richer component details and derived views.
_Avoid_: App database, cache, inventory table, slot list

**Protocol Part ID**:
The compact component identifier stored in a smart chassis binding table record. It is the recoverable key used to reconnect a hardware slot binding to app-side component details.
_Avoid_: Full part number, SKU, product code

**Hardware Restore**:
The recovery workflow that rebuilds app-side stock records by reading binding tables from one or more smart chassis. It restores minimal slot bindings first, then enriches component details from app or network data.
_Avoid_: Backup import, sync, cloud restore, database recovery

**Digital Twin View**:
The app view that mirrors a physical container and its slots so the user can inspect stock state and trigger physical guidance. It represents ledger-backed state, not guaranteed real-time physical truth.
_Avoid_: Dashboard, grid view, real-time sensor view

**Ledger Quantity**:
The quantity recorded by the app or smart chassis for a stock item. It is an accounting value that may drift from physical reality until the user or a counting device reconciles it.
_Avoid_: Physical quantity, actual count, sensor count

**Find-by-Light**:
The workflow where the app guides the user to a component by lighting the smart chassis slot that holds it. It starts from search, scan, or slot selection and ends when the user locates the physical bin.
_Avoid_: Light command, LED effect, pick-to-light

**Pick-to-Light**:
The workflow where VibeRack turns a BOM or pick list into lit smart chassis slots so the user can collect multiple required components. It is list-driven and may update lights as items are completed.
_Avoid_: Find-by-light, batch search, light effect

**Stock-In Guidance**:
The workflow where VibeRack recommends or confirms a destination slot for a component being stocked, then uses the smart chassis light to guide the user to place the passive bin there.
_Avoid_: Inbound, add stock, label printing

**Slot Reflow**:
A structured change to slot order or occupancy, such as inserting, removing, or moving a block of bindings. On a smart chassis, slot reflow is executed through hardware binding-table operations rather than independent app-side renumbering.
_Avoid_: Manual reorder, drag sort, slot renumbering

**Smart Dock**:
A shared counting and weighing device used to reconcile ledger quantities when components are taken from passive bins. It is a measurement aid, not a container or source of slot bindings.
_Avoid_: Container, smart chassis, per-bin sensor, scale

**Stock Reconciliation**:
The act of correcting or confirming a stock item's ledger quantity using user confirmation, counting, weighing, or another trusted observation.
_Avoid_: Sync, restore, import, quantity edit

**BOM Match**:
The process of linking BOM rows to known components and available stock items in VibeRack. It produces pick candidates but does not itself perform physical picking.
_Avoid_: Pick-to-light, BOM import, component search

**Safety Stock Threshold**:
The minimum acceptable ledger quantity for a stock item before VibeRack treats it as low stock. It is a warning threshold, not a measured quantity or reservation.
_Avoid_: Quantity, stock count, reorder quantity, physical minimum

**Label Printing**:
An auxiliary workflow for producing physical labels for components or non-smart storage. It must not define smart-chassis identity or replace hardware-owned bindings.
_Avoid_: Slot identity, binding source, smart chassis label

**Binding Table Version**:
The monotonic version of a smart chassis binding table, used to detect whether app-side cached slot bindings are stale. In the BLE protocol this is represented by `table_seq`.
_Avoid_: Sync timestamp, cache version, app revision

**Stock Ledger**:
The app-side accounting view of components, stock items, quantities, and stock operations. It supports search, BOM matching, digital twin views, and reconciliation, but smart chassis bindings are validated against hardware binding tables.
_Avoid_: Binding table, database dump, physical inventory

**Stock Operation**:
A recorded business action that changes or validates stock ledger state, such as stock-in, quantity reconciliation, slot reflow, hardware restore, or light-guided picking. It may reference a BLE operation, but it is not the BLE operation itself.
_Avoid_: BLE opcode, database log, audit text

**Smart Chassis Protocol**:
The versioned BLE/NFC contract between the VibeRack app and smart chassis hardware. It defines how devices are identified, how binding tables are read or changed, and how physical guidance lights are controlled.
_Avoid_: App API, firmware internals, Bluetooth wrapper

**Physical Guidance**:
The use of smart chassis lighting to guide a user toward a real-world slot or action. Find-by-light, pick-to-light, stock-in guidance, and slot reflow guidance are forms of physical guidance.
_Avoid_: LED animation, UI highlight, notification

**Smart Chassis Identity**:
The identifying information that lets VibeRack recognize and reconnect to a specific smart chassis across NFC, BLE scanning, and app records. It includes hardware identifiers such as MAC address and batch ID, but should be treated as the chassis identity rather than as a plain Bluetooth address.
_Avoid_: Bluetooth address, device name, label code

**NFC Wake-Up**:
The tap interaction that wakes or identifies a smart chassis and routes the app into the correct connection or digital twin flow. It is a smart-chassis interaction, not a generic label lookup.
_Avoid_: NFC label, location tag, material tag

**Low-Stock Signal**:
A derived warning that a stock item's ledger quantity is at or below its safety stock threshold. It may drive digital twin heatmaps, reminders, or light behavior, but it is not a direct sensor reading.
_Avoid_: Sensor alert, physical empty state, reorder command

**Smart Chassis Cache**:
The app-side copy of smart chassis identity, advertised state, binding table metadata, and slot bindings. It exists for fast UI rendering and must be refreshed or reconciled when hardware versions indicate it is stale.
_Avoid_: Binding table, source of truth, local authority

**Custom Component**:
A user-created component record that is not resolved from an LCSC catalog part. It can still be stored, bound to slots, counted, picked, and restored through a protocol part ID.
_Avoid_: Manual item, temporary item, unknown part

**Catalog Enrichment**:
The process of filling a component record with richer catalog data such as name, package, brand, specifications, images, or source links. It enriches component details but does not define slot binding or ledger quantity.
_Avoid_: Binding, restore, stock import, component identity

**Catalog Component**:
A component record resolved from an LCSC catalog identifier or catalog data. It may have richer metadata than a custom component, but it follows the same storage, binding, picking, and reconciliation rules.
_Avoid_: Official component, product, SKU

**Community Fork**:
The inherited, unofficial LCSC-oriented Android app lineage that VibeRack builds from. It may reuse catalog-oriented workflows, but it must not imply official LCSC support or constrain protocol-first product decisions.
_Avoid_: Official LCSC support, vendor app, certified integration

**Smart Chassis Slot Binding**:
The hardware-owned association between one smart chassis slot, one protocol part ID, and a ledger quantity. It is stored as a binding table record and is the minimal unit restored from hardware.
_Avoid_: App placement, label assignment, UI slot row

**Binding Drift**:
A mismatch between app-side smart chassis cache or stock ledger state and the hardware binding table. Binding drift is resolved by checking binding table version, CRC, and hardware reads rather than trusting app-side state alone.
_Avoid_: Sync error, UI stale state, quantity drift

**Quantity Drift**:
A mismatch between a stock item's ledger quantity and the real physical count. It is expected in workflows without continuous sensing and is reduced through stock reconciliation.
_Avoid_: Binding drift, app bug, sensor state

**Hardware-Owned Binding**:
The principle that smart chassis slot bindings are owned by the smart chassis hardware, not by the app cache. App-side records follow successful protocol operations or hardware restore reads.
_Avoid_: App-owned binding, local override, client-side renumbering

**Component Placement**:
The relationship that a component is placed in a specific container slot. A placement may have an associated stock item and ledger quantity, but the placement is about where the component lives.
_Avoid_: Quantity, stock operation, label

**Rewrite-Friendly App**:
The current development posture that the Android app may be substantially rebuilt when that is the fastest way to reach the protocol-first VibeRack product. Existing implementation is useful only while it accelerates the current hardware-backed workflow.
_Avoid_: Stable legacy app, compatibility surface, preservation target

**MVP Scope**:
The smallest VibeRack app experience that proves the smart chassis workflow: identify a chassis, bind components to slots, show the digital twin view, guide stock-in and find-by-light, and recover bindings from hardware.
_Avoid_: Full ERP, legacy feature parity, all-in-one inventory app

**Chassis Onboarding**:
The workflow that introduces a smart chassis to the app by reading its identity, creating or updating its container record, and preparing it for binding-table operations.
_Avoid_: Pairing, device setup, Bluetooth connection

**Slot Binding Workflow**:
The user workflow for assigning a component and ledger quantity to a smart chassis slot through the smart chassis protocol. It should confirm the physical slot and update app-side records only after the hardware binding succeeds.
_Avoid_: Inbound, add inventory, local slot edit

**Chassis Simulator**:
A development or test substitute for smart chassis hardware that follows the smart chassis protocol closely enough to exercise app workflows before physical boards are available.
_Avoid_: Mock data, demo mode, fake inventory

**Fast Development Phase**:
The current project phase where speed to a working smart-chassis workflow matters more than preserving legacy app structure or feature parity. Decisions should favor protocol validation, clear domain shape, and removable code.
_Avoid_: Stabilization phase, maintenance mode, compatibility phase

**Legacy Feature Parity**:
A non-goal for VibeRack during the fast development phase. The app does not need to preserve every inherited LCSC-oriented workflow when those workflows do not advance the smart-chassis MVP.
_Avoid_: Migration requirement, acceptance baseline, compatibility promise
