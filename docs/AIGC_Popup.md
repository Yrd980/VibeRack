# 弹窗清单

本文档基于当前代码现状整理应用内已经落地的弹窗类型，以及各自出现的场景。

## 0. 当前复用骨架

当前弹窗已经明确收敛为两层共享能力：

### 0.1 物料详情骨架

- 组件：`ComponentInfoDialog`
- 文件：`app/src/main/java/com/example/lcsc_android_erp/core/ui/ComponentInfoDialog.kt`
- 作用：统一承载物料图片、第一属性、第二属性、滚动布局、长按复制与震动反馈

当前已接入：

- `InventoryItemManageDialog`

### 0.2 仓库选择统一弹窗

- 组件：`LocationPickerDialog`
- 文件：`app/src/main/java/com/example/lcsc_android_erp/core/ui/LocationPickerDialog.kt`
- 作用：统一承载“选择仓库位置 / 选择目标仓库”的整套弹窗实现，不只是样式骨架

当前已接入：

- `MaterialInboundDialog` 内部的“选择仓库位置”
- `InventoryItemManageDialog` 内部的“转移仓库”
- 库位多选后的“批量转移”

## 1. 物料入库确认弹窗

- 组件：`MaterialInboundDialog`
- 文件：`app/src/main/java/com/example/lcsc_android_erp/feature/inbound/MaterialInboundDialog.kt`
- 作用：统一承载“物料确认入库”的主弹窗

当前已接入的场景：

| 场景 | 入口页面 | 触发方式 | 备注 |
| --- | --- | --- | --- |
| 手动入库确认 | 入库页 `手动入库` | 搜索结果点击“选择并入库” | 数量可编辑 |
| 扫码入库确认 | 入库页 `扫码入库` | 扫码成功并查到立创物料后 | 数量通常来自二维码，只读 |
| BOM 直接入库 | 搜索页 `BOM搜索` | 未匹配项点击“直接入库” | 先联网查料号，再进入确认弹窗 |
| 仓库内扫码添加确认 | 库位物料页 | 点击右下角 `+` 后扫码，查询成功进入确认 | 固定入到当前仓库 |

统一能力：

- 展示物料图片
- 展示第一属性、第二属性
- 展示已有库存提醒
- 支持仓库位置选择
- 支持数量输入或只读数量
- 支持网络请求中的加载转圈

### 1.1 物料入库中的“选择仓库位置”子弹窗

- 组件：`LocationPickerDialog`
- 文件：`app/src/main/java/com/example/lcsc_android_erp/core/ui/LocationPickerDialog.kt`
- 出现场景：
  - 手动入库确认
  - 扫码入库确认
  - BOM 直接入库确认
  - 仓库内扫码添加确认
- 备注：
  - 仓库名称会根据背景色自动切换黑字/白字
  - 某一行仓库过多时支持横向滑动

## 2. BOM 绑定本地物料弹窗

| 项目 | 内容 |
| --- | --- |
| 组件 | `BomBindingDialog` |
| 文件 | `app/src/main/java/com/example/lcsc_android_erp/feature/search/SearchScreen.kt` |
| 场景 | 搜索页 `BOM搜索`，未匹配项点击“绑定匹配” |
| 作用 | 从本地库存中选择一个物料进行绑定 |
| 备注 | 有料号时做持久化绑定，无料号时做当前会话内临时绑定 |

## 3. 新增库位弹窗

| 项目 | 内容 |
| --- | --- |
| 组件 | `AddLocationDialog` |
| 文件 | `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryScreen.kt` |
| 场景 | 库存总览页点击右下角 `+` |
| 作用 | 新建库位，设置编码、名称、颜色 |
| 备注 | 内部可继续打开颜色选择弹窗 |

## 4. 仓库设置弹窗

| 项目 | 内容 |
| --- | --- |
| 组件 | `LocationSettingsDialog` |
| 文件 | `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryScreen.kt` |
| 场景 | 长按库位卡片，或进入库位详情后点击右上角齿轮 |
| 作用 | 修改仓库编号、名称、颜色、排序方式 |
| 备注 | 内部可继续打开颜色选择弹窗，也可能继续弹出“无法删除仓库”确认弹窗 |

