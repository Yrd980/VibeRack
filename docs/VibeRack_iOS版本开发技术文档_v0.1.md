# VibeRack iOS 版本开发技术文档 v0.1

> 状态：iOS 立项开发稿  
> 当前日期：2026-06-16  
> 上游基线：`智能物料管理系统_项目技术文档_v1.0.md`、`智能底盘BLE接口规格_v0.1.md`、`CONTEXT.md`  
> 目标：在 iPhone 上实现 VibeRack 智能底盘 MVP，并与 Android、固件、未来云端共享同一套领域模型和硬件协议。

---

## 1. 文档目的

本文档用于启动 VibeRack iOS 版本开发，明确：

- iOS 版本的产品范围、MVP 验收标准和非目标。
- Swift/Apple 平台下的架构、模块划分和技术选型。
- Core Bluetooth、Core NFC 与现有智能底盘 BLE/NFC 协议的对接方式。
- 本地数据模型、硬件恢复、数字孪生、找料点灯、入库绑定、BOM 拣料的落地方案。
- Android 现有实现与 iOS 新实现之间应共享的领域规则和协议契约。

iOS 版本不是对 Legacy LCSC Android App 的功能复刻。它应直接服务 VibeRack 当前方向：智能底盘、统一容器模型、硬件绑定表、数字孪生、找料点灯、BOM 拣料和本地台账。

## 2. 产品定位

iOS App 是 VibeRack 系统中的移动端控制台。它负责：

- 通过 NFC Wake-Up 快速识别智能底盘。
- 通过 BLE 读取、写入和校验智能底盘绑定表。
- 在手机上展示容器/槽位数字孪生视图。
- 执行扫码入库、槽位绑定、找料点灯、BOM Pick-to-Light。
- 维护本地 Stock Ledger，并在硬件绑定表存在时以硬件为智能底盘槽位绑定的事实源。
- 在换机、重装或本地数据丢失后，从智能底盘 Binding Table 恢复最小可用库存。

一句话定义保持不变：手机里的货架和墙上的货架一起亮灯。

## 3. iOS MVP 范围

### 3.1 P0 必须完成

1. 智能底盘发现与连接
   - BLE 扫描 VibeRack 广播。
   - 解析 Manufacturer Data 中的 `proto_ver`、`batch_id`、`battery_pct`、`status_flags`、`table_seq`。
   - NFC 读取 `lcscerp://device?...` URI 后进入目标底盘连接流程。
   - 连接后读取 Battery Service、Device Information、Table Info。

2. 绑定表协议客户端
   - 实现 Binding Control Point 写入与 Notify 响应解析。
   - 支持 `READ_ONE`、`READ_ALL`、`WRITE_ONE`、`CLEAR_ONE`、`SET_QTY`。
   - `READ_ALL` 必须校验记录数、`slot_count` 和全表 CRC，失败不得落账。
   - 槽位记录必须按 16B 定长和 CRC-8/MAXIM 处理。

3. 灯控协议客户端
   - 实现 17B Light Command。
   - 支持 `OFF`、`FIND`、`PICK`、`STOCK_IN`。
   - 所有指令保持默认 MTU 23 可达，不依赖大 MTU。

4. 本地统一容器模型
   - `Container` 支持普通容器、收纳盒、智能底盘。
   - `Slot` 表示容器内编号位置。
   - `StockItem` 指向 `(containerId, slotNumber)`。
   - 智能底盘槽位绑定以硬件 Binding Table 为事实源，本地只保存缓存、富数据和台账视图。

5. 数字孪生只读版
   - 展示 25 槽智能底盘网格。
   - 显示空槽、已绑定组件、电量、缓存是否过期、低库存状态。
   - 点击槽位可发 `FIND` 或 `STOCK_IN`。
   - 广播 `table_seq` 与本地缓存不一致时提示刷新。

6. 入库绑定
   - 扫描或手动创建 Component。
   - 选择智能底盘和槽位。
   - 发 `STOCK_IN` 绿灯引导。
   - 用户确认后发 `WRITE_ONE`。
   - 收到成功响应后更新本地缓存和 Stock Ledger。

7. 从硬件恢复
   - 逐台 NFC 或 BLE 选择智能底盘。
   - 执行 `READ_ALL`。
   - 用 `part_id` 重建最小 Component 与 Stock Item。
   - 可后续通过 LCSC 或手动编辑做 Catalog Enrichment。

### 3.2 P1 应尽快完成

