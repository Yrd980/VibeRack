<!--
 * @author: BC
 * @date: 26/04/06
 * @lastEditTime: 26/04/24
 * @description: 
 * @note: 
 * @version: 0.1.0
-->
# LCSC Android ERP

面向立创商城物料管理场景的 Android 原生应用。项目已从“小型离线仓储”工具演进为智能物料管理系统的 APP 端：保留扫码入库、手动入库、库位/盒层管理、库存检索、BOM 匹配、二维码/NFC/蓝牙打印、本地备份等基础能力，并按 VibeRack 智能底盘方案演进到“扫码入库 → NFC 碰一下 → 找料点灯 → BOM 拣料 → 库存台账”。

当前产品与协议设计以两份新文档为准：

- [智能物料管理系统 项目技术文档 v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)：产品定位、系统架构、硬件/固件/APP 改造优先级、核心算法和路线图
- [智能底盘 BLE 接口规格 v0.1](./docs/智能底盘BLE接口规格_v0.1.md)：智能底盘 BLE/NFC 广播、GATT 服务、绑定表记录、灯控指令和安全约束

## 功能概览

- 扫码入库：识别立创二维码，提取 `pc` 编号并查询物料信息
- 手动入库：按关键词搜索立创商城物料并选择入库
- 库位/盒层管理：维护传统库位、组件盒与层位，当前代码已包含 Boxes/BoxLayer 数据与 UI 切片
- 库存查看：按库位查看物料列表、详情、数量修改、转移与删除
- BOM 搜索：导入 Excel BOM，查看已匹配 / 未匹配项并支持直接入库
- 标签与打印：生成物料/层位标签，支持已验证的 P0/印立方窄标签打印路径
- NFC：支持应用内 NFC 标签 payload 读写，并规划接入智能底盘设备 URI
- 智能底盘方向：规划 25 槽 BLE 底盘、NFC 碰醒、找料点灯、BOM 拣料、多槽位绑定表和从硬件恢复
- 导入导出：支持库存 Excel 备份与恢复
- 多语言：支持中文 / English 切换

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

本地核心数据使用 `Room + SQLite` 持久化；轻量配置与偏好使用 `DataStore`。

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
app/src/main/java/com/example/lcsc_android_erp/
  core/       数据库、DataStore、网络、国际化、共享 UI
  data/       Repository、远程抓取、备份与图片持久化
  domain/     业务模型与仓储接口
  feature/    home / inbound / inventory / search / settings
  ui/         应用壳与主题

docs/         设计与规划文档
log/          真机日志导出
app/schemas/  Room schema 导出
```

## 文档

- [智能物料管理系统 项目技术文档 v1.0](./docs/智能物料管理系统_项目技术文档_v1.0.md)
- [智能底盘 BLE 接口规格 v0.1](./docs/智能底盘BLE接口规格_v0.1.md)
- [P0 打印、NFC 与 BOM 联调记录](./docs/superpowers/specs/2026-06-10-printer-nfc-bom-session-findings.md)

## 开源协议

本项目采用 `GNU General Public License v3.0`（`GPLv3`）。

- 许可证全文见 [LICENSE](./LICENSE)
- 如分发修改版本，请遵循 GPLv3 对源代码公开与同协议传播的要求

## 当前说明

- 应用固定竖屏
- 网络用于查询立创商城物料信息
- 库存、库位、图片缓存、语言偏好均保存在本地
- 项目当前为单模块 `app`，按包分层组织
- 后续 APP 改造优先对齐统一容器模型：普通库位是 1 槽容器，收纳盒是 N 层容器，智能底盘是 25 槽 BLE/NFC 容器
- `docs/superpowers/` 下仅保留仍有价值的 P0 打印、NFC 与 BOM 联调记录；若与上述两份设计文档冲突，以新设计文档为准
