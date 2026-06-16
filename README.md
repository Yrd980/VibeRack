<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description:
 * @note:
 * @version: 0.1.0
-->
# VibeRack

VibeRack 是面向创客、电子实验室和小型研发团队的智能物料管理 Android 应用。它把移动端、智能底盘、被动料盒和库存账组合起来，围绕组件入库、NFC 唤醒、数字孪生槽位、找料点灯、BOM 拣料点灯、库存账和硬件恢复构建工作流。

本仓库继承自一个非官方 LCSC 取向 Android 应用。旧代码是可复用的实现基础，不是兼容性约束；当旧库位、盒层、标签、NFC 或库存流程与当前智能底盘协议和 VibeRack 领域模型冲突时，优先按协议优先方向迁移、替换或删除。

当前产品与协议设计以两份新文档为准：

- [智能物料管理系统 项目技术文档 v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)：产品定位、VibeRack 系统架构、硬件/固件/APP 改造优先级、统一容器模型、算法、路线图和风险
- [智能底盘 BLE 接口规格 v0.1](./docs/智能底盘BLE接口规格_v0.1.md)：智能底盘 BLE/NFC 广播、GATT 服务、槽位绑定表记录、灯控指令、安全规则和端到端硬件流程

## 功能概览

- 组件入库：识别立创二维码或手动搜索目录组件，提取可恢复的组件标识、数量和目录信息
- 智能底盘入库引导：选择底盘与槽位，点亮 `STOCK_IN` 引导，确认后通过 BLE `WRITE_ONE` 写入硬件绑定表，再更新 App 侧库存账
- 统一容器/槽位模型：旧库位是 1 槽容器，收纳盒是 N 槽容器，智能底盘是 25 槽 BLE/NFC 容器
- 数字孪生视图：查看智能底盘 25 个槽位、占用状态、低库存信号、激活灯光、`table_seq`、CRC 和缓存新鲜度
- 找料点灯：从搜索、扫码或槽位选择触发智能底盘物理引导
- BOM 拣料点灯：导入 Excel BOM，匹配本地库存账并按智能底盘槽位点亮待取组件
- 硬件恢复：读取一个或多个智能底盘的绑定表，先恢复最小槽位绑定，再用 App 或网络目录数据增强组件详情
- 库存账：记录组件、容器槽位、库存项、数量状态和库存操作，支持搜索、BOM 匹配、盘点与数量校正
- 标签与打印：保留已验证的 P0/印立方 10 mm 旋转窄标签打印路径，作为非智能存储和辅助标识工作流
- NFC：支持应用内 NFC payload 读写，并接入智能底盘设备 URI 进行唤醒、识别和恢复入口路由
- 导入导出：支持库存账 Excel 备份与恢复
- 多语言：支持中文 / English 切换

## 协议优先规则

- 智能底盘的槽位绑定以硬件绑定表为准，App 侧缓存不是绑定来源
- App 侧 `stock_item`、`stock_operation`、`container.tableSeq` 和 `container.tableCrc16` 必须跟随成功 BLE 操作，或跟随经过 `table_seq`/CRC 校验的读表结果
- 不要在 App 侧独立重编号智能底盘槽位；插入、移除和移动槽位应通过硬件绑定表操作完成
- 目录增强只补充组件详情，不定义槽位绑定或库存数量
- 标签打印是辅助工作流，不能替代智能底盘身份或硬件绑定关系

## 技术栈

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

本地核心数据使用 `Room + SQLite` 持久化；轻量配置与偏好使用 `DataStore`。智能底盘协议层包含 BLE 扫描、GATT 客户端、协议编解码、灯控、读表恢复和 fake chassis 测试替身。

## 环境要求

- Android Studio 最新稳定版
- JDK 11
- Android SDK 36
- 设备 / 模拟器 Android 11 及以上

## 快速开始

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

如需在设备上运行，请在 Android Studio 中打开仓库根目录并运行 `app` 配置。

## 目录结构

```text
app/src/main/java/com/viberack/app/
  core/       数据库、DataStore、网络、BLE/NFC、国际化、共享 UI
  data/       Repository、远程抓取、备份、图片持久化、协议应用服务
  domain/     业务模型与仓储接口
  feature/    home / inbound / inventory / search / containers / boxes / settings
  ui/         应用壳与主题

docs/         产品、协议、规划与 agent 文档
hardware/     硬件相关资料
log/          真机日志导出
app/schemas/  Room schema 导出
```

## 文档

- [CONTEXT.md](./CONTEXT.md)：当前 VibeRack 领域语言和术语边界
- [智能物料管理系统 项目技术文档 v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)
- [智能底盘 BLE 接口规格 v0.1](./docs/智能底盘BLE接口规格_v0.1.md)
- [P0 打印、NFC 与 BOM 联调记录](./docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md)

当 `docs/superpowers/` 下旧记录与 `CONTEXT.md` 或上述两份当前设计文档冲突时，以当前设计文档和智能底盘 BLE 规格为准。

## 开源协议

本项目采用 `GNU General Public License v3.0`（`GPLv3`）。

- 许可证全文见 [LICENSE](./LICENSE)
- 如分发修改版本，请遵循 GPLv3 对源代码公开与同协议传播的要求

## 当前说明

- 应用固定竖屏
- 网络用于目录组件查询和后续目录增强
- 库存账、容器缓存、智能底盘缓存、图片缓存、语言偏好均保存在本地
- 项目当前为单模块 `app`，按包分层组织
- 快速开发阶段不追求旧 LCSC 应用功能等价；优先证明智能底盘 MVP：识别底盘、绑定组件到槽位、数字孪生视图、入库引导、找料点灯、从硬件恢复绑定表
