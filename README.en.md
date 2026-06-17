<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description:
 * @note:
 * @version: 0.1.0
-->
# VibeRack

VibeRack is an intelligent desktop component management system for makers, electronics labs, and small R&D teams. This is the current canonical repository for the Android app, iOS app, shared domain documentation, and smart chassis protocol documentation.

This is no longer a legacy LCSC/ERP compatibility project. Old code is only an implementation source; when legacy locations, box layers, labels, NFC flows, or inventory behavior conflict with the current smart chassis protocol and VibeRack domain model, the protocol-first product direction wins.

Current product and protocol direction is defined by:

- [Smart Component Management System Technical Document v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md): product positioning, VibeRack architecture, hardware/firmware/app priorities, unified container model, algorithms, roadmap, and risks
- [Smart Chassis BLE Interface Specification v0.1](./docs/智能底盘BLE接口规格_v0.1.md): BLE/NFC advertising, GATT services, slot binding-table records, light commands, security rules, and end-to-end hardware flows

## Current Features

- Android main navigation: Home / Containers / Search / Settings
- Android smart chassis flows: BLE scan, NFC device URI routing, 25-slot digital twin, find-by-light, STOCK_IN guidance, READ_ALL restore preview
- Android search: stock search, BOM matching, smart chassis find-by-light, BOM pick-to-light, and binding search results into empty smart chassis slots
- Unified container and slot model: a normal location is a 1-slot container, and a smart chassis is a 25-slot BLE/NFC container
- iOS project: `ios/VibeRack.xcodeproj`, with SwiftUI app, BLE/protocol layer, GRDB stock ledger, digital twin, stock-in, search, BOM, settings, and XCTest
- BOM pick-to-light: import Excel BOM files, match local stock ledger records, and light the smart chassis slots that should be picked
- Hardware restore: read binding tables from one or more smart chassis, restore minimal slot bindings first, then enrich component details from app or catalog data

## Protocol-First Rules

- Smart chassis slot bindings are owned by the hardware binding table; the app-side cache is not the binding source of truth
- App-side `stock_item`, `stock_operation`, `container.tableSeq`, and `container.tableCrc16` must follow successful BLE operations or `table_seq`/CRC-validated table reads
- Do not independently renumber smart chassis slots in the app. Insert, remove, and move operations should execute through hardware binding-table operations
- Catalog enrichment fills component details; it does not define slot binding or ledger quantity
- Label printing is auxiliary and must not replace smart chassis identity or hardware-owned bindings

## Tech Stack

- Android: Kotlin 2.2, AGP 9.2, Jetpack Compose + Material 3, Room + SQLite, DataStore, Retrofit/OkHttp/Jsoup, CameraX, ML Kit, Coil, ZXing, Apache POI, BLE/NFC
- iOS: SwiftUI, CoreBluetooth, CoreNFC, GRDB/SQLite, XCTest
- Shared rules: protocol and domain semantics follow `CONTEXT.md` and the current product/protocol docs under `docs/`

Core business data is persisted with `Room + SQLite`. Lightweight preferences and app settings are stored with `DataStore`. The smart chassis protocol layer includes BLE scanning, GATT client behavior, protocol codecs, light commands, table restore, and a fake chassis test substitute.

## Requirements

- Latest stable Android Studio
- JDK 17
- Android SDK 36
- Android 11+ device or emulator
- Xcode for iOS work

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

To run locally, open the repository root in Android Studio and launch the `app` configuration.

## Project Layout

```text
app/          Android app module: Compose UI, Room, BLE/NFC, data layer
domain/       Android shared domain module: models and repository interfaces
ios/          iOS SwiftUI project and XCTest
assets/       sample assets, including BOM xlsx files
docs/         product, protocol, iOS plans, hardware integration, and agent docs
app/schemas/  exported Android Room schemas
```

## Documentation

- [CONTEXT.md](./CONTEXT.md): current VibeRack domain language and terminology boundaries
- [Smart Component Management System Technical Document v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)
- [Smart Chassis BLE Interface Specification v0.1](./docs/智能底盘BLE接口规格_v0.1.md)
- [VibeRack iOS Technical Document v0.1](./docs/VibeRack_iOS版本开发技术文档_v0.1.md)
- [VibeRack iOS Execution Plan v0.2](./docs/VibeRack_iOS开发执行计划_v0.2.md)

If older notes under `docs/superpowers/` conflict with `CONTEXT.md` or the two current design documents, follow the current design documents and smart chassis BLE specification.

## License

This project is licensed under the `GNU General Public License v3.0` (`GPLv3`).

- Full text: [LICENSE](./LICENSE)
- If you distribute modified versions, follow the GPLv3 requirements for source disclosure and same-license redistribution

## Notes

- The app is locked to portrait orientation
- Network access is used for catalog component lookup and later catalog enrichment
- Stock ledger, container cache, smart chassis cache, cached images, and language preferences are stored locally
- Android currently uses two Gradle modules: `:app` and `:domain`
- iOS lives under `ios/` and is verified on macOS/Xcode
- The fast development phase does not target legacy LCSC feature parity. The priority is proving the smart chassis MVP: identify a chassis, bind components to slots, show the digital twin view, guide stock-in, find by light, and restore bindings from hardware
