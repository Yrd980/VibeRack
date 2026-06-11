# 智能底盘 BLE 接口规格 v0.1

适用范围：智能底盘（25 槽，nRF52811 + NT3H2111）与 Android APP（LCSC ERP）之间的全部蓝牙交互。本文档是固件与 APP 的共同契约，任何修改需同步更新协议版本号。

---

## 1. 设备命名与标识

- 设备名：`VBRK-XXXX`，XXXX 为 MAC 后两字节十六进制，放在扫描响应（Scan Response）中。
- 批次 ID（batch_id）：uint16，出厂或首次绑定时分配，写入 MCU flash 与 NFC NDEF。
- 协议版本（proto_ver）：当前为 `0x01`。APP 发现版本高于自身支持时提示升级 APP，低于时提示升级固件。

## 2. 广播包格式

广播间隔：常态 1500ms；NFC 碰醒后 100ms 持续 30 秒。

**Advertising Data（≤31 字节）**

| 字段 | 长度 | 内容 |
|---|---|---|
| Flags | 3 B | 标准 LE General Discoverable |
| Manufacturer Specific Data | 11 B | 见下 |

**厂商自定义字段布局（9 B 有效载荷）**

| 偏移 | 长度 | 字段 | 说明 |
|---|---|---|---|
| 0 | 2 B | company_id | 开发期用 0xFFFF，量产前申请正式 ID |
| 2 | 1 B | proto_ver | 协议版本 |
| 3 | 2 B | batch_id | 小端 |
| 5 | 1 B | battery_pct | 0–100 |
| 6 | 1 B | status_flags | bit0 低电量；bit1 存在未绑定槽位；bit2 正在点灯；bit3 故障 |
| 7 | 2 B | table_seq | 绑定表版本号低 16 位，APP 据此判断缓存是否过期，免连接 |

开发期固件当前会在上述核心字段后附加 2 字节预留尾部，写 `0x0000`。APP 解析时只依赖前 9 字节核心字段，并忽略额外尾部字节，避免后续广告扩展破坏兼容。

> APP 扫描页凭广播即可渲染"全部底盘 + 电量 + 数据是否最新"列表，无需逐台连接。

## 3. 服务总表

| 服务 | UUID | 内容 |
|---|---|---|
| 绑定表服务 | 自定义 128-bit（基 UUID + 0x0001） | 槽位记录读写与结构化操作 |
| 灯控服务 | 自定义 128-bit（基 UUID + 0x0002） | 点灯指令与状态 |
| Battery Service | 0x180F（标准） | 电量百分比，notify |
| Device Information | 0x180A（标准） | 固件版本、硬件版本、型号 |
| Secure DFU | Nordic 标准 | 无按钮 OTA 升级入口 |

自定义基 UUID 定一个随机 128-bit，全产品线复用；开发期以 UUID 第一段低 16 位 short id 区分服务/特征。

### 3.1 开发期 UUID 常量

开发期固定使用 UUID 模板 `7f4bXXXX-8d1a-4d45-9a4e-2b4a7c000000`，其中第一段末尾 `XXXX` 为 16-bit short id，对应上表"基 UUID + 0x0001 / 0x0002"的写法。若后续修改以下任一 UUID，必须同步提升本协议文档版本并通知 Android、固件两端。

| 项 | Short id | UUID |
|---|---:|---|
| Binding Table Service | 0x0001 | `7f4b0001-8d1a-4d45-9a4e-2b4a7c000000` |
| Binding Control Point characteristic | 0x1001 | `7f4b1001-8d1a-4d45-9a4e-2b4a7c000000` |
| Table Info characteristic | 0x1002 | `7f4b1002-8d1a-4d45-9a4e-2b4a7c000000` |
| Light Service | 0x0002 | `7f4b0002-8d1a-4d45-9a4e-2b4a7c000000` |
| Light Command characteristic | 0x2001 | `7f4b2001-8d1a-4d45-9a4e-2b4a7c000000` |
| Light Status characteristic | 0x2002 | `7f4b2002-8d1a-4d45-9a4e-2b4a7c000000` |

