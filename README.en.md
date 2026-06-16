<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description:
 * @note:
 * @version: 0.1.0
-->
# VibeRack

VibeRack is a native Android app for intelligent desktop component management, aimed at makers, electronics labs, and small R&D teams. It combines the mobile app, smart chassis, passive bins, and stock ledger into workflows for component stock-in, NFC wake-up, digital twin slots, find-by-light, BOM pick-to-light, stock accounting, and hardware restore.

This repository is inherited from an unofficial LCSC-oriented Android app. The legacy app is a reusable implementation base, not a compatibility contract. When legacy locations, box layers, labels, NFC flows, or inventory behavior conflict with the current smart chassis protocol and VibeRack domain model, the protocol-first product direction wins.

Current product and protocol direction is defined by:

- [Smart Component Management System Technical Document v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md): product positioning, VibeRack architecture, hardware/firmware/app priorities, unified container model, algorithms, roadmap, and risks
- [Smart Chassis BLE Interface Specification v0.1](./docs/智能底盘BLE接口规格_v0.1.md): BLE/NFC advertising, GATT services, slot binding-table records, light commands, security rules, and end-to-end hardware flows

## Features

- Component stock-in: parse LCSC QR labels or search catalog components, then capture recoverable component identity, quantity, and catalog details
- Smart chassis stock-in guidance: choose a chassis and slot, light `STOCK_IN`, confirm placement, write the hardware binding table with BLE `WRITE_ONE`, then update the app-side stock ledger
- Unified container and slot model: a legacy location is a 1-slot container, a storage box is an N-slot container, and a smart chassis is a 25-slot BLE/NFC container
- Digital twin view: inspect the 25 smart chassis slots, occupancy, low-stock signals, active lights, `table_seq`, CRC, and cache freshness
- Find-by-light: trigger physical guidance from search, scan, or slot selection
- BOM pick-to-light: import Excel BOM files, match local stock ledger records, and light the smart chassis slots that should be picked
- Hardware restore: read binding tables from one or more smart chassis, restore minimal slot bindings first, then enrich component details from app or catalog data
- Stock ledger: record components, container slots, stock items, quantity state, and stock operations for search, BOM matching, counting, and reconciliation
- Labels and printing: keep the verified P0/Yinlifang 10 mm rotated narrow-label print path as an auxiliary workflow for non-smart storage and physical labels
- NFC: read/write app NFC payloads and route smart chassis device URIs into wake-up, identity, and restore flows
- Import/export: Excel stock ledger backup and restore
- Localization: switch between Chinese and English

## Protocol-First Rules

- Smart chassis slot bindings are owned by the hardware binding table; the app-side cache is not the binding source of truth
- App-side `stock_item`, `stock_operation`, `container.tableSeq`, and `container.tableCrc16` must follow successful BLE operations or `table_seq`/CRC-validated table reads
- Do not independently renumber smart chassis slots in the app. Insert, remove, and move operations should execute through hardware binding-table operations
- Catalog enrichment fills component details; it does not define slot binding or ledger quantity
- Label printing is auxiliary and must not replace smart chassis identity or hardware-owned bindings

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- MVVM + Repository
- Room + SQLite
- DataStore
- Retrofit + OkHttp + Jsoup
- CameraX + ML Kit Barcode Scanning
- Coil
- ZXing
- Apache POI
- Android BLE / NFC / classic Bluetooth SPP

Core business data is persisted with `Room + SQLite`. Lightweight preferences and app settings are stored with `DataStore`. The smart chassis protocol layer includes BLE scanning, GATT client behavior, protocol codecs, light commands, table restore, and a fake chassis test substitute.

## Requirements

- Latest stable Android Studio
- JDK 11
- Android SDK 36
- Android 11+ device or emulator

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

To run locally, open the repository root in Android Studio and launch the `app` configuration.

## Project Layout

```text
app/src/main/java/com/viberack/app/
  core/       database, DataStore, network, BLE/NFC, i18n, shared UI
  data/       repositories, remote scraping, backup, image persistence, protocol application services
  domain/     business models and repository interfaces
  feature/    home / inbound / inventory / search / containers / boxes / settings
  ui/         app shell and theme

docs/         product, protocol, planning, and agent documentation
hardware/     hardware-related materials
log/          exported device crash logs
app/schemas/  exported Room schemas
```

## Documentation

- [CONTEXT.md](./CONTEXT.md): current VibeRack domain language and terminology boundaries
- [Smart Component Management System Technical Document v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)
- [Smart Chassis BLE Interface Specification v0.1](./docs/智能底盘BLE接口规格_v0.1.md)
- [P0 printing, NFC, and BOM integration record](./docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md)

If older notes under `docs/superpowers/` conflict with `CONTEXT.md` or the two current design documents, follow the current design documents and smart chassis BLE specification.

## License

This project is licensed under the `GNU General Public License v3.0` (`GPLv3`).

- Full text: [LICENSE](./LICENSE)
- If you distribute modified versions, follow the GPLv3 requirements for source disclosure and same-license redistribution

## Notes

- The app is locked to portrait orientation
- Network access is used for catalog component lookup and later catalog enrichment
- Stock ledger, container cache, smart chassis cache, cached images, and language preferences are stored locally
- The codebase is currently a single `app` module with package-based layering
- The fast development phase does not target legacy LCSC feature parity. The priority is proving the smart chassis MVP: identify a chassis, bind components to slots, show the digital twin view, guide stock-in, find by light, and restore bindings from hardware
