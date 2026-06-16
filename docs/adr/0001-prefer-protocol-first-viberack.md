# Prefer Protocol-First VibeRack Behavior

## Status

Accepted.

## Context

VibeRack is in a fast development phase and is built from an inherited unofficial LCSC-oriented Android app. That codebase is a reusable starting point, not a compatibility target. The current product direction is defined by `CONTEXT.md`, `docs/智能物料管理系统_项目技术文档_v1.0.md`, and `docs/智能底盘BLE接口规格_v0.1.md`.

The app must prove the smart chassis workflow before preserving legacy feature shape: identify a chassis, bind components to slots, show the digital twin view, guide stock-in and find-by-light, support BOM pick-to-light, and recover bindings from hardware.

## Decision

VibeRack behavior follows the current smart chassis hardware protocol and domain model before legacy app behavior. When legacy locations, storage boxes, labels, NFC flows, or inventory behavior conflict with the current smart chassis protocol and VibeRack domain model, we will migrate, replace, or delete the legacy behavior instead of preserving compatibility.

Project identity should also follow this decision. App package, application id, visible app name, Room database filename, docs, and agent-facing paths should use VibeRack naming. LCSC naming remains valid only where it specifically means the LCSC catalog/source, QR label parsing, supplier part identifiers, or documented legacy protocol URI compatibility.

## Consequences

- Legacy location and box flows may stay only while they serve the unified container model or migration path.
- Smart chassis binding-table operations are hardware-owned; app cache and stock ledger updates follow successful BLE operations or validated hardware reads.
- Compatibility mirrors such as `inventory_item` and `inventory_txn` are technical debt, not product baseline.
- Future changes should not introduce new LCSC-branded product identity unless they refer to catalog integration or inherited lineage.