- BOM 导入和 BOM Match。
- 按智能底盘分组的 Pick-to-Light 掩码下发。
- 低库存阈值与数字孪生热力状态。
- Binding Drift 检测与修复提示。
- 本地备份导出/导入。

### 3.3 暂不追求

- 完整复刻 Android 旧版所有 ERP 功能。
- 多用户云同步和冲突合并。
- 整理模式完整交互和拖拽重排。
- 蓝牙打印功能。
- 共享取料坞计数/称重。
- iPad/macOS 多窗口深度适配。

## 4. 平台约束与关键差异

### 4.1 BLE：iOS 不暴露外设 MAC

Android 侧可以更直接地围绕 MAC 地址做定向连接，但 iOS Core Bluetooth 不向 App 暴露 BLE 外设真实 MAC 地址。现有 NFC URI 中的 `mac=AA:BB:...` 对 iOS 仍有价值，但不能直接作为 Core Bluetooth 连接句柄。

iOS MVP 连接策略：

1. NFC 读取 URI，得到 `batch`、`ver`、`mac` 等身份线索。
2. 启动 BLE 扫描，筛选 VibeRack Manufacturer Data。
3. 优先匹配 `proto_ver` 与 `batch_id`。
4. 连接候选外设后读取 Table Info / Device Information，并与本地 Smart Chassis Identity 记录校验。
5. 连接成功后把 iOS 的 `CBPeripheral.identifier` 作为本机缓存键，但不得把它当作跨设备、重装后稳定身份。

协议风险：

- 如果 `batch_id` 在用户仓库内不唯一，iOS NFC 后可能无法唯一定位目标底盘。
- 若需要稳定跨平台定向连接，建议在后续协议版本中增加硬件唯一 ID 的广播字段或 GATT 身份特征。任何 UUID、字段或 NDEF 语义变化都必须同步更新 `智能底盘BLE接口规格`。

### 4.2 NFC：优先读取 NDEF URI

iOS Core NFC 适合当前设计中的 NDEF URI 路由：`lcscerp://device?mac=...&batch=...&ver=1`。MVP 不使用 NFC 与硬件做 APDU 数据通道，所有绑定表和灯控仍走 BLE。

iOS 工程要求：

- 开启 Near Field Communication Tag Reading capability。
- 配置 NFC 使用说明文案。
- 使用 `NFCNDEFReaderSession` 读取 NDEF。
- NFC 扫描应由用户动作触发，读取后进入 Smart Chassis 连接页。

### 4.3 后台能力

iOS 可通过 Background Modes 声明 `bluetooth-central`，让系统在特定 BLE 事件上唤醒 App。但 VibeRack MVP 不应依赖长期后台扫描完成核心动作。

MVP 原则：

- 找料、入库、恢复、BOM 拣料都以用户前台操作为主。
- 点灯超时由固件保证，App 断连不应导致灯立刻熄灭。
- 后台 BLE 只作为连接恢复和状态通知的增强能力，不作为业务正确性的前提。

### 4.4 权限与真机要求

- Core Bluetooth、Core NFC 都必须在真机验证。
- NFC 功能依赖设备支持和开发者账号能力配置。
- App Store 审核时，后台蓝牙能力必须有明确、真实、可解释的 BLE 使用场景。

## 5. 技术选型

### 5.1 推荐栈

| 层 | 选型 | 说明 |
|---|---|---|
| UI | SwiftUI | 适合从零实现数字孪生、列表、表单、状态驱动 UI |
| 架构 | MVVM + Use Case / Service | 保持 UI -> ViewModel -> Repository/Service -> BLE/DB |
| 并发 | Swift Concurrency + AsyncStream | 将 BLE/NFC delegate 事件桥接为异步流 |
| 本地数据库 | SwiftData（P0）或 SQLite/GRDB（风险备选） | SwiftData 开发速度快；若复杂迁移/查询受限，再切 GRDB |
| 偏好设置 | UserDefaults / AppStorage | 保存轻量用户设置 |
| BLE | Core Bluetooth | CBCentralManager、CBPeripheral、CBCharacteristic |
| NFC | Core NFC | NFCNDEFReaderSession |
| 扫码 | AVFoundation | 扫描 LCSC 标签、二维码、条码 |
| 文件导入 | UniformTypeIdentifiers + FileImporter | BOM CSV/XLSX 后续引入 |
| 测试 | XCTest | 协议编解码、CRC、仓储逻辑、BOM 匹配优先做单测 |

### 5.2 SwiftData 使用边界

