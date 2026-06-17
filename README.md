<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description:
 * @note:
 * @version: 0.1.0
-->
# VibeRack

VibeRack 是面向创客、电子实验室和小型研发团队的智能物料管理系统。这个仓库是当前唯一主仓库，包含 Android App、iOS App、共享领域模型文档和智能底盘协议文档。

当前开发方向不是旧 LCSC/ERP 功能复刻。旧代码只作为实现来源之一；当旧库位、盒层、标签、NFC 或库存流程与当前智能底盘协议和 VibeRack 领域模型冲突时，优先按协议优先方向迁移、替换或删除。

当前产品与协议设计以两份新文档为准：

- [智能物料管理系统 项目技术文档 v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)：产品定位、VibeRack 系统架构、硬件/固件/APP 改造优先级、统一容器模型、算法、路线图和风险
- [智能底盘 BLE 接口规格 v0.1](./docs/智能底盘BLE接口规格_v0.1.md)：智能底盘 BLE/NFC 广播、GATT 服务、槽位绑定表记录、灯控指令、安全规则和端到端硬件流程

## 当前功能概览

- Android 当前主导航：Home / Containers / Search / Settings
- Android 智能底盘：BLE 扫描、NFC 设备 URI 路由、25 槽数字孪生、找料点灯、STOCK_IN 入库引导、READ_ALL 恢复预览
- Android 搜索：库存搜索、BOM 匹配、智能底盘 find-by-light、BOM pick-to-light、搜索结果绑定到智能底盘空槽
- 统一容器/槽位模型：普通库位是 1 槽容器，智能底盘是 25 槽 BLE/NFC 容器
- iOS 工程：`ios/VibeRack.xcodeproj`，包含 SwiftUI App、BLE/协议层、GRDB 本地账本、数字孪生、入库、搜索、BOM、设置和 XCTest
- BOM 拣料点灯：导入 Excel BOM，匹配本地库存账并按智能底盘槽位点亮待取组件
- 硬件恢复：读取智能底盘绑定表，先恢复最小槽位绑定，再用 App 或网络目录数据增强组件详情

## 协议优先规则

- 智能底盘的槽位绑定以硬件绑定表为准，App 侧缓存不是绑定来源
- App 侧 `stock_item`、`stock_operation`、`container.tableSeq` 和 `container.tableCrc16` 必须跟随成功 BLE 操作，或跟随经过 `table_seq`/CRC 校验的读表结果
- 不要在 App 侧独立重编号智能底盘槽位；插入、移除和移动槽位应通过硬件绑定表操作完成
- 目录增强只补充组件详情，不定义槽位绑定或库存数量
- 标签打印是辅助工作流，不能替代智能底盘身份或硬件绑定关系

## 技术栈

- Android：Kotlin 2.2、AGP 9.2、Jetpack Compose + Material 3、Room + SQLite、DataStore、Retrofit/OkHttp/Jsoup、CameraX、ML Kit、Coil、ZXing、Apache POI、BLE/NFC
- iOS：SwiftUI、CoreBluetooth、CoreNFC、GRDB/SQLite、XCTest
- 共享规则：协议和领域语义以 `CONTEXT.md` 与 `docs/` 下当前产品/协议文档为准

本地核心数据使用 `Room + SQLite` 持久化；轻量配置与偏好使用 `DataStore`。智能底盘协议层包含 BLE 扫描、GATT 客户端、协议编解码、灯控、读表恢复和 fake chassis 测试替身。

## 环境要求

- Android Studio 最新稳定版
- JDK 17
- Android SDK 36
- 设备 / 模拟器 Android 11 及以上
- Xcode（开发 iOS 时使用）

## 快速开始

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

如需在设备上运行，请在 Android Studio 中打开仓库根目录并运行 `app` 配置。

## 目录结构

```text
app/          Android App 模块，Compose UI、Room、BLE/NFC、数据层
domain/       Android 共享领域模块，模型和仓储接口
ios/          iOS SwiftUI 工程和 XCTest
assets/       样例数据，例如 BOM xlsx
docs/         产品、协议、iOS 计划、硬件对接和 agent 文档
app/schemas/  Android Room schema 导出
```

## 文档

- [CONTEXT.md](./CONTEXT.md)：当前 VibeRack 领域语言和术语边界
- [智能物料管理系统 项目技术文档 v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)
- [智能底盘 BLE 接口规格 v0.1](./docs/智能底盘BLE接口规格_v0.1.md)
- [VibeRack iOS 版本开发技术文档 v0.1](./docs/VibeRack_iOS版本开发技术文档_v0.1.md)
- [VibeRack iOS 开发执行计划 v0.2](./docs/VibeRack_iOS开发执行计划_v0.2.md)

当 `docs/superpowers/` 下旧记录与 `CONTEXT.md` 或上述两份当前设计文档冲突时，以当前设计文档和智能底盘 BLE 规格为准。

## 开源协议

本项目采用 `GNU General Public License v3.0`（`GPLv3`）。

- 许可证全文见 [LICENSE](./LICENSE)
- 如分发修改版本，请遵循 GPLv3 对源代码公开与同协议传播的要求

## 当前说明

- 应用固定竖屏
- 网络用于目录组件查询和后续目录增强
- 库存账、容器缓存、智能底盘缓存、图片缓存、语言偏好均保存在本地
- Android 当前为 `:app` + `:domain` 两个 Gradle 模块
- iOS 当前位于 `ios/`，在 macOS/Xcode 环境验证
- 快速开发阶段不追求旧 LCSC 应用功能等价；优先证明智能底盘 MVP：识别底盘、绑定组件到槽位、数字孪生视图、入库引导、找料点灯、从硬件恢复绑定表
