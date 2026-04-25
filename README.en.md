<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description: 
 * @note: 
 * @version: 0.1.0
-->
# LCSC Android ERP

![](docs/video16_9.jpeg)
【【开源】告别物料丢失烦恼 立创商城物料管理手机APP】 https://www.bilibili.com/video/BV1fL9JBHEpr

Native Android app for LCSC component inventory workflows. The project is built for small offline-friendly warehouse scenarios and covers scanned inbound, manual inbound, storage location management, inventory search, BOM matching, QR label export, and local backup/restore.

## Features

- Scan inbound: parse LCSC QR payloads and look up material data by `pc`
- Manual inbound: search LCSC catalog entries by keyword and confirm inbound
- Location management: edit location code, name, color, and item sort rules
- Inventory management: inspect location items, edit quantity, transfer, and delete
- BOM search: import Excel BOM files, review matched / unmatched rows, and inbound directly
- QR label export: preview a material QR label and save it to the system gallery
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

- [Technical plan](./docs/AIGC_project.md)
- [Popup inventory](./docs/AIGC_Popup.md)
- [Component inventory](./docs/AIGC_component.md)
- [Original requirement note](./docs/project.md)

## License

This project is licensed under the `GNU General Public License v3.0` (`GPLv3`).

- Full text: [LICENSE](./LICENSE)
- If you distribute modified versions, follow the GPLv3 requirements for source disclosure and same-license redistribution

## Notes

- The app is locked to portrait orientation
- Network access is used to query material information from LCSC
- Inventory, locations, cached images, and language preferences are stored locally
- The codebase is currently a single `app` module with package-based layering
