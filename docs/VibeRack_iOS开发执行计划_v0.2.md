# VibeRack iOS 开发执行计划 v0.2

> 状态：开发阶段技术决策稿  
> 日期：2026-06-16  
> 上游事实源：`CONTEXT.md`、`docs/智能物料管理系统_项目技术文档_v1.0.md`、`docs/智能底盘BLE接口规格_v0.1.md`  
> Android 源码基线：`app/src/main/java/com/viberack/app`  
> 硬件/固件基线：`/Users/wq/PartRack-Hardware`  
> 本机验证环境：Xcode 26.1.1, iOS 26.1 Simulator, `iPhone Air` device type  
> 目标：直接开工构建 VibeRack iOS 版，不复刻旧 Android ERP 外形，优先证明智能底盘 MVP。

---

## 1. 结论先行

iOS 版应按 VibeRack 当前产品方向重建，而不是逐屏复制 Android 旧版。Android 仓库在 iOS 开发中只作为需求、领域规则、协议实现和既有算法参考，不作为界面、导航或交互的强制模板。Android 代码中最值得迁移的是协议、领域规则、BOM 匹配、入库解析和统一容器账本；不应迁移的是旧 `Inventory`/`Boxes`/`Printer` 作为主导航的产品形态。

开发阶段推荐技术配置：

| 方向 | 决策 |
|---|---|
| UI | SwiftUI + Apple 原生控件；`TabView`、`NavigationStack`、`List`、`Form`、`Sheet`、`Toolbar`、SF Symbols；iOS 26 可用处采用原生 Liquid Glass API，不做 Android Material 复刻 |
| 模拟器目标 | `iPhone Air`，iOS 26.1；用户口头的“iPhone17 air”在本机 Xcode 中实际设备名为 `iPhone Air` |
| 真机目标 | iPhone 真机，用于 CoreBluetooth、CoreNFC、相机扫描；模拟器只验 UI、数据流、协议单测 |
| 本地账本 | GRDB/SQLite 作为主存储；直接建 `component/container/container_slot/stock_item/stock_operation`，legacy 表只做导入兼容 |
| 协议层 | 纯 Swift 值类型 + XCTest；先移植硬件仓库 `protocol/test-vectors.json`，再移植 Android `SmartChassisCodec` 测试向量 |
| BLE | CoreBluetooth；前台操作优先，不把后台 BLE 作为正确性前提 |
| NFC | CoreNFC `NFCNDEFReaderSession`；只读 NDEF URI 后路由，数据仍走 BLE |
| 打印 | iOS MVP 不做；Android P0/Q5 走经典蓝牙 SPP/RFCOMM，iOS 通用 App 不能按这个方式直接迁移 |
| 固件协同 | 以 `/Users/wq/PartRack-Hardware/protocol/test-vectors.json` 和 `docs/verification-matrix.md` 为跨端协议与验证证据基线 |
| 开发顺序 | 协议单测 -> iOS 工程骨架 -> BLE 扫描/连接/Device Health -> 本地账本 -> 数字孪生 -> 入库/找料 -> 硬件恢复 -> BOM Pick-to-Light |
| SPM 锁文件 | App 工程提交 `ios/VibeRack.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`；仓库继续忽略其他位置的 `Package.resolved` |

---

## 2. 已验证事实

### 2.1 本机工具链验证

已运行：

```bash
xcodebuild -version
xcrun simctl list devicetypes | rg -i "iPhone.*(17|Air)"
xcrun simctl list runtimes | rg -i "iOS|available"
xcrun simctl list devices available | rg -i "iPhone 17|iPhone Air|Air"
```

结果：

- Xcode: `26.1.1 (17B100)`
- 可用 runtime: `iOS 26.1 (23B86)`
- 可用模拟器：`iPhone Air`、`iPhone 17`、`iPhone 17 Pro`、`iPhone 17 Pro Max`
- 本计划的模拟器验收目标写作 `iPhone Air (iOS 26.1)`。

### 2.2 Apple 平台资料核对

已查 Apple 官方资料：

- Core Bluetooth 后台能力不是无限后台运行；后台扫描会合并发现事件、降低扫描频率，App 被唤醒后应快速处理。来源：Apple Core Bluetooth Programming Guide。
- Core Bluetooth 支持 `bluetooth-central` background mode 和 state restoration，但本项目 MVP 不应依赖后台行为完成找料、入库、恢复或拣料。
- Core NFC `NFCNDEFReaderSession` 适合当前 NDEF URI 路由设计。
- ExternalAccessory 是 iOS 与外部配件通信框架，但通用经典蓝牙 SPP/RFCOMM 打印不能按 Android 实现直接迁移；P0/Yinlifang 打印需另查厂商 SDK、MFi、BLE GATT 打印协议或 AirPrint 路线。

### 2.3 Android 源码事实

Android 当前入口是 `MainActivity.kt` -> `ui/VibeRackApp.kt`。`NavHost` 有 8 个 route，但底部只显示 6 个 Tab：

- 显示：`home`、`containers`、`inbound`、`search`、`printer`、`settings`
- 隐藏 route：`boxes`、`inventory`

真正贴近 VibeRack MVP 的屏幕是 `containers`、`inbound`、`search` 和 `settings` 中的硬件恢复入口。`home` 是旧汇总页，`boxes`/`inventory` 是 legacy storage/legacy location 线，`printer` 已被产品文档降权。

Android 已实现的可迁移资产：

- BLE 协议常量：`core/ble/smart/SmartChassisProtocol.kt`
- 广播、Slot Record、CRC、Light Command 编解码：`core/ble/smart/SmartChassisCodec.kt`
- 协议单测：`src/test/java/com/viberack/app/core/ble/smart/SmartChassisCodecTest.kt`
- 智能底盘操作封装：`core/ble/smart/SmartChassisOperations.kt`
- 统一容器账本实体：`ContainerEntity.kt`、`ContainerSlotEntity.kt`、`StockItemEntity.kt`、`StockOperationEntity.kt`
- 智能底盘写操作先 BLE 成功再落本地账：`data/repository/SlotOperationRepositoryImpl.kt`
- 搜索 + Find-by-Light + BOM Pick-to-Light 规则：`feature/search/SearchViewModel.kt`、`feature/search/BomWorkflow.kt`
- LCSC 入库 QR 解析：`feature/inbound/LcscQrParser.kt`

### 2.4 硬件/固件仓库事实

本机存在硬件固件仓库：

```text
/Users/wq/PartRack-Hardware
```

必须与 iOS 开发同步参考的文件：