SwiftData 用于本地对象图和台账持久化。协议帧、CRC、BLE 连接状态不应直接绑在 SwiftData Model 上。

建议：

- `@Model` 只放持久化实体。
- 协议层使用纯 Swift `struct`。
- Repository 负责在协议模型和持久化模型之间转换。
- 所有硬件写操作先等 BLE 成功，再提交 SwiftData 事务。

如果后续出现以下问题，应评估切换或局部引入 SQLite/GRDB：

- 复杂查询性能不稳定。
- 数据迁移需求超出 SwiftData 当前能力。
- 需要与 Android Room schema 生成一致的跨平台数据库迁移资产。

## 6. iOS 工程结构建议

建议新建独立 iOS 工程，但仓库内文档和协议测试向 Android 仓库靠拢。若放在当前仓库，可使用：

```text
ios/
  VibeRack/
    VibeRackApp.swift
    App/
    Core/
      Bluetooth/
      NFC/
      Persistence/
      Protocol/
      Scanner/
      Sync/
      UI/
    Domain/
      Models/
      Repositories/
      UseCases/
    Features/
      Home/
      Chassis/
      Inbound/
      Search/
      BOM/
      Settings/
    Resources/
  VibeRackTests/
    ProtocolTests/
    RepositoryTests/
    BOMTests/
```

模块职责：

- `Core/Protocol`：智能底盘 BLE 协议编解码、CRC、opcode、light command。
- `Core/Bluetooth`：扫描、连接、服务发现、特征读写、Notify 订阅、重连策略。
- `Core/NFC`：NDEF URI 读取、解析、路由。
- `Core/Persistence`：SwiftData container、schema、migration、repository 实现。
- `Domain`：Component、Container、Slot、StockItem、StockOperation 等领域模型。
- `Features/Chassis`：智能底盘列表、数字孪生、槽位详情。
- `Features/Inbound`：扫码/手动入库与槽位绑定。
- `Features/Search`：组件搜索与 Find-by-Light。
- `Features/BOM`：BOM 导入、匹配和 Pick-to-Light。

## 7. 领域模型

### 7.1 核心实体

`Component`

- `id`
- `protocolPartId`
- `source`: catalog / custom
- `lcscPartNumber`
- `manufacturerPartNumber`
- `name`
- `package`
- `brand`
- `specSummary`
- `imageURL`
- `updatedAt`

`Container`

- `id`
- `type`: legacySingleSlot / storageBox / smartChassis
- `displayName`
- `slotCount`
- `createdAt`
- `updatedAt`

`SmartChassis`

- `containerId`
- `batchId`
- `protocolVersion`
- `advertisedName`
- `nfcMacHint`
- `iosPeripheralIdentifier`
- `batteryPercent`
- `statusFlags`
- `tableSeq`
- `tableCrc16`
- `firmwareVersion`
- `hardwareVersion`
- `lastSeenAt`
- `lastSyncedAt`

`StockItem`

- `id`
- `componentId`
- `containerId`
- `slotNumber`
- `quantity`
- `quantityState`: known / estimated / unknown
- `safetyStockThreshold`
- `flags`
- `updatedAt`

`StockOperation`

- `id`
- `type`: stockIn / bindSlot / setQuantity / findByLight / pickToLight / hardwareRestore / clearSlot
- `containerId`
- `slotNumber`
- `componentId`
- `quantityBefore`
- `quantityAfter`
- `hardwareOpCode`
- `hardwareTableSeqBefore`
- `hardwareTableSeqAfter`
- `createdAt`

### 7.2 智能底盘缓存原则

- 广播中的 `table_seq` 与本地 `tableSeq` 不一致时，本地 Smart Chassis Cache 视为可能过期。
- 对智能底盘槽位做写操作时，必须先通过 BLE 写硬件，成功后再更新本地 Stock Ledger。
- `READ_ALL` 恢复得到的是最小绑定集，不代表组件富数据完整。
- Binding Drift 用 `table_seq`、`crc16`、`READ_ALL` 解决，不通过本地强行覆盖硬件解决。

## 8. 协议层设计

### 8.1 类型定义

协议层使用纯 Swift 值类型，避免 UI 和数据库依赖：

