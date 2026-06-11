# Repository Guidelines

## Project Structure & Module Organization

This repository is a single-module Android app in `app/`. Kotlin source lives under `app/src/main/java/com/example/lcsc_android_erp`, organized by layer and feature:

- `feature/`: Compose screens and view models for `home`, `inbound`, `inventory`, `search`, and `settings`
- `core/`: app container, Room database, DataStore, network, and shared UI helpers
- `data/`: repository implementations, backup/export, image persistence, and remote data sources
- `domain/`: repository interfaces and app models
- `ui/`: app shell and theme

Resources are in `app/src/main/res`. Room schemas are exported to `app/schemas`. Design and planning notes live in `docs/`. Crash logs are stored in `log/`.

## Current Product Baseline

Treat these two documents as the current source of truth for product and protocol decisions:

- `docs/智能物料管理系统_项目技术文档_v1.0.md` — product positioning, VibeRack system architecture, hardware/firmware/app priorities, container model direction, algorithms, roadmap, and risks
- `docs/智能底盘BLE接口规格_v0.1.md` — BLE/NFC advertising, GATT services, slot-binding record layout, binding-table operation codes, light command format, pairing/security rules, and end-to-end hardware flows

When the remaining implementation record under `docs/superpowers/` disagrees with these two documents, follow the two current design documents unless the user explicitly says otherwise.

The active product direction is no longer only a generic offline warehouse ERP. It is the Android app for a smart component management system: scanned inbound, NFC wake-up, digital twin container/slot view, find-by-light, BOM pick-to-light, local/cloud stock ledger, label printing, and hardware restore from smart chassis binding tables.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repo root:

- `./gradlew :app:compileDebugKotlin` — fast compile check for Kotlin/Compose changes
- `./gradlew :app:assembleDebug` — build a debug APK
- `./gradlew :app:testDebugUnitTest` — run local unit tests
- `./gradlew :app:connectedDebugAndroidTest` — run instrumentation/UI tests on a device or emulator

For local Android Studio work, open the root project and run the `app` configuration.

## Coding Style & Naming Conventions

- Follow Kotlin defaults: 4-space indentation, no tabs, concise functions, and expression-style code when readable
- Keep feature UI in `feature/<name>/`, business/data code in `data/` or `domain/`
- Name screens and dialogs with `...Screen`, `...Dialog`, `...Route`
- Name view models `...ViewModel`, UI state models `...UiState`, and shared Compose cards `...Card`
- Reuse existing shared components such as `MaterialInboundDialog` and `core/ui/MaterialListCard` before adding new UI variants
- For new inventory modeling work, align with the unified container model in the v1.0 technical document: legacy storage locations are 1-slot containers, boxes are N-layer containers, and smart chassis are 25-slot BLE/NFC containers
- Keep BLE smart chassis protocol code byte-accurate with the v0.1 BLE interface spec. Do not invent alternate opcodes, record layouts, UUID semantics, or light command payloads without updating the protocol document
- Keep hardware as the binding-table source of truth for smart chassis flows. App-side cache updates should follow successful BLE operations and `table_seq`/CRC validation rather than independently re-numbering slots
- Preserve verified printer behavior: P0/Yinlifang discovery can use BLE scan, but printing connects through classic Bluetooth SPP/RFCOMM. Do not regress the existing 10 mm rotated box-layer label path when changing printer UI

## Testing Guidelines

Current test roots:

- `app/src/test/` for local JVM tests
- `app/src/androidTest/` for instrumentation tests

Prefer adding unit tests for parsers, sort rules, BOM matching, and repository logic. Name tests after the behavior being verified, for example `matches resistor BOM rows by value`.

## Commit & Pull Request Guidelines

Git history is not available in this workspace, so use a simple imperative style:

- `inventory: fix location detail image loading`
- `search: refresh BOM match after direct inbound`

PRs should include:

- a short summary of user-visible changes
- affected screens or flows
- screenshots for Compose UI changes
- verification notes, such as `./gradlew :app:compileDebugKotlin`

## Architecture Notes

This app uses Jetpack Compose + Room + DataStore + Retrofit/Jsoup. Preserve the existing flow: UI -> ViewModel -> Repository -> Room/network. Keep persisted schema changes compatible and update `app/schemas` when Room entities change.

Prefer incremental vertical slices that keep existing inbound, search, inventory, printer, NFC, and backup flows usable. For smart chassis work, implement the app-side BLE layer against mocks or simulators when hardware is unavailable, but keep packet formats and state transitions aligned with the BLE spec.