- `README.md`：当前 bring-up 状态和软件侧交接事项。
- `docs/verification-matrix.md`：硬件验证事实单一来源。
- `docs/app-ble-contract-v0.1.md`：APP 对接的稳定 BLE 契约。
- `docs/ble-protocol-v0.1.md`：固件侧 BLE v0.1 摘要。
- `protocol/viberack_protocol.h`：协议常量、结构体和 opcode。
- `protocol/test-vectors.json`：跨端测试向量。
- `tools/ble_gatt_smoke_test.py`：Mac/CoreBluetooth-Bleak 实机烟测脚本。

截至硬件仓库当前 README 和 verification matrix：

- 当前软件侧可联调设备名为 `VBRK-0000`。
- 当前开发 `batch_id` 为 `1`。
- BLE 扫描、连接、service discovery 已在 Seeed Studio XIAO nRF52840 Sense 上实机通过。
- `Table Info`、Binding Control Point、`WRITE_ONE -> READ_ONE`、paced `READ_ALL`、`SET_QTY` 已实机通过。
- settings/NVS 单槽重启恢复已实机通过。
- Light Command / Light Status 和 10s 超时自动 OFF 已实机通过。
- Device Health Service 已实机读取，样例 payload 为 `64 02 00 00`。
- NFC URI / NT3H2111 NDEF、OTA/Secure DFU、真实电池 ADC、真实 WS2812 槽位颜色仍不可依赖。
- XIAO nRF52840 Sense 的实机证据不能自动外推为 nRF52832 目标板或量产硬件证据。

固件仓库新增了 App 仓库 v0.1 文档未完整纳入的 Device Health Service：

| 项 | UUID / 布局 |
|---|---|
| Device Health Service | `7f4b0003-8d1a-4d45-9a4e-2b4a7c000000` |
| Device Health characteristic | `7f4b3001-8d1a-4d45-9a4e-2b4a7c000000`, Read + Notify |
| Payload | 4B: `battery_pct(1B) + reset_reason(2B LE) + health_flags(1B)` |

iOS M1 应读取 Device Health，不能只依赖广告电量、BAS 或 DIS。

---

## 3. 产品范围

### 3.1 iOS MVP 必做

1. 智能底盘发现与连接
   - 扫描 VibeRack 广播。
   - 解析 `proto_ver`、`batch_id`、`battery_pct`、`status_flags`、`table_seq`。
   - 连接后发现 Binding Table Service、Light Service、Device Health Service、BAS、DIS。
   - 读取 Device Health，展示或记录 `battery_pct`、`reset_reason`、`health_flags`。

2. 绑定表协议客户端
   - `READ_ONE`、`READ_ALL`、`WRITE_ONE`、`CLEAR_ONE`、`SET_QTY`。
   - `READ_ALL` 必须校验 25 条记录、`slot_count` 和 CRC16。
   - Slot Record 必须 16B 定长并校验 CRC-8/MAXIM。
   - Binding Control Point write 需要处理固件的 encrypted write 要求；iOS 端应在 Insufficient Authentication / Encryption 相关错误后触发重试或提示配对。
   - 写类操作成功后，固件 notify 顺序是 Binding CP 状态 notify，然后 Table Info notify；iOS 不得假设两个 notify 同一时刻到达。

3. 灯控协议客户端
   - 17B Light Command。
   - 支持 `OFF`、`FIND`、`PICK`、`STOCK_IN`。

4. 本地统一容器账本
   - `Component`
   - `Container`
   - `ContainerSlot`
   - `StockItem`
   - `StockOperation`
   - `SmartChassis` identity/cache 字段。

5. 数字孪生视图
   - 25 槽 5x5 网格。
   - 电量、缓存过期、槽位占用、数量、低库存标记。
   - 点击槽位可找料点灯、入库引导、修改数量、清槽。

6. 入库绑定
   - 扫 LCSC QR 或手动创建 Component。
   - 选择智能底盘和槽位。
   - `STOCK_IN` 绿灯引导。
   - `WRITE_ONE` 成功后再写本地账。

7. 搜索与 Find-by-Light
   - 按料号、MPN、名称、封装、规格搜索。
   - 命中智能底盘槽位后发 `FIND`。
   - 更新数量走 `SET_QTY`。

8. 从硬件恢复
   - NFC 或 BLE 选择底盘。
   - `READ_ALL` 后从 Binding Table 重建最小 Component/StockItem。
   - 保留已有富数据，只校准硬件事实。

### 3.2 MVP+ 紧随其后

- BOM 导入与 BOM Match。
- 按智能底盘分组生成 25-bit mask，下发 `PICK`。
- 低库存阈值与数字孪生热力状态。
- Android schema v2 Excel 导入兼容。

### 3.3 暂不做

- 旧 Android `Boxes` 完整复刻。
- 旧 Android `Inventory` 位置网格完整复刻。
- 蓝牙打印。
- Nordic DFU UI。
- 整理模式完整拖拽重排。
- 后台自动扫描仓库。
- 多用户云同步。

---

## 4. Apple 原生 UI 方案

### 4.1 主导航

iOS 使用 4 个 Tab：

| Tab | SwiftUI View | 对应 Android 资产 | 说明 |
|---|---|---|---|
| 底盘 | `ChassisListView` | `containers` | 默认首页，展示智能底盘和数字孪生入口 |
| 入库 | `StockInFlowView` | `inbound` + slot inbound | 扫码/手动创建组件并绑定到智能底盘槽位 |
| 搜索 | `SearchView` | `search` | 搜索组件、Find-by-Light，后续承载 BOM |
| 设置 | `SettingsView` | `settings` | 权限、恢复、诊断、导入导出 |

不把 `Printer`、`Boxes`、`Inventory` 作为 Tab。它们可以后续放在设置或调试入口。

这套导航是 iOS 首版的产品设计，不要求与 Android 的 route、Tab 数量、页面层级或组件布局一致。Android 对应屏幕只用于确认用户任务和数据依赖。

### 4.2 SwiftUI 组件原则

- 使用系统 `TabView`，不自绘底部导航。
- 每个 Tab 使用独立 `NavigationStack`，保留独立返回栈。
- 列表用 `List` / `Section`，设置用 `Form`。
- 弹层使用 `.sheet(item:)`，不要多布尔状态互相竞争。
- 操作用 `ToolbarItem`、`Menu`、`Button`、`Picker`、`Toggle`。
- 图标使用 SF Symbols。
- 不使用 Android Material 颜色、组件形态和交互动效。
- 不按 Android Compose 页面结构逐项翻译；同一业务流程可以在 iOS 上重新组织成更短的 sheet、toolbar、context menu 或分步导航。
- iOS 26 上可以使用原生 `glassEffect` / `.buttonStyle(.glass)` / `.buttonStyle(.glassProminent)`，但仅用于工具栏、浮动操作、状态胶囊等少量交互元素。
- 槽位网格是工具界面，优先清晰、稳定、可扫视，不做营销式大卡片。