```swift
struct SlotRecord: Equatable {
    var slot: UInt8
    var partId: ProtocolPartID
    var quantity: UInt16
    var flags: UInt8
    var reserved: UInt8
    var crc8: UInt8
}

struct TableInfo: Equatable {
    var tableSeq: UInt32
    var crc16: UInt16
    var slotCount: UInt8
}

enum BindingOpcode: UInt8 {
    case readOne = 0x01
    case readAll = 0x02
    case writeOne = 0x10
    case clearOne = 0x11
    case insertAt = 0x20
    case removeAt = 0x21
    case moveBlock = 0x22
    case setQuantity = 0x30
    case factoryReset = 0xF0
}
```

### 8.2 必测协议行为

- Manufacturer Data 小端解析。
- 16B Slot Record 编码/解码。
- `part_id` ASCII 不足补 `0x00`。
- CRC-8/MAXIM。
- CRC-16/CCITT-FALSE。
- Light Command 17B 布局。
- `READ_ALL` 结束帧 `payload=0xFF`。
- status 错误码映射。
- 默认 MTU 下 `WRITE_ONE`、`SET_QTY`、Light Command 不拆包。

### 8.3 错误处理

| 错误 | iOS 处理 |
|---|---|
| `ERR_PARAM` | 停止流程，提示协议参数错误，记录诊断日志 |
| `ERR_FULL` | 提示末槽非空或目标无空位，不更新本地 |
| `ERR_FLASH_BUSY` | 退避 100ms 重试，最多 3 次 |
| `ERR_CRC` | 重新编码记录并重试一次，仍失败则停止 |
| Notify 超时 | 断开后允许用户重试，不更新本地 |
| CRC16 校验失败 | 丢弃本次 `READ_ALL` 结果，提示重新读取 |
| `proto_ver` 高于支持版本 | 提示升级 App |
| `proto_ver` 低于支持版本 | 提示升级固件 |

## 9. BLE 连接流程

### 9.1 扫描列表

1. 创建 `CBCentralManager`。
2. 等待 `poweredOn`。
3. 扫描所有外设或按服务 UUID 扫描。
4. 从 advertisement data 中读取 Manufacturer Data。
5. 解析出 VibeRack 广播核心字段。
6. 更新 Smart Chassis List：
   - 名称：`VBRK-XXXX` 或本地别名。
   - 电量。
   - 是否低电量。
   - 是否存在未绑定槽位。
   - 是否正在点灯。
   - 本地缓存是否过期。

### 9.2 NFC 定向连接

1. 用户点击 NFC 扫描按钮。
2. `NFCNDEFReaderSession` 读取 URI。
3. 解析 `batch`、`ver`、`mac`。
4. 如果本地已有 `batchId` 对应智能底盘，进入该底盘页并开始扫描。
5. 从扫描结果中找 `batch_id` 匹配的外设。
6. 连接并读取 Table Info。
7. 若 `table_seq` 与本地不同，提示刷新或自动 `READ_ALL`。

### 9.3 GATT 初始化

连接成功后：

1. Discover Binding Table Service、Light Service、BAS、DIS。
2. 订阅 Binding Control Point Notify。
3. 订阅 Table Info Notify。
4. 订阅 Light Status Notify。
5. 读取 Table Info。
6. 读取 Battery Level。
7. 读取 Firmware Revision / Hardware Revision。
8. 将设备状态发布给 ViewModel。

### 9.4 写操作事务

所有硬件写入使用统一事务：

1. 读取当前 Table Info。
2. 生成协议命令。
3. 写入 Control Point 或 Light Command。
4. 等待 Notify 或状态确认。
5. 成功后更新本地缓存和 Stock Operation。
6. 失败则保持本地不变。
7. 关键绑定变化后可追加 `READ_ALL` 校验。

## 10. NFC 路由设计

URI：

```text
lcscerp://device?mac=AA:BB:CC:DD:EE:FF&batch=1001&ver=1
```

iOS 路由结果：

```text
SmartChassisRoute(
  batchId: 1001,
  protocolVersion: 1,
  macHint: "AA:BB:CC:DD:EE:FF"
)
```

处理规则：

- `ver` 高于 App 支持版本：提示升级 App。
- URI 缺少 `batch`：提示标签不完整。
- 读取成功但 BLE 未发现：显示“底盘未广播或距离过远”，提供重试。
- 发现多个同 `batch_id` 外设：要求用户选择，并记录为协议风险。

## 11. 主要功能设计

### 11.1 首页

首页优先展示智能底盘和高频动作：

- 顶部：NFC 扫描、扫码入库、搜索。
- 主体：智能底盘列表。
- 每个底盘项显示电量、25 槽占用概况、低库存数量、缓存状态。
- 底部或次级入口：BOM、库存、设置。