### 4.1 颜色选择弹窗

- 组件：`ColorWheelDialog`
- 场景：
  - 新增库位时点击颜色加号
  - 仓库设置时点击颜色加号
- 作用：通过色盘选择自定义颜色

### 4.2 无法删除仓库确认弹窗

- 位置：`LocationSettingsDialog` 内部
- 场景：删除仍有物料的仓库时触发
- 作用：提示当前不能直接删除，并提供“强制删除”

## 5. 仓库内扫码添加壳弹窗

| 项目 | 内容 |
| --- | --- |
| 组件 | `LocationScanAddDialog` |
| 文件 | `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryScreen.kt` |
| 场景 | 进入某个仓库详情页后，点击右下角 `+` |
| 作用 | 提供仓库内扫码入口 |
| 备注 | 当扫码后进入“查询中 / 查询成功 / 查询失败”状态时，会切换到统一的物料入库确认弹窗 |

## 6. 库位物料管理弹窗

| 项目 | 内容 |
| --- | --- |
| 组件 | `InventoryItemManageDialog` |
| 文件 | `app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryItemManageDialog.kt` |
| 场景 | 点击库位详情中的某个物料；点击搜索结果中的某一条具体库存记录 |
| 作用 | 查看物料详情，修改数量，转移仓库，删除物料 |
| 备注 | 弹窗主体复用 `ComponentInfoDialog`，内部继续复用 `LocationPickerDialog` |

### 6.1 单个物料转移仓库弹窗

- 组件：`LocationPickerDialog`
- 场景：点击“转移仓库”
- 作用：给当前物料选择目标仓库
- 备注：与入库弹窗中的“选择仓库位置”完全复用同一实现

## 7. 库位多选批量操作弹窗

### 7.1 批量转移弹窗

- 组件：`LocationPickerDialog`
- 文件：`app/src/main/java/com/example/lcsc_android_erp/core/ui/LocationPickerDialog.kt`
- 场景：库位详情页长按物料进入多选后，点击“批量转移”
- 作用：给一组已选物料选择目标仓库
- 备注：与单个物料“转移仓库”、入库弹窗“选择仓库位置”完全复用同一实现

### 7.2 批量删除确认弹窗

- 文件：`app/src/main/java/com/example/lcsc_android_erp/feature/inventory/InventoryScreen.kt`
- 场景：库位详情页长按物料进入多选后，点击“批量删除”
- 作用：确认是否删除当前选中的物料

## 8. 设置页弹窗

### 8.1 语言切换弹窗

- 组件：`LanguageDialog`
- 场景：设置页点击“语言”
- 作用：切换中文 / 英文

### 8.2 关于弹窗

- 组件：`AboutDialog`
- 场景：设置页点击“关于”
- 作用：展示应用名称、版本、说明、技术栈

### 8.3 导入导出结果提示弹窗

- 场景：设置页执行库存导入 / 导出后
- 作用：展示结果消息
- 备注：使用通用 `AlertDialog`，文案由 `inventoryBackupMessage` 驱动

## 当前结构总结

从产品语义上看，当前弹窗可以分为 5 组：

1. 物料入库类：统一收敛到 `MaterialInboundDialog`
2. 库位管理类：新增库位、仓库设置、颜色选择、删除确认
3. 库位物料操作类：统一收敛到 `InventoryItemManageDialog` + `LocationPickerDialog`
4. BOM 辅助类：绑定本地物料
5. 设置类：语言、关于、导入导出结果

其中最重要的一次收敛是：

- 过去存在多套“物料入库确认”弹窗
- 当前已统一收敛为 `MaterialInboundDialog`
- 后续如果继续扩展入库方式，应优先复用这一套，而不是新增新的确认弹窗

本轮新增的收敛是：

- 过去“转移仓库 / 选择仓库位置 / 批量转移”存在多套仓库选择弹窗
- 当前已统一收敛为 `LocationPickerDialog`
- 过去搜索页和仓库页的物料管理存在两套实现
- 当前已统一收敛为 `InventoryItemManageDialog`