### 4.3 iPhone Air 适配

- 5x5 槽位网格使用固定 aspect-ratio，避免文字撑开。
- Dynamic Type 下槽位内文字最多显示槽号、短料号、数量，超长料号截断。
- 主要动作放在底部 safe area 附近的原生按钮或 toolbar，不用悬浮大卡片遮挡网格。
- 数字孪生在竖屏优先；横屏只保证可用，不作为首版主体验。

---

## 5. iOS 工程结构

建议在当前仓库新增：

```text
ios/
  VibeRack.xcodeproj
  VibeRack/
    VibeRackApp.swift
    App/
      AppView.swift
      AppTab.swift
      Router.swift
      DependencyGraph.swift
    Core/
      Bluetooth/
      NFC/
      Persistence/
      Protocol/
      Scanner/
      Diagnostics/
    Domain/
      Models/
      Repositories/
      UseCases/
    Features/
      Chassis/
      StockIn/
      Search/
      BOM/
      Settings/
    Resources/
  VibeRackTests/
    ProtocolTests/
    PersistenceTests/
    UseCaseTests/
```

### 5.1 App shell

- `AppView`：安装 `TabView`、每 Tab 一个 `NavigationStack`。
- `AppTab`：`chassis`、`stockIn`、`search`、`settings`。
- `Router`：route enum + sheet enum。
- `DependencyGraph`：集中创建 BLE、NFC、数据库、repository、use case。

### 5.2 Core/Protocol

纯 Swift，不依赖 UI、BLE、SQLite：

- `SmartChassisProtocol.swift`
- `SmartChassisCodec.swift`
- `SlotRecord.swift`
- `TableInfo.swift`
- `LightCommand.swift`
- `DeviceHealth.swift`
- `CRC8Maxim.swift`
- `CRC16CcittFalse.swift`

先移植硬件仓库 `protocol/test-vectors.json`，再移植 Android 单测，确保 iOS、Android、固件三端二进制布局一致。

### 5.3 Core/Bluetooth

- `BluetoothCentral.swift`：封装 `CBCentralManagerDelegate`。
- `SmartChassisScanner.swift`：解析广播并发布扫描结果。
- `SmartChassisConnection.swift`：连接、服务发现、特征读写、Notify。
- `SmartChassisClient.swift`：对外提供 async API。
- `ChassisSimulatorClient.swift`：模拟器/UI 开发用，不伪装成真机验证。
- `DeviceHealthClient.swift`：读取并订阅 Device Health characteristic。

### 5.4 Core/Persistence

主存储使用 GRDB/SQLite：

- 明确 schema、外键、唯一索引、事务。
- 便于与 Android Room schema、Excel 备份、未来云端增量同步对齐。
- SwiftData 暂不作为主账本；后续如果只做轻量 UI 状态可局部使用，但不是 Stock Ledger 的事实存储。

P0 表：

```sql
component(id, protocol_part_id, source, lcsc_part_number, manufacturer_part_number, name, package_name, brand, spec_summary, image_path, created_at, updated_at)
container(id, code, display_name, type, slot_count, batch_id, proto_version, advertised_name, nfc_mac_hint, ios_peripheral_identifier, battery_pct, status_flags, table_seq, table_crc16, firmware_version, hardware_version, last_seen_at, last_synced_at, created_at, updated_at)
container_slot(id, container_id, slot_number, slot_code, display_name, sort_order, created_at, updated_at)
stock_item(id, component_id, container_id, container_slot_id, quantity, quantity_state, safety_stock_threshold, flags, last_inbound_at, updated_at)
stock_operation(id, type, container_id, container_slot_id, component_id, quantity_before, quantity_after, quantity_delta, source_type, source_ref, raw_payload, ble_opcode, ble_status, table_seq_before, table_seq_after, created_at)
```

---

## 6. 身份与协议风险

### 6.1 iOS 不暴露真实 BLE MAC

Android 代码大量以 MAC 作为智能底盘身份键，但 iOS CoreBluetooth 不给通用 App 暴露外设真实 MAC 地址。iOS 不能把 NFC URI 中的 `mac` 直接当连接句柄。

iOS MVP 身份策略：

1. NFC 读取 `batch`、`ver`、`mac`，其中 `mac` 只作为 hint。
2. BLE 扫描 VibeRack Manufacturer Data。
3. 优先匹配 `batch_id` 和 `proto_ver`。
4. 连接后读取 Table Info / DIS / BAS。
5. 本机缓存 `CBPeripheral.identifier`，但不把它当跨设备稳定身份。

协议建议：

- 如果 `batch_id` 不能保证用户仓库内唯一，应在 v0.2 协议增加稳定硬件唯一 ID 广播字段或 GATT identity characteristic。
- 在协议更新前，iOS 端遇到多个相同 `batch_id` 外设必须要求用户选择并提示风险。

### 6.2 写操作事实源

智能底盘的槽位绑定以硬件 Binding Table 为事实源。iOS 本地账本只能在以下情况更新智能底盘槽位：

- BLE 写操作成功并返回有效状态。
- `READ_ALL` 通过 25 条记录、slot_count、CRC16 校验。
- 用户选择放弃本地缓存并以硬件恢复结果覆盖智能底盘缓存。

不得在 iOS 端自行重编号后假定硬件成功。

---

## 7. 阶段计划

### M0：工程与协议

目标：在 `iPhone Air` 模拟器跑起 SwiftUI 空壳，协议单测通过。

- 创建 `ios/` SwiftUI 工程。
- 设置最低系统：iOS 26.0 开发阶段；后续发布前再评估是否下探。
- 配置 SPM：GRDB。
- 创建 4 Tab shell。
- 移植协议常量和编解码。
- 加载或镜像 `/Users/wq/PartRack-Hardware/protocol/test-vectors.json` 中的 frame。
- 移植 Android `SmartChassisCodecTest` 测试向量。
- 增加 Device Health 4B payload 解析测试。

验收：

- `xcodebuild test` 通过协议测试。
- `Package.resolved` 决策：提交 Xcode app workspace 下的锁文件，当前 `.gitignore` 已通过 `!ios/VibeRack.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` 明确放行；继续忽略根目录或其他 SwiftPM local package 的 `Package.resolved`。
- 以下硬件仓库测试向量在 XCTest 中逐项通过：
  - `WRITE_ONE slot 1, part_id C1234567, qty 12`
  - `READ_ONE slot 1`
  - `READ_ALL`
  - `READ_ALL` end frame `02 00 FF`
  - `FACTORY_RESET` wire bytes `F0 A5 A5 5A 5A`
  - `FIND slot 1 green 30s`
  - `PICK slots 1,7,25 green 30s`
  - `OFF`