P0/银立方（Yinlifang）打印机蓝牙行为不属于本 GATT 合约：打印机仍使用 BLE 扫描发现设备，打印连接继续走经典蓝牙 SPP/RFCOMM。

## 4. 槽位绑定记录格式（16 字节定长）

| 偏移 | 长度 | 字段 | 说明 |
|---|---|---|---|
| 0 | 1 B | slot | 槽位号 1–25，0 表示无效 |
| 1 | 10 B | part_id | ASCII，立创料号 `C` 开头；手动物料 `M` 开头 + APP 本地号；不足补 0x00 |
| 11 | 2 B | qty | uint16 小端，台账数量 |
| 13 | 1 B | flags | bit0 湿敏 MSD；bit1 低库存已触发；bit2 自定义物料；其余保留 |
| 14 | 1 B | 保留 | 写 0x00 |
| 15 | 1 B | crc8 | 前 15 字节 CRC-8/MAXIM |

设计原则：MCU 只存"槽位 → 料号 + 数量"这一最小可恢复集。料号是 APP 数据库主键，物料详情由 APP 凭料号联网重建。安全库存阈值、来源链接等富数据只存 APP/云端，不下发 MCU。

## 5. 绑定表服务

### 5.1 特征值

| 特征 | 属性 | 说明 |
|---|---|---|
| Control Point | Write / Notify | 操作码协议，见 5.2 |
| Table Info | Read / Notify | `table_seq`(uint32) + `crc16`(全表 CRC-16/CCITT-FALSE) + `slot_count`(uint8=25) |

每次任何写操作成功后 `table_seq` 自增并 Notify，多手机场景下其他已连接端凭此感知变更。

### 5.2 操作码协议（写入 Control Point，结果经 Notify 返回）

| Op | 名称 | 载荷 | 行为 |
|---|---|---|---|
| 0x01 | READ_ONE | slot(1B) | Notify 返回该槽 16B 记录 |
| 0x02 | READ_ALL | 无 | 连续 Notify 25 条记录，末尾跟结束帧 |
| 0x10 | WRITE_ONE | 16B 记录 | 覆盖写入单槽，写穿 FDS |
| 0x11 | CLEAR_ONE | slot(1B) | 清空单槽 |
| 0x20 | INSERT_AT | slot(1B) + 16B 记录 | slot 及之后的记录整体后移一位，新记录落位。末槽非空则拒绝，返回 ERR_FULL |
| 0x21 | REMOVE_AT | slot(1B) | 删除该槽，之后记录整体前移合拢 |
| 0x22 | MOVE_BLOCK | from(1B) + to(1B) + len(1B) | 区块平移，用于整理模式执行后落账 |
| 0x30 | SET_QTY | slot(1B) + qty(2B) | 仅改数量，最高频操作单独给短指令 |
| 0xF0 | FACTORY_RESET | magic(4B) | 清空全表，magic=0x5A5AA5A5 防误触 |

**关键约定：插入/移除/平移的重编号逻辑在 MCU 端执行**，APP 只发结构化指令。这保证硬件是绑定关系的单一事实源，APP 收到操作成功后用 READ_ALL 或本地等价变换刷新缓存，两端永不因"各自算各自的"而分叉。

所有响应帧格式：`op(1B) + status(1B) + payload`。status：0x00 成功；0x01 参数错误；0x02 槽满；0x03 flash 忙（APP 退避 100ms 重试）；0x04 CRC 错误。

## 6. 灯控服务

### 6.1 特征值

| 特征 | 属性 | 说明 |
|---|---|---|
| Light Command | Write Without Response | 17B 指令，见 6.2 |
| Light Status | Read / Notify | mode(1B) + 剩余秒数(2B) |

### 6.2 指令格式（17 字节，兼容默认 MTU 23）