首页不做营销页，不展示大段说明文案。用户打开 App 应直接进入可操作的库存工作台。

### 11.2 数字孪生视图

25 槽网格建议使用 5 x 5 固定布局，视觉上对应智能底盘。每槽稳定尺寸，避免因长文本撑开。

槽位状态：

- 空槽：低对比边框。
- 已绑定：显示槽号、短料号、数量。
- 低库存：红色状态标记。
- 缓存过期：顶部提示需要刷新，不直接把本地缓存当真。
- 正在点灯：槽位显示活动状态。

槽位操作：

- 空槽：入库到此槽、点绿灯引导。
- 已绑定：找料点灯、改数量、清空槽位、查看组件详情。
- 缓存过期：优先提示读取硬件。

### 11.3 入库绑定

流程：

1. 扫描 LCSC 标签或手动创建组件。
2. 解析 `pc`、`qty` 等字段。
3. 选择目标智能底盘。
4. 选择或推荐空槽。
5. 发 `STOCK_IN` 灯控。
6. 用户放入 passive bin 后确认。
7. 编码 Slot Record 并发 `WRITE_ONE`。
8. 成功后写本地 Stock Item 和 Stock Operation。

失败处理：

- 硬件写失败：不得创建本地智能底盘绑定。
- 本地保存失败：提示重新同步，允许 `READ_ONE/READ_ALL` 从硬件恢复。
- 槽位已被其他设备占用：用 `READ_ONE` 刷新该槽后提示用户重新选择。

### 11.4 搜索与 Find-by-Light

搜索目标：

- LCSC 料号。
- 厂商料号。
- 名称/规格。
- 封装。
- 自定义组件备注。

命中智能底盘 Stock Item 后：

1. 若本地 `tableSeq` 过期，先提示刷新。
2. 连接目标底盘。
3. 发 `FIND`，`mask_a` 只包含目标槽。
4. 用户取料后可更新数量，走 `SET_QTY`。

### 11.5 BOM Pick-to-Light

MVP 后第一阶段：

1. 导入 BOM。
2. 执行 BOM Match。
3. 按智能底盘分组。
4. 每台底盘生成 25-bit 掩码。
5. 发 `PICK`。
6. 用户勾选完成项后重发剩余掩码。
7. 全部完成后发 `OFF`。

Pick-to-Light 不直接等同于数量扣减。是否扣减应由用户确认或后续 Smart Dock 计数结果驱动。

### 11.6 从硬件恢复

流程：

1. 设置页进入“从智能底盘恢复”。
2. 扫描或 NFC 选择一台底盘。
3. 连接后执行 `READ_ALL`。
4. 校验 25 条记录、`slot_count` 和 CRC16。
5. 对非空记录创建/更新：
   - Component 最小记录。
   - Smart Chassis Container。
   - Stock Item。
   - Hardware Restore Stock Operation。
6. 重复下一台。
7. 恢复完成后显示缺少富数据的组件列表，引导联网补全或手动编辑。

恢复原则：

- 硬件恢复只恢复智能底盘 binding table 的最小事实。
- 不恢复安全库存阈值、图片、历史操作日志等 App/云端富数据。
- 如果本地已有更完整 Component，保留富数据，只校准槽位和数量。

## 12. 测试策略

### 12.1 单元测试优先级

P0 必须覆盖：

- Manufacturer Data 解析。
- NDEF URI 解析。
- Slot Record 编解码。
- CRC-8/MAXIM。
- CRC-16/CCITT-FALSE。
- Light Command 编码。
- `READ_ALL` Notify 聚合与结束帧处理。
- opcode status 映射。
- 硬件写成功后才落本地账。
- `table_seq` 不一致时标记缓存过期。

### 12.2 模拟器/Mock

iOS Simulator 无法完整验证 BLE/NFC 真机行为。需要同时准备：

- 协议层纯单测。
- `ChassisSimulator`：在 App 内或 macOS 辅助工具中模拟协议响应。
- 真机 + 固件板联调。

模拟器只用于 UI 和数据流验证，不作为 BLE/NFC 验收依据。

### 12.3 真机验收用例

1. 扫描发现底盘，显示电量和 `table_seq`。
2. NFC 碰一下进入对应底盘页。
3. `READ_ALL` 读取 25 槽并校验通过。
4. 空槽入库：绿灯引导、`WRITE_ONE` 成功、本地出现 stock item。
5. 搜索组件后 `FIND` 点亮正确槽。
6. `SET_QTY` 更新数量，重读后数量一致。
7. 删除 App 本地数据后，从硬件恢复成功。
8. BOM 生成掩码并执行 `PICK`。
9. 手机断开 BLE 后，灯按固件 timeout 自动熄灭。