- 以下 Android 迁移向量在 XCTest 中以 `testAndroidMigrationVector...` 命名显式覆盖：
  - Core advertisement 解析并忽略 reserved tail。
  - Android manufacturer payload 不含 company bytes 的解析。
  - Table Info 解析。
  - Slot Record 编码、解析和 CRC-8/MAXIM 校验。
  - CRC-8/MAXIM 与 CRC16/CCITT-FALSE check values。
  - Light Status 和 READ_ALL end payload 解析。
- Device Health XIAO 样例 `64 02 00 00` 以独立 XCTest 覆盖。
- `xcodebuild build -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 通过。
- 模拟器截图确认 4 Tab 原生 UI 可见。

M0 收尾记录：

| 日期 | 命令 / 证据 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-16 | `git check-ignore -v ios/VibeRack.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` | 未被忽略 | `.gitignore` 第 60 行忽略通用 `Package.resolved`，第 61 行放行 iOS App 工程锁文件 |
| 2026-06-16 | `ios/VibeRack.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` | GRDB 7.11.0 锁定 | revision `9ed8c8457e00ff9c7aedb3bf213f20a2cfdf509e` |
| 2026-06-16 | `xcodebuild test -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` | 通过 | 覆盖协议、Android 迁移向量、BLE UUID/广播解析和 Router 测试；xcresult `Test-VibeRack-2026.06.16_22-46-09-+0800.xcresult` |
| 2026-06-16 | `xcodebuild build -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` | 通过 | 验证 4 Tab SwiftUI shell |
| 2026-06-16 | XcodeBuildMCP `build_run_sim` + `snapshot_ui` + `screenshot` | 通过 | iPhone Air 模拟器启动 `com.viberack.ios`，4 Tab 可见；截图 `/var/folders/p6/m7cr2vmd68b1t7mt87xjnl1c0000gn/T/screenshot_optimized_e3182fb9-f0f0-413d-88e6-6ca5e1f5c053.jpg` |

### M1：BLE 发现与连接

目标：真机发现 `VBRK-0000` 并读基础信息。

- CoreBluetooth central。
- 扫描广播并解析 Manufacturer Data。
- 连接目标底盘。
- Discover Binding Table Service、Light Service、Device Health Service、BAS、DIS。
- 读取 Table Info、Light Status、Device Health。
- 支持 Binding Control Point encrypted write 的配对/加密错误处理。

验收：

- 真机截图/日志显示 `batch_id`、电量、`table_seq`。
- 真机日志读取 Device Health，当前 XIAO 样例应可解析 `64 02 00 00` 为 battery 100、reset_reason `0x0002`、health_flags `0x00`。
- 多台底盘可区分；同 batch 冲突可提示。
- 明确记录验证对象是 XIAO nRF52840 Sense 还是 nRF52832/回板硬件。

M1 当前实机记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-16 | Mac CoreBluetooth/Bleak 扫描 | 发现 `VBRK-0000`，RSSI `-56` | macOS 系统蓝牙列表也显示 `VBRK-0000`，地址 `D9:2F:81:45:C1:62` |
| 2026-06-16 | Manufacturer Data 解析 | company `0xFFFF`，payload `01 01 00 64 02 11 00 00 00` | `proto_ver=1`、`batch_id=1`、`battery_pct=100`、`status_flags=0x02`、`table_seq_low16=0x0011`；当前广告未携带 service UUID，iOS 扫描需全扫后按 Manufacturer Data 过滤 |
| 2026-06-16 | GATT service discovery | Binding Table、Device Health、Light 均发现 | Binding CP `write, notify`；Table Info `read, notify`；Light Command `write-without-response`；Light Status `read, notify`；Device Health `read, notify` |
| 2026-06-16 | Table Info | `11 00 00 00 21 F5 19` | `table_seq=17`、`crc16=0xF521`、`slot_count=25` |
| 2026-06-16 | Light Status | `00 00 00` | `mode=OFF`、`remaining=0` |
| 2026-06-16 | Device Health | `64 02 00 00` | `battery=100`、`reset_reason=0x0002`、`health_flags=0x00` |
| 2026-06-16 | BAS / DIS | 当前未发现 | `2A19`、`2A26`、`2A27` 读取返回 characteristic not found；iOS M1 应把 BAS/DIS 作为可选信息处理，不能阻塞自定义服务闭环 |
| 2026-06-16 | iPhone 真机 CoreBluetooth 扫描 | 未验证 | 当前 `iPhone Air (26.3.1)` 在 `xcrun xctrace list devices` 中为 offline，在 `xcrun devicectl list devices` 中为 unavailable；Mac/Bleak 扫描证据不能替代 iOS 真机 App 扫描证据 |
| 2026-06-17 | Mac/Bleak Device Health 只读检查 | 通过 | `.venv/bin/python tools/ble_gatt_smoke_test.py --run-device-health` 返回 `64 02 00 00`；确认 `VBRK-0000` 当前可被 Mac 侧 BLE 脚本访问 |
| 2026-06-17 | Mac/Bleak 非破坏性 batch smoke | 通过 | `.venv/bin/python tools/ble_gatt_smoke_test.py --run-batch` 完成 Table Info、Light Status、`WRITE_ONE -> READ_ONE`、`READ_ALL` 结束帧、`SET_QTY`、FIND/OFF 状态；这是硬件侧 Mac 证据，不替代 iPhone App 证据 |

### M2：绑定表闭环

目标：READ_ALL/WRITE_ONE/SET_QTY/CLEAR_ONE 可用。

- Binding Control Point Notify 聚合。
- READ_ALL 结束帧识别。
- CRC16 校验。
- 写操作错误码映射。
- Flash busy 退避重试。

验收：

- READ_ALL 成功后得到 25 槽。
- WRITE_ONE 后 Table Info 变化。
- SET_QTY 后重读数量一致。
- CRC 失败时不落本地账。

M2 当前状态：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | `BindingTableReadAllAggregator` 聚合 25 槽并校验 CRC16 | 通过 | XCTest 覆盖结束帧、缺记录、乱序、CRC mismatch、错误 status |
| 2026-06-17 | `SmartChassisWorkflow` 写类操作成功后才落本地账 | 通过 | 模拟器 client 覆盖 `WRITE_ONE`、`SET_QTY`、`CLEAR_ONE` 业务路径；硬件失败不落账 |
| 2026-06-17 | 真实 Binding Control Point Notify 顺序和 Table Info 变化 | 未验证 | 需要 iPhone 真机 + 智能底盘硬件 |

### M3：本地账本与数字孪生

目标：在 iPhone Air 模拟器和真机都能看到 25 槽数字孪生。

- GRDB schema。
- Container/Slot/StockItem/StockOperation repository。
- 5x5 槽位网格。
- 缓存过期提示。
- 槽位详情 sheet。

验收：

- 模拟器用 `ChassisSimulatorClient` 展示 25 槽。
- 真机 READ_ALL 后落本地账。
- 重启 App 后账本恢复。

M3 当前状态：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | GRDB 本地账本与操作流水 | 通过 | 覆盖 seed、stock-in、set quantity、clear、restore、migration 兼容 |
| 2026-06-17 | iPhone Air 模拟器 25 槽数字孪生 | 通过 | XcodeBuildMCP snapshot 显示 25 个可点击槽位目标 |
| 2026-06-17 | 槽位详情 sheet 的 `FIND` / `SET_QTY` / `CLEAR_ONE` | 通过 | 模拟器 workflow 路径通过 XCTest 和 build 验证 |
| 2026-06-17 | 真机 READ_ALL 后落本地账并重启恢复 | 未验证 | 需要真实 BLE `READ_ALL` 验收 |

### M4：入库绑定与 Find-by-Light

目标：完成核心价值链中的扫码入库和找料点灯。

- AVFoundation 扫码。
- LCSC QR parser 移植。
- 手动 Component 创建。
- 选择底盘/槽位。
- STOCK_IN -> WRITE_ONE -> 本地落账。
- 搜索命中后 FIND。

验收：

- 扫一个 LCSC 标签，绑定到指定槽位。
- 搜索该组件，点亮正确槽位。
- 硬件失败时本地不出现假绑定。

M4 当前状态：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | 手动协议料号入库绑定模拟闭环 | 通过 | 入库 Tab 执行 `STOCK_IN` -> `WRITE_ONE` receipt -> 本地落账 |
| 2026-06-17 | 搜索命中后 Find-by-Light 模拟闭环 | 通过 | 搜索 Tab 调用 workflow 发送 `FIND`；Find 不改变账本 |
| 2026-06-17 | 硬件失败时本地不出现假绑定 | 通过 | `SmartChassisWorkflowTests` 覆盖 `.errFlashBusy` 不落账 |
| 2026-06-17 | AVFoundation 扫码和 LCSC QR parser iOS 移植 | 未完成 | 下一阶段代码任务 |
| 2026-06-17 | 真实灯控和真实 `WRITE_ONE` | 未验证 | 需要 iPhone 真机 + 智能底盘硬件 |

### M5：硬件恢复

目标：删除本地数据后，从底盘恢复最小库存。

- NFC NDEF 读取。
- 选择/连接底盘。
- READ_ALL restore。
- placeholder Component。
- Hardware Restore StockOperation。

验收：

- 清空本地数据库。
- NFC 或 BLE 选择底盘。
- 恢复非空槽位和数量。
- 组件富数据缺失时显示可补全状态。

M5 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | Repository 从 `BindingTableSnapshot` 恢复本地账本 | 通过 | `READ_ALL` 快照先走 `BindingTableReadAllAggregator.validate`；非空槽 upsert，硬件空槽删除本地库存；记录 `restore` StockOperation；更新 `table_seq` / `table_crc16` |
| 2026-06-17 | Settings -> 从底盘恢复 -> 运行模拟恢复 | 通过 | XcodeBuildMCP 在 iPhone Air 模拟器触发模拟 READ_ALL 恢复，UI 显示 `已按 READ_ALL 快照恢复 2 个非空槽位，table_seq 42` |
| 2026-06-17 | BLE/NFC 真机恢复 | 未验证 | 当前实现只覆盖协议模拟和本地账本闭环，不能替代真实 NFC 选择底盘、BLE READ_ALL、真机恢复验收 |

### M6：BOM Pick-to-Light

目标：把 Android 已有 BOM 匹配规则迁到 iOS。

- CSV/XLSX 导入策略先定 CSV；XLSX 可 P1 通过第三方库或服务端转换。
- Supplier Part -> Manufacturer Part -> passive footprint/value 匹配。
- 按智能底盘分组生成 mask。
- PICK 下发和逐项完成后重发 mask。

验收：

- BOM 行命中智能底盘库存。
- 每台底盘收到正确 25-bit mask。
- 勾选完成后剩余灯位正确。

M6 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | `BOMPickPlanner` 按 BOM 行匹配智能底盘库存 | 通过 | 覆盖 Supplier Part 大小写归一化、未命中行保留、同一 BOM 行跨底盘命中 |
| 2026-06-17 | 按智能底盘分组生成 PICK 灯控 mask | 通过 | 每组从命中槽位生成 25-bit mask，并构造 `.pick` `LightCommand`，颜色使用绿色，超时使用协议默认值 |
| 2026-06-17 | CSV/XLSX 导入、勾选完成后重发 mask、真实 BLE PICK 下发 | 未验证 | 当前切片只覆盖领域规划器和协议命令生成；UI 导入和真机下发仍待后续 M6 切片 |

### M7：非真机阻塞功能推进队列

目标：在 iPhone 真机暂不可用时，继续把 App 侧业务能力、账本模型和导入体验做完整。后续可用 `goal` 命令按下面任务批量推进。

这些任务不依赖 iPhone BLE/NFC 真机验收，可在模拟器、XCTest、Mac 侧硬件 smoke 或静态文件输入下完成：

#### M7.1 Component 账本模型

输入：

- 当前 GRDB schema：`container`、`container_slot`、`stock_item`、`stock_operation`。
- 当前搜索实现只基于 `stock_item.protocol_part_id`。

改动范围：

- 增加 `component` 表和 migration。
- 给 `stock_item` 增加 `component_id`，保留 `protocol_part_id` 作为协议恢复字段。
- Repository 增加创建/更新 Component、按富字段搜索库存的 API。
- UI 搜索从只搜 `protocol_part_id` 扩展到 LCSC 料号、MPN、名称、封装、规格摘要。

验证：

- 新增 `ComponentRepositoryTests`：
  - migration 后旧 seed 数据仍可读。
  - 创建自定义 Component 后可绑定到槽位。
  - 搜索 MPN、名称、封装、规格摘要能命中库存。
  - 硬件恢复仅有 `protocol_part_id` 时可创建 placeholder Component。
- 运行：

```bash
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1' \
  -only-testing:VibeRackTests/ChassisRepositoryTests
