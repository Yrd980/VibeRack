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

面向立创商城物料管理场景的 Android 原生应用。项目聚焦“小型离线仓储”流程，支持扫码入库、手动入库、库位管理、库存检索、BOM 匹配、二维码打印，以及本地库存导入导出。

## 功能概览

- 扫码入库：识别立创二维码，提取 `pc` 编号并查询物料信息
- 手动入库：按关键词搜索立创商城物料并选择入库
- 库位管理：维护库位编号、名称、颜色、排序方式
- 库存查看：按库位查看物料列表、详情、数量修改、转移与删除
- BOM 搜索：导入 Excel BOM，查看已匹配 / 未匹配项并支持直接入库
- 二维码打印：生成物料二维码预览并保存到系统相册
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

- [技术规划](./docs/AIGC_project.md)
- [弹窗整理](./docs/AIGC_Popup.md)
- [组件整理](./docs/AIGC_component.md)
- [需求原始说明](./docs/project.md)

## 开源协议

本项目采用 `GNU General Public License v3.0`（`GPLv3`）。

- 许可证全文见 [LICENSE](./LICENSE)
- 如分发修改版本，请遵循 GPLv3 对源代码公开与同协议传播的要求

## 当前说明

- 应用固定竖屏
- 网络用于查询立创商城物料信息
- 库存、库位、图片缓存、语言偏好均保存在本地
- 项目当前为单模块 `app`，按包分层组织
