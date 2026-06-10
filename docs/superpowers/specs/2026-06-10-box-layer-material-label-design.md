# Box Layer Material Label Design

## Purpose

This app should become a printer-first tool for managing LCSC components stored in magnetic stackable boxes.

The hardware model is:

- A user owns many small magnetic boxes.
- Each box has many layers.
- Boxes can be freely stacked and moved, so the physical order is not fixed.
- A label is attached to the outside of a box layer.
- One layer holds one LCSC component.
- Each component-layer pair can print one or more labels.

The app is not primarily a generic warehouse ERP. It should help the user assign LCSC parts to box layers and print labels for those layers.

## Confirmed Decisions

- Label granularity: one label represents one component in one box layer.
- Label placement: labels are attached to the outside of the box layer.
- Layer occupancy: one layer holds only one component.
- Print quantity: the user chooses how many labels to print for a component-layer pair.
- Default print quantity: 1 label.
- Position format: box code plus layer code, for example `BOX01-L01`.
- Box creation: the user enters a box code and layer count.
- Layer generation: the app automatically generates layers such as `L01` through `L20`.
- Layer editing: generated layers can be manually renamed or edited after creation.

## Current Logic Mismatch

The current code models the system as warehouse inventory:

- `storage_location` is treated as a warehouse/location code like `A1` or `C3`.
- One location can contain many different inventory items.
- The location label is a summary label with location code, material count, and total quantity.
- Material labels are available mainly from inbound/search flows.
- Printer connection is implemented, but the printer screen is hidden from bottom navigation.
- NFC supports location labels and material payload parsing, but the exposed workflow mainly writes location NFC tags.
- BOM search currently matches local inventory instead of acting as an LCSC catalog/list of components to assign and print.

For the target product, the core object should be a box layer, not a generic warehouse location.

## Recommended Domain Model

Add explicit box and layer concepts instead of stretching `storage_location` indefinitely.

```text
Box
- id
- code: BOX01
- name
- layerCount
- createdAt
- updatedAt

BoxLayer
- id
- boxId
- layerCode: L01
- displayName
- sortOrder
- createdAt
- updatedAt

LayerMaterial
- id
- layerId
- componentId
- quantity
- sourceType
- rawPayload
- createdAt
- updatedAt
```

Rules:

- One `Box` has many `BoxLayer` rows.
- One `BoxLayer` can bind at most one `Component`.
- One `Component` can appear in multiple `BoxLayer` rows.
- `LayerMaterial.quantity` is inventory quantity, not print quantity.
- Print quantity is temporary UI state for a print task and should default to 1.

## Label Content

A component label should show the physical position and the component identity.

Recommended visible fields:

```text
BOX01-L03
C17710
0805W8F4700T5E
470Ω ±1% 0805
Qty: 100
```

The exact secondary line should use available component fields in priority order:

1. MPN or component name.
2. Important specifications such as resistance/capacitance, tolerance, power.
3. Package.
4. Brand if space allows.

## NFC / QR Payload

The label payload should point back to the exact layer and component.

Recommended URI:

```text
lcscerp://material?part=C17710&box=BOX01&layer=L03
```

The current payload has `part` and optional `location`. It should evolve to explicit `box` and `layer` fields.

Scanning a tag or QR code should open the bound layer and component detail.

## Core Flows

### Create Box

1. User taps create box.
2. User enters box code, for example `BOX01`.
3. User enters layer count, for example `20`.
4. App creates `Box`.
5. App creates `BoxLayer` rows from `L01` to `L20`.
6. User can edit layer names or display labels later.

### Assign Component To Layer

1. User opens a box and layer.
2. User scans an LCSC QR code, searches LCSC, or imports from BOM.
3. App fetches component details from LCSC.
4. User confirms quantity.
5. App binds the component to the selected layer.
6. If the layer already has a component, the app should ask whether to replace it.

### Print Component Layer Label

1. User opens a layer with a bound component.
2. App previews the label.
3. Print quantity defaults to `1`.
4. User can change print quantity.
5. App sends that many print jobs to the selected printer.

### Print Empty Layer Label

1. User opens a generated empty layer.
2. App can print a position-only label such as `BOX01-L03`.
3. This is useful when preparing a new box before assigning components.

### BOM / Catalog Flow

BOM should act as a list of parts to look up in the LCSC catalog, not as a local inventory search.

Recommended behavior:

1. Import BOM.
2. Parse LCSC part numbers or manufacturer parts.
3. Query LCSC component details.
4. Let the user assign each component to an empty box layer.
5. Print labels for assigned components.

Local inventory matching can remain a secondary warning, not the main BOM result.

## UI Direction

The main navigation should prioritize printing and box-layer management.

Recommended top-level areas:

1. Boxes
2. Print
3. Inbound / Add Component
4. Search / Catalog
5. Settings

Printer setup should be reachable directly because the app's primary purpose is operating the printer.

Terminology should shift away from warehouse language:

- `仓库` -> `盒子`
- `库位` -> `盒子层位`
- `仓库标签` -> `层位标签`
- `物料标签` -> `部件层标签`

## Implementation Priority

1. Restore printer as a visible primary navigation item.
2. Introduce box and layer UI language.
3. Add box creation with automatic layer generation.
4. Enforce one component per layer.
5. Move component label printing into the layer detail flow.
6. Add custom print quantity per print action.
7. Update NFC/QR payload to use `box` and `layer`.
8. Rework BOM flow into LCSC catalog lookup and layer assignment.
9. Update export/import to preserve boxes, layers, bindings, and component details.

## Migration Notes

Existing `storage_location` data may be migrated as box layers later.

Possible mapping:

- Existing `storage_location.code` becomes `BoxLayer.displayName` or a generated layer code.
- If a code already looks like `BOX01-L03`, split it into `Box.code = BOX01` and `BoxLayer.layerCode = L03`.
- Otherwise, group legacy locations into a default box such as `BOX-LEGACY`.

Because the current model allows multiple components in one location, migration must handle conflicts:

- If a legacy location has one component, map it directly.
- If a legacy location has multiple components, create multiple layers or ask the user to split them.

## Open Implementation Questions

- Exact printed label dimensions for the current hardware.
- Whether the label should include QR, NFC-only metadata, or both.
- Whether replacing a layer's component should archive the old binding or delete it.
- Whether a box layer can temporarily have zero quantity while still bound to a component.
- Whether print history should be recorded.

These questions do not block documenting the product direction, but they should be answered before implementation.