```

完成证据：

- 测试通过。
- 搜索 UI 中可用非协议料号字段命中模拟库存。

M7.1 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | Component schema 与 `stock_item.component_id` migration | 通过 | `component` 表、placeholder Component、旧 seed 数据兼容均由 XCTest 覆盖 |
| 2026-06-17 | 富字段搜索 | 通过 | `SearchRepositoryTests` 覆盖 MPN、名称、封装、规格摘要命中；搜索 UI 主标题优先显示 MPN/LCSC |
| 2026-06-17 | 硬件恢复 placeholder Component | 通过 | `READ_ALL` 恢复仅有 `protocol_part_id` 时自动创建 hardware_restore 来源 Component |

#### M7.2 LCSC QR / 扫码入库

输入：

- Android 参考：`feature/inbound/LcscQrParser.kt`。
- 当前 iOS 入库页支持手动 `protocolPartId` + 数量。

改动范围：

- 移植 LCSC QR parser 到 Swift。
- 增加 parser XCTest，覆盖 `pc`、数量、空值、异常二维码。
- 增加 AVFoundation 扫码入口；模拟器提供手动粘贴 QR payload fallback。
- 扫码解析后进入现有 `SmartChassisWorkflow.stockIn`。

验证：

- 新增 `LcscQrParserTests`：
  - 解析 LCSC part number。
  - 解析数量。
  - 缺字段时给出可理解错误。
  - 非 LCSC QR 不误识别。
- 模拟器验证：
  - 入库页手动粘贴 QR payload 后自动填入组件和数量。
  - 点击绑定后仍走 `STOCK_IN -> WRITE_ONE -> 本地落账`。
- 运行：

```bash
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1' \
  -only-testing:VibeRackTests/LcscQrParserTests \
  -only-testing:VibeRackTests/SmartChassisWorkflowTests