## 13. 里程碑

### M0：工程骨架

- 创建 SwiftUI iOS 工程。
- 建立 Domain、Protocol、Bluetooth、NFC、Persistence 分层。
- 完成协议常量、CRC、Slot Record、Light Command 单测。

### M1：发现与连接

- BLE 扫描并解析广播。
- NFC URI 读取和路由。
- 连接目标底盘并读取 Table Info / Battery / DIS。
- 首页显示智能底盘列表。

### M2：绑定表闭环

- `READ_ALL` 完成。
- `WRITE_ONE`、`CLEAR_ONE`、`SET_QTY` 完成。
- 本地 SwiftData 模型可保存 Container、SmartChassis、Component、StockItem。
- 数字孪生只读版完成。

### M3：核心工作流 Demo

- 扫码/手动入库到智能底盘。
- Find-by-Light。
- 从硬件恢复。
- 基础搜索。

### M4：BOM Pick-to-Light

- BOM 导入和匹配。
- 按底盘生成 PICK 掩码。
- 逐项完成后更新掩码。

### M5：打磨与发布准备

- 错误处理、权限文案、空状态、诊断日志。
- 真机兼容性测试。
- App Store capability 与隐私说明检查。

## 14. 风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| iOS 不暴露 BLE MAC，NFC URI 中 MAC 不能直接连接 | 高 | 用 `batch_id` + 广播 + 连接后校验；若不够唯一，升级协议增加稳定硬件 ID |
| SwiftData 迁移或复杂查询后期受限 | 中 | P0 先用 SwiftData 提速；协议层和领域层保持纯 Swift，必要时替换 Persistence 实现 |
| NFC 能力配置和真机限制导致开发受阻 | 中 | 早期就配置 capability，用真实 iPhone 验证；不要把 NFC 作为唯一入口 |
| 后台 BLE 体验不稳定 | 中 | MVP 前台操作优先，固件负责点灯超时；后台只做增强 |
| 多手机同时修改导致 Binding Drift | 中 | P0 单机为主；所有写前后读取 `table_seq`，发现变更则要求刷新 |
| 本地台账与硬件绑定不一致 | 高 | 硬件写成功后才落账；`READ_ALL` + CRC 作为修复入口 |
| BOM 拣料误扣库存 | 中 | Pick-to-Light 与扣减分离，扣减需用户确认或后续 Smart Dock 数据 |

## 15. 与 Android 版本的协作边界

必须共享：

- 领域语言：Component、Container、Slot、Stock Item、Smart Chassis、Binding Table、Hardware Restore。
- BLE/NFC 协议常量和二进制布局。
- CRC 算法与测试向量。
- BOM Match 规则。
- 低库存、数量状态、硬件恢复语义。

可以不同：

- UI 交互细节。
- 本地数据库实现。
- 蓝牙连接内部状态机。
- 平台权限与后台策略。

不允许不同：

- Slot Record 二进制布局。
- opcode 语义。
- Light Command 17B 布局。
- `table_seq` / CRC 校验规则。
- 智能底盘硬件作为绑定事实源的原则。

## 16. 官方资料核对

本稿涉及 Apple 平台能力时参考了以下官方资料：

- Core Bluetooth 后台处理：<https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html>
- Core Bluetooth：<https://developer.apple.com/documentation/corebluetooth/>
- Core NFC：<https://developer.apple.com/documentation/corenfc>
- NFC NDEF Reader Session：<https://developer.apple.com/documentation/corenfc/nfcndefreadersession>
- SwiftData：<https://developer.apple.com/documentation/swiftdata>
- SwiftData 持久化模型：<https://developer.apple.com/documentation/swiftdata/preserving-your-apps-model-data-across-launches>

## 17. 下一步建议

1. 先创建 `ios/` SwiftUI 工程，完成 `Core/Protocol` 和单元测试。
2. 与固件确认 `batch_id` 是否能作为用户仓库内唯一智能底盘识别字段。
3. 准备一套协议测试向量，Android、iOS、固件三端共用。
4. 在 iPhone 真机上尽早验证 NFC URI 读取与 BLE 广播解析。
5. M1 完成后再进入数字孪生 UI 和入库绑定，避免 UI 先行但硬件链路不可用。

