<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description: 
 * @note: 
 * @version: 0.1.0
-->
# LCSC Android ERP

Native Android app for LCSC component inventory workflows. The project has evolved from a small offline-friendly warehouse tool into the app side of a smart component management system. It keeps the existing scanned inbound, manual inbound, location/box-layer management, inventory search, BOM matching, QR/NFC/Bluetooth label printing, and local backup/restore flows, while moving toward the VibeRack hardware workflow: scan inbound, tap NFC, find parts with lights, pick BOM rows, and maintain the stock ledger.

Current product and protocol direction is defined by:

- [Smart Component Management System Technical Document v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md): product positioning, architecture, hardware/firmware/app priorities, algorithms, and roadmap
- [Smart Chassis BLE Interface Specification v0.1](./docs/智能底盘BLE接口规格_v0.1.md): BLE/NFC advertising, GATT services, binding-table records, light commands, and security constraints

## Features

- Scan inbound: parse LCSC QR payloads and look up material data by `pc`
- Manual inbound: search LCSC catalog entries by keyword and confirm inbound
- Location / box-layer management: edit legacy locations plus component boxes and layers; the codebase already includes the first Boxes/BoxLayer data and UI slice
- Inventory management: inspect location items, edit quantity, transfer, and delete
- BOM search: import Excel BOM files, review matched / unmatched rows, and inbound directly
- Labels and printing: generate material/layer labels and use the verified P0/Yinlifang narrow-label print path
- NFC: read/write app-specific NFC payloads and plan the smart chassis device URI route
- Smart chassis direction: planned 25-slot BLE chassis, NFC wake-up, find-by-light, BOM pick-to-light, multi-slot binding table, and hardware restore
- Import / export: back up and restore inventory data with Excel files
- Localization: switch between Chinese and English

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

Core business data is persisted with `Room + SQLite`. Lightweight preferences and app settings are stored with `DataStore`.

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
app/src/main/java/com/example/lcsc_android_erp/
  core/       database, DataStore, network, i18n, shared UI
  data/       repositories, remote scraping, backup, image persistence
  domain/     business models and repository interfaces
  feature/    home / inbound / inventory / search / settings
  ui/         app shell and theme

docs/         planning and design notes
log/          exported device crash logs
app/schemas/  exported Room schemas
```

## Documentation

- [Smart Component Management System Technical Document v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)
- [Smart Chassis BLE Interface Specification v0.1](./docs/智能底盘BLE接口规格_v0.1.md)
- [P0 printing, NFC, and BOM integration record](./docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md)

## License

This project is licensed under the `GNU General Public License v3.0` (`GPLv3`).

- Full text: [LICENSE](./LICENSE)
- If you distribute modified versions, follow the GPLv3 requirements for source disclosure and same-license redistribution

## Notes

- The app is locked to portrait orientation
- Network access is used to query material information from LCSC
- Inventory, locations, cached images, and language preferences are stored locally
- The codebase is currently a single `app` module with package-based layering
- Future app work should align with the unified container model: a legacy location is a 1-slot container, a box is an N-layer container, and a smart chassis is a 25-slot BLE/NFC container
- `docs/superpowers/` only keeps still-useful P0 printing, NFC, and BOM integration notes; if they conflict with the two current design documents above, the current design documents win