```

完成证据：

- Parser 测试通过。
- 模拟器入库页能从 QR payload 填充字段并绑定到空槽。

M7.2 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | Swift `LcscQrParser` | 通过 | 覆盖 `pc`、`qty`、空数量、大小写 key、非 LCSC QR、缺 `pc` 错误 |
| 2026-06-17 | 入库页 QR payload fallback | 通过 | 手动粘贴 LCSC payload 后填入 LCSC 料号和数量；绑定仍走 `STOCK_IN -> WRITE_ONE -> 本地落账` |
| 2026-06-17 | AVFoundation 相机扫码 | 未完成 | 当前只做模拟器可用的粘贴 fallback；真机相机扫码入口后续补齐 |

#### M7.3 BOM 导入和 Pick-to-Light UI

输入：

- 已确认样例文件：`assets/bom.xlsx`，来源为处理后的 `/Users/wq/Downloads/BOM_原版（已验证）_PCB1_2_2026-06-10.xlsx`。
- 样例 workbook：
  - sheet：`BOM_原版（已验证）_PCB1_2_2026-06-10`
  - 行数：51，包含 1 行表头和 50 行有效 BOM 数据
  - 列数：10
  - 表头：`No.`、`Quantity`、`Comment`、`Footprint`、`Value`、`Manufacturer Part`、`Manufacturer`、`Supplier Part`、`Supplier`、`Designator`
- 当前 `BOMPickPlanner` 已能按 BOM 行匹配库存并生成 `PICK` mask。

改动范围：

- 增加 BOM workbook/CSV 解析器。
- 先支持上述 XLSX 表头；CSV 可作为同列名导入 fallback。
- 增加 BOM 导入 UI、BOM 行列表、匹配/未匹配状态。
- 按智能底盘分组展示 pick group。
- 勾选完成项后重算剩余 mask，并通过 simulator client 发送 `PICK`。

验证：

- 新增 `BOMImportTests`：
  - 从样例 XLSX 读取 50 条 BOM 数据行。
  - 表头映射正确。
  - `Supplier Part = C2829702` 的行能读出 `Quantity`、`Manufacturer Part`、`Manufacturer`、`Designator`。
  - 空 Supplier Part 的被动件行仍保留 Comment/Footprint/Value，供后续规则匹配。
- 新增/扩展 `BOMPickPlannerTests`：
  - 从样例导入结果匹配库存。
  - 勾选完成后剩余 mask 不再包含已完成槽位。
- 运行：

```bash
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1' \
  -only-testing:VibeRackTests/BOMImportTests \
  -only-testing:VibeRackTests/BOMPickPlannerTests
```

完成证据：

- 样例 XLSX 导入测试通过。
- 模拟器 BOM 页面显示 50 条数据行、匹配状态和按底盘分组的 PICK mask。

M7.3 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | `BOMWorkbookImporter` 导入 `assets/bom.xlsx` | 通过 | `BOMImportTests` 覆盖 sheet 名称、10 列表头、50 条数据行、`C2829702` 行和空 Supplier Part 被动件行 |
| 2026-06-17 | BOM planner 匹配导入行并重算剩余 mask | 通过 | `BOMPickPlannerTests` 覆盖样例 `C2829702` 命中库存，以及完成项不再进入剩余 PICK mask |
| 2026-06-17 | 搜索 Tab -> BOM Pick 模拟器页面 | 通过 | iPhone Air 模拟器显示 sheet `BOM_原版（已验证）_PCB1_2_2026-06-10`、数据行 50、已匹配 2、未匹配 48、剩余分组 1；J2/CN1 命中 `VBRK-0000` 槽位 7，mask `0x40` |
| 2026-06-17 | 模拟器发送 `PICK` | 通过 | BOM Pick 页面点击 `发送 PICK` 走 `SmartChassisWorkflow.pickByLight`；`SmartChassisWorkflowTests` 覆盖 `.pick` 灯控命令不改变本地账本 |
| 2026-06-17 | 全量 iOS XCTest | 通过 | `xcodebuild test -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 通过 45 个测试；xcresult `Test-VibeRack-2026.06.17_19-21-15-+0800.xcresult` |
| 2026-06-17 | iOS simulator build | 通过 | `xcodebuild build -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 返回 `BUILD SUCCEEDED` |

#### M7.4 硬件恢复 UI 打磨

输入：

- 当前 Repository 已支持 `restoreFromBindingTableSnapshot`。
- 设置页已有模拟 READ_ALL 恢复入口。

改动范围：

- 增加恢复预览页。
- 显示将新增、更新、清空的槽位。
- 恢复后展示缺少富数据的 Component 列表，引导用户补全。

验证：

- 新增 `HardwareRestorePreviewTests`：
  - 比较本地账本和 `BindingTableSnapshot` 后能列出新增、更新、清空。
  - CRC 失败时不生成可应用预览。
- 模拟器验证：
  - 设置页运行模拟恢复前可看到预览。
  - 应用恢复后数字孪生槽位和操作流水更新。
- 运行：

```bash
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1' \
  -only-testing:VibeRackTests/HardwareRestorePreviewTests \
  -only-testing:VibeRackTests/ChassisRepositoryTests
