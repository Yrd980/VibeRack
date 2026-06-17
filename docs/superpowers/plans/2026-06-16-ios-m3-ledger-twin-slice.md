# iOS M3 Ledger Twin Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first simulator-verifiable M3 slice: GRDB-backed local ledger schema, a seeded smart chassis repository, and a 25-slot SwiftUI digital twin fed by repository data.

**Architecture:** Add focused domain value models, a GRDB database factory/migrator, and a `ChassisRepository` protocol with a GRDB implementation plus simulator seed. `AppView` owns a `DependencyGraph` and injects the repository into `ChassisListView` / `ChassisDetailView`; UI remains SwiftUI-native and does not depend on BLE hardware.

**Tech Stack:** Swift 5, SwiftUI Observation, GRDB 7.11, XCTest, iOS 26 simulator.

---

### Task 1: Ledger Schema And Repository

**Files:**
- Create: `ios/VibeRack/Domain/Models/ChassisModels.swift`
- Create: `ios/VibeRack/Domain/Repositories/ChassisRepository.swift`
- Create: `ios/VibeRack/Core/Persistence/DatabaseFactory.swift`
- Create: `ios/VibeRack/Core/Persistence/GRDBChassisRepository.swift`
- Test: `ios/VibeRackTests/PersistenceTests/ChassisRepositoryTests.swift`

- [ ] Write failing XCTest for simulator seed returning one smart chassis with 25 slots and first-slot stock.
- [ ] Run `xcodebuild test ... -only-testing:VibeRackTests/ChassisRepositoryTests` and verify compile/test failure.
- [ ] Implement minimal domain models, GRDB migrator, and repository methods.
- [ ] Run the targeted test and verify it passes.

### Task 2: Digital Twin UI Wiring

**Files:**
- Modify: `ios/VibeRack/App/AppView.swift`
- Modify: `ios/VibeRack/App/AppTab.swift`
- Modify: `ios/VibeRack/App/Router.swift`
- Modify: `ios/VibeRack/Features/Chassis/ChassisListView.swift`
- Create: `ios/VibeRack/App/DependencyGraph.swift`

- [ ] Replace hard-coded demo slot data with injected `ChassisRepository`.
- [ ] Keep list/detail navigation in the existing per-tab `NavigationStack`.
- [ ] Render a stable 5x5 grid from repository slot state with slot number, part id, quantity, and empty state.
- [ ] Run `xcodebuild test` and `xcodebuild build` for iPhone Air simulator.

### Task 3: Commit And Push

**Files:**
- Modify: Xcode project file for all new source/test files.

- [ ] Stage only M3 slice files.
- [ ] Commit with `ios: add simulator ledger twin`.
- [ ] Push `codex/ios`.