| 偏移 | 长度 | 字段 | 说明 |
|---|---|---|---|
| 0 | 1 B | mode | 见 6.3 |
| 1 | 4 B | mask_a | 槽位掩码，bit0=1 号槽 … bit24=25 号槽 |
| 5 | 4 B | mask_b | 第二掩码（整理模式用），其他模式写 0 |
| 9 | 3 B | color_a | RGB |
| 12 | 3 B | color_b | RGB（整理模式第二色） |
| 15 | 2 B | timeout_s | 自动熄灭秒数，0 = 默认 30s，上限 300s |

### 6.3 模式枚举

| mode | 名称 | 灯效 |
|---|---|---|
| 0x00 | OFF | 立即全灭并断 MOS |
| 0x01 | FIND | 两段式引导：整条 color_a 闪 2s → 仅 mask_a 常亮至超时 |
| 0x02 | PICK | mask_a 全部常亮（BOM 拣料），APP 每勾掉一项重发更新后的掩码 |
| 0x03 | SORT | mask_a 亮 color_a（取出位，建议红），mask_b 亮 color_b（放入位，建议绿） |
| 0x04 | STOCK_IN | mask_a 呼吸 color_a（入库引导，建议绿） |
| 0x05 | FX | 彩蛋动画，mask 忽略，timeout 强制 ≤10s |

固件实现要求：灯效由 PWM + EasyDMA 输出，模式切换先灭后亮避免残影；超时熄灭定时器独立于 BLE 连接状态；MOS 仅在 mode≠OFF 期间导通。

## 7. 安全与配对

- v0.1 采用 Just Works 配对 + 绑定（bonding），NFC NDEF 中携带 batch_id 与 MAC 用于 APP 定向连接。
- NDEF URI 格式沿用 APP 现有路由体系，新增设备类型：`lcscerp://device?mac=AA:BB:CC:DD:EE:FF&batch=1001&ver=1`。
- 写类操作（绑定表全部 op、DFU）要求链路已加密；灯控读操作放开，便于快速点灯。
- v0.2 预留：NDEF 内置 6 位 passkey 升级为 Passkey 配对，防邻桌误连。

## 8. 典型时序

**入库绑定**：扫立创标签 → APP 选定底盘与槽位（孪生视图点选或推荐）→ `STOCK_IN` 点绿灯 → 用户放盒 → APP 发 `WRITE_ONE` → 收成功响应后落本地账。

**找料**：本地搜索命中 → APP 连接目标底盘（慢广播直连）→ 发 `FIND` → 用户取料 → APP 发 `SET_QTY` 更新数量 → 断开。

**BOM 拣料**：BOM 匹配完成 → 按底盘分组生成掩码 → 逐台发 `PICK` → 每完成一项重发掩码 → 掩码为 0 发 `OFF`。

**整理模式**：APP 用第二属性比较器算出目标序 → diff 生成最少步数方案 → 逐步发 `SORT`（红=掰开取出，绿=落位）→ 全部完成后发对应 `MOVE_BLOCK`/`INSERT_AT` 落账 → 校验 `table_seq` 与 CRC。

**从硬件恢复**（换机/重装）：碰 NFC → APP 跳转并连接 → `READ_ALL` 拉 25 条记录 → 凭料号联网补全物料详情 → 重建本地库存。逐台碰完，全仓复活。

## 9. 实现约束备忘

1. 默认 MTU 23 字节下所有单帧指令均可达，APP 仍应尝试 MTU 协商至 ≥64 以加速 READ_ALL。
2. 绑定表写操作全部写穿 FDS；SET_QTY 高频，依赖 FDS 磨损均衡，flash 寿命按每天 50 次更新评估 >10 年。
3. 连接参数：交互期间请求 interval 30–50ms，空闲 2s 后协商至 400ms + slave latency 4。
4. `table_seq` 持久化存储，FACTORY_RESET 也不清零，保证版本号单调递增。
5. 固件断言/看门狗复位后首包 Notify 携带复位原因码，便于现场排查。