```

完成证据：

- 预览测试通过。
- 模拟器设置页恢复流程可从预览到应用完整走通。

M7.4 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | `HardwareRestorePreviewBuilder` 恢复预览 | 通过 | `HardwareRestorePreviewTests` 覆盖新增、更新、清空槽位分类；CRC mismatch 时抛出 `SmartChassisBindingTableError.crcMismatch`，不生成可应用预览 |
| 2026-06-17 | Repository 应用 `READ_ALL` snapshot | 通过 | `ChassisRepositoryTests` 覆盖 table_seq/CRC 更新、槽位新增/更新/清空、restore operation 记录和 placeholder Component |
| 2026-06-17 | 设置页模拟恢复预览 UI | 通过 | XcodeBuildMCP 模拟器验证：设置 -> 从底盘恢复 -> 生成恢复预览，显示 `table_seq 42`、CRC `0x7E7D`、新增 1、更新 1、清空 1 |
| 2026-06-17 | 设置页应用恢复 | 通过 | 模拟器点击 `应用恢复` 后显示 `已按 READ_ALL 快照应用恢复，table_seq 42`，并清空预览状态 |
| 2026-06-17 | 全量 iOS XCTest | 通过 | `xcodebuild test -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 通过 47 个测试；xcresult `Test-VibeRack-2026.06.17_19-33-19-+0800.xcresult` |
| 2026-06-17 | iOS simulator build | 通过 | `xcodebuild build -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 返回 `BUILD SUCCEEDED` |

#### M7.5 诊断页

输入：

- 当前 `SmartChassisWorkflow`、`ChassisSimulatorClient`、`stock_operation`。
- Mac/Bleak smoke 可读 `VBRK-0000`。

改动范围：

- 在设置页增加诊断入口。
- 展示最近 stock operations。
- 展示最近模拟 client 命令日志：mode/opcode/status/table_seq/payload hex。
- 展示 Mac/Bleak smoke 最近证据的手动记录区。

验证：

- 新增 `DiagnosticsTests`：
  - workflow 操作后诊断事件包含 opcode/status/table_seq。
  - Light Command payload hex 与协议编码一致。
- 模拟器验证：
  - 入库、Find、Set Qty 后诊断页能看到事件。
- 运行：

```bash
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1' \
  -only-testing:VibeRackTests/DiagnosticsTests \
  -only-testing:VibeRackTests/SmartChassisWorkflowTests
```

完成证据：

- 诊断测试通过。
- 模拟器诊断页能看到最近操作。

M7.5 当前模拟器记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | `DiagnosticsTests` 诊断事件 | 通过 | 覆盖 Binding 命令的 opcode/status/table_seq/payload hex，以及 Light Command payload hex |
| 2026-06-17 | 设置页诊断事件 UI | 通过 | 模拟器验证：Find by Light 后显示 `Light 0x1` 与 payload `01 01 00 00 00 00 00 00 00 00 FF 00 00 00 00 1E 00` |
| 2026-06-17 | 入库绑定诊断链路 | 通过 | 入库 `C7654321` 到槽位 2 后诊断页显示 `stock_in`、`Binding 0x10`、seq 18、payload `10 02 43 37 36 35 34 33 32 31 00 00 01 00 00 00 F8` |
| 2026-06-17 | M7.5 定向 XCTest | 通过 | `DiagnosticsTests` + `SmartChassisWorkflowTests` 共 8 个测试通过；xcresult `Test-VibeRack-2026.06.17_19-46-41-+0800.xcresult` |
| 2026-06-17 | 全量 iOS XCTest | 通过 | `xcodebuild test -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 通过 50 个测试；xcresult `Test-VibeRack-2026.06.17_19-49-57-+0800.xcresult` |
| 2026-06-17 | iOS simulator build | 通过 | `xcodebuild build -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 返回 `BUILD SUCCEEDED` |

#### M7.6 协议文档同步

输入：

- 硬件仓库已实现 Device Health Service：
  - service `7f4b0003-8d1a-4d45-9a4e-2b4a7c000000`
  - characteristic `7f4b3001-8d1a-4d45-9a4e-2b4a7c000000`
  - payload `battery_pct(1B) + reset_reason(2B LE) + health_flags(1B)`
- iOS 代码和测试已覆盖 `64 02 00 00`。

改动范围：

- 更新 `docs/智能底盘BLE接口规格_v0.1.md`。
- 明确 BAS/DIS 当前可选，不能阻塞 Binding/Light/Device Health 自定义服务闭环。
- 确认 Android/iOS/硬件文档用词一致。

验证：

- 文档 grep 检查包含 Device Health UUID、payload 布局、`64 02 00 00` 样例。
- 协议 XCTest 仍通过。
- 运行：

```bash
rg -n "Device Health|7f4b0003|7f4b3001|64 02 00 00" docs/智能底盘BLE接口规格_v0.1.md
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1' \
  -only-testing:VibeRackTests/SmartChassisProtocolVectorTests
```

完成证据：

- grep 命中关键字段。
- 协议向量测试通过。

M7.6 当前文档同步记录：

| 日期 | 验证项 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | 主 BLE 规格纳入 Device Health | 通过 | `docs/智能底盘BLE接口规格_v0.1.md` 已加入 service `7f4b0003-8d1a-4d45-9a4e-2b4a7c000000`、characteristic `7f4b3001-8d1a-4d45-9a4e-2b4a7c000000` |
| 2026-06-17 | Device Health payload 布局与样例 | 通过 | 文档明确 4B payload：`battery_pct(1B) + reset_reason(2B LE) + health_flags(1B)`；样例 `64 02 00 00` 表示 battery 100、reset_reason `0x0002`、health_flags `0x00` |
| 2026-06-17 | BAS/DIS 可选语义同步 | 通过 | 主协议、iOS 技术文档、硬件仓库对接指南和产品技术文档均明确 BAS/DIS/DFU 为可选补充，不能阻塞 Binding/Light/Device Health 自定义服务闭环 |
| 2026-06-17 | 文档 grep | 通过 | `rg -n "Device Health|7f4b0003|7f4b3001|64 02 00 00" docs/智能底盘BLE接口规格_v0.1.md` 命中关键字段 |
| 2026-06-17 | 协议向量 XCTest | 通过 | `SmartChassisProtocolVectorTests` 10 个测试通过；xcresult `Test-VibeRack-2026.06.17_19-55-03-+0800.xcresult` |
| 2026-06-17 | 全量 iOS XCTest | 通过 | `xcodebuild test -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 通过 50 个测试；xcresult `Test-VibeRack-2026.06.17_19-56-29-+0800.xcresult` |
| 2026-06-17 | iOS simulator build | 通过 | `xcodebuild build -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` 返回 `BUILD SUCCEEDED` |

准备状态：

| 项 | 当前状态 | 下个 session 入口 |
|---|---|---|
| BLE 外设 `VBRK-0000` | Mac/Bleak 可访问，Device Health 和 batch smoke 通过 | 可先做真实 `SmartChassisClient` adapter 的 Mac 对照验证；iPhone App 证据仍待真机 online |
| BOM 样例 | 已处理原文件尾部空行，并同步到仓库 `assets/bom.xlsx`；当前为 1 行表头 + 50 行有效 BOM 数据 | 下个 session 直接使用 `assets/bom.xlsx` 作为测试 fixture |
| App 侧模拟器闭环 | 4 Tab、25 槽数字孪生、入库/搜索/槽位操作模拟闭环通过 | 可直接推进 Component、扫码、BOM UI、诊断页 |

---

## 8. 测试矩阵

| 能力 | 模拟器 | 真机 | 固件/硬件 |
|---|---:|---:|---:|
| SwiftUI 主导航 | 必测 | 抽测 | 不需要 |
| 数字孪生布局 | 必测 `iPhone Air` | 必测 | 不需要 |
| 协议编解码 | 必测 XCTest | 必测 XCTest | 与固件测试向量对齐 |
| Device Health 解析 | 必测 XCTest | 必测读取 | 当前 XIAO 已有样例 `64 02 00 00` |
| SQLite 账本 | 必测 | 必测 | 不需要 |
| BLE 扫描 | 不可验证 | 必测 | 必测 |
| BLE GATT/Notify | 不可验证 | 必测 | 必测 |
| NFC NDEF | 不可验证 | 必测 | 需真实 NFC 标签/NT3H2111 |
| 相机扫码 | 模拟器可有限验证 | 必测 | 不需要 |
| 灯控 | 不可验证 | 必测 | 必测 |
| 硬件恢复 | 协议模拟可测 | 必测 | 必测 |
| 打印 | 不做 | 后续评估 | 后续评估 |

---

## 9. 开发验收命令

计划中的 iOS 工程创建后，使用：

```bash
xcodebuild test \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'

xcodebuild build \
  -project ios/VibeRack.xcodeproj \
  -scheme VibeRack \
  -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'
```

真机 BLE/NFC 验收不能由模拟器替代。每个硬件里程碑需要保留：

- 真机型号和 iOS 版本。
- 硬件仓库 commit 或 UF2 SHA-256。
- 固件版本。
- 验证对象：XIAO nRF52840 Sense、nRF52832 目标板或回板硬件。
- 底盘 batch_id。
- 操作日志。
- 失败时的 BLE opcode/status/table_seq。

如果使用当前硬件仓库已烧录 peripheral UF2，应记录 README 中给出的 SHA-256：

```text
25514abd08d93a7154a704e4b9a151acb4f7823dca6617c8cb043741ea972689
```

### 9.1 2026-06-17 执行补充记录

本轮已补齐模拟器可验证的核心工作流闭环：

- 新增 `SmartChassisWorkflow` 应用层用例和 `SmartChassisClient` 抽象。
- 新增 `ChassisSimulatorClient`，模拟器路径会编码真实协议帧并返回成功 receipt，但不伪装成真机 BLE 证据。
- 入库 Tab 从直接写本地账改为 `STOCK_IN` 灯控 -> `WRITE_ONE` 成功 receipt -> 本地 `stock_item` / `stock_operation` 落账。
- 搜索 Tab 的 Find-by-Light 从只生成文案改为调用 workflow 发送 `FIND` 灯控命令。
- 数字孪生槽位可点击打开详情 sheet，支持 `FIND`、`SET_QTY`、`CLEAR_ONE`，数量/清槽都坚持硬件操作成功后再更新本地账本。
- 新增 `SmartChassisWorkflowTests` 覆盖：
  - stock-in 先发送 `STOCK_IN` 和 `WRITE_ONE`，再落本地账。
  - 硬件写失败时本地不出现假绑定。
  - Find-by-Light 只发灯控，不改账本。
  - `SET_QTY` 成功后再更新本地数量和操作流水。

验证命令 / 证据：

| 日期 | 命令 / 证据 | 结果 | 备注 |
|---|---|---|---|
| 2026-06-17 | `xcodebuild test -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` | 通过 | 覆盖协议向量、BLE UUID/广播、READ_ALL 聚合、GRDB 账本、搜索、BOM、Router、SmartChassisWorkflow；xcresult `Test-VibeRack-2026.06.17_00-54-36-+0800.xcresult` |
| 2026-06-17 | `xcodebuild build -project ios/VibeRack.xcodeproj -scheme VibeRack -destination 'platform=iOS Simulator,name=iPhone Air,OS=26.1'` | 通过 | Debug simulator build 成功 |
| 2026-06-17 | XcodeBuildMCP `build_run_sim` + `snapshot_ui` | 通过 | iPhone Air 模拟器启动 `com.viberack.ios`，底盘、入库、搜索、设置 4 Tab 可见；25 槽数字孪生可见且槽位为可点击目标 |
| 2026-06-17 | `xcrun devicectl list devices` + `xcrun xctrace list devices` | iPhone Air unavailable/offline | 真机 BLE/NFC 仍未验证；模拟器证据不能替代 M1/M2/M5 的真实 CoreBluetooth/CoreNFC 验收 |

---

## 10. 待确认事项

这些不是阻塞 M0/M1 的问题，但需要尽早确认：

1. `batch_id` 是否保证用户仓库内唯一。若不能，协议 v0.2 需要稳定硬件 ID。
2. iOS 首版最低系统是否锁 iOS 26，还是 UI 使用 iOS 26 fallback 并降低部署目标。
3. GRDB 是否可作为项目允许的第三方依赖；若必须全 Apple 框架，备选为 Core Data。
4. LCSC enrichment 在 iOS 首版是否需要上线；网页解析不稳定，建议 P1。
5. BOM 首版是否只支持 CSV；XLSX 在 iOS 上不如 Android Apache POI 直接。
6. 是否需要从 Android Excel schema v2 导入历史数据；需要的话先修正/容错 `containers` sheet 重复 `slotCount` 表头。
7. App 仓库 `docs/智能底盘BLE接口规格_v0.1.md` 是否需要补入硬件仓库已实现的 Device Health Service，避免 iOS/Android 文档分叉。
8. iOS 加密写/配对体验是否以当前 XIAO 固件为准，还是等待 nRF52832 目标板后再定最终 UX。

---

## 11. 文档自查

- 没有把旧 Android 6 Tab 当作 iOS 产品目标。
- 没有把 Android 页面当作 iOS UI 模板；Android 仅作为需求和实现参考。
- 没有把模拟器写成能验证 BLE/NFC。
- 没有把 NFC MAC 写成 iOS 可直接连接能力。
- 没有把打印纳入 MVP。
- 数据主模型以统一容器账本为中心。
- 智能底盘绑定坚持硬件事实源。
- 本机工具链和模拟器名称已用命令验证。
- 已把 `/Users/wq/PartRack-Hardware` 纳入 iOS 协议与验证基线。
- 已区分 XIAO nRF52840 Sense 证据和 nRF52832/回板硬件待验证项。
