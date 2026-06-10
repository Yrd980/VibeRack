# Printer, NFC, and BOM Session Findings

Date: 2026-06-10

This document captures the findings and engineering decisions from the printer/NFC/BOM discussion and the physical P0 printer test session.

Related product-direction note:

- `docs/superpowers/specs/2026-06-10-box-layer-material-label-design.md`

## Product Direction

The app should move from a generic warehouse ERP shape toward a printer-first component box tool.

The target physical workflow is:

1. A user stores LCSC components in small magnetic stackable boxes.
2. Each box has narrow layers, currently around 10 mm wide, and future boxes may be around 12-18 mm wide.
3. One layer should bind to one component.
4. The app should assign components to box layers.
5. The app should print labels for the layer/component binding.
6. NFC tags are separate physical media from the printed label, but they should bind to the same layer/component identity in the app.

This means the primary domain object should be a `BoxLayer`, not a generic warehouse location.

## Label Width Constraint

The current box layer width may be only about 10 mm.

Engineering implications:

- Printed labels must be designed for very narrow visible space.
- The first line should prioritize the physical position, for example `BOX01-L03`.
- The second line should prioritize the LCSC part number or compact part identity.
- Full MPN/spec text may need truncation or a secondary detail screen.
- QR codes may be too large for the narrowest label unless the label can extend along the layer length.
- NFC should not be treated as printed onto the same sticker; it should be a separate tag bound to the same app record.

Recommended payload identity:

```text
lcscerp://material?part=C17710&box=BOX01&layer=L03
```

Future label design should support multiple label profiles:

- 10 mm narrow layer label.
- 12-18 mm layer label.
- Larger fallback label for storage bags or reels.

## NFC Direction

NFC should be a first-class app workflow, but separate from the printed sticker.

Recommended behavior:

1. User binds a component to a box layer.
2. User prints a visible label for the layer.
3. User optionally writes an NFC tag with the same payload.
4. Scanning NFC opens the bound layer/component detail.

Current app context:

- `core/nfc/` exists in the working tree.
- NFC payload handling already has a basis for reading/writing app-specific records.
- The current exposed workflow is closer to location labels; it should evolve toward explicit `box` and `layer` fields.

## BOM Findings

File inspected:

```text
asset/BOM.xlsx
```

Observed structure:

- One sheet.
- 50 rows total.
- Headers:
  - `No.`
  - `Quantity`
  - `Comment`
  - `Footprint`
  - `Value`
  - `Manufacturer Part`
  - `Manufacturer`
  - `Supplier Part`
  - `Supplier`
  - `Designator`
- 24 rows have `Supplier Part`.
- 26 rows are missing `Supplier Part`.

Important interpretation:

- BOM `Quantity` is project usage count, not stock quantity.
- BOM import should not be treated as local inventory lookup first.
- BOM import should become an engineering BOM to LCSC catalog resolution flow:
  1. Parse `Supplier Part` when present.
  2. Fall back to `Manufacturer Part`, `Value`, `Footprint`, and manufacturer when missing.
  3. Resolve or let the user choose the LCSC component.
  4. Assign resolved components to box layers.
  5. Print labels and optionally write NFC tags for those layer bindings.

## Printer Research Findings

User requirements discussed:

1. Investigate the Detonger/De Tong printer plugin and how to integrate it into the material management app.
2. Investigate NFC open-source projects and Taobao NFC tags, then integrate NFC into the same app.
3. Run the core printer flow with the physical printer nearby.

Useful external references:

- Detonger Android quick-start PDF: `https://detonger.com/software/um/%E5%BE%B7%E4%BD%9F%E7%94%B5%E5%AD%90-%E8%93%9D%E7%89%99%E6%89%93%E5%8D%B0%E6%8E%A5%E5%8F%A3Android%E4%BD%BF%E7%94%A8%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8-2021-05-08.pdf`
- DCloud Detonger printer plugin: `https://ext.dcloud.net.cn/plugin?id=4027`

The practical takeaway is that the vendor ecosystem centers around the vendor LPAPI/plugin, and first-use pairing is expected in the system Bluetooth settings. For this native Android project, the direct SPP route is enough to establish the connection path without bringing in the full vendor SDK immediately.

## Android Test Environment

Observed environment:

- Android device: `10AE2N0KTC004L2`
- Model: `V2309A`
- Android SDK: `36`
- App package: `com.example.lcsc_android_erp`
- Java environment comes from Scoop:
  - `JAVA_HOME` points under `C:\Users\Yrd98\scoop\apps\openjdk17\current`
- Gradle user home also pointed under Scoop initially:
  - `C:\Users\Yrd98\scoop\apps\gradle\current\.gradle`

Build issue encountered:

- The Scoop Gradle/Kotlin cache produced a Gradle Kotlin DSL `NoSuchMethodError`.
- Workaround used for this session:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user-home-test'
.\gradlew.bat --no-daemon --init-script tmp\gradle-mirror-init.gradle.kts :app:assembleDebug --no-configuration-cache
```

Important environment decision:

- Do not modify the user's global Scoop environment variables.
- Use a project-local temporary `GRADLE_USER_HOME` per build command when needed.

Permissions granted on the Android device:

- `android.permission.BLUETOOTH_SCAN`
- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.CAMERA`

Bluetooth was enabled on the Android device.

## P0 Printer Test Findings

Physical printer:

- Displayed/advertised name: `P0-YF604020851`
- Bluetooth address: `B8:50:44:11:5C:9D`

Initial app behavior:

- Printer tab was restored to the bottom navigation.
- The P0 printer type option was available as `佟 P0 / 印立方`.
- The app could start scanning.
- Before the printer was powered on, no matching P0 device appeared.
- After the printer was powered on, the app discovered `P0-YF604020851`.

BLE scan evidence:

- Android BLE scan returns the printer name `P0-YF604020851`.
- Current name matcher already handles it because it contains `P0`.
- The scan/filter layer is not the problem.

Failed BLE-GATT connection hypothesis:

- The first P0 implementation used BLE GATT.
- Android connected at the BLE level.
- Service discovery returned success but `serviceCount=0`.
- There was no writable GATT characteristic to use for printing.

Conclusion:

- This printer should not be handled as a BLE-GATT printer for the current app path.
- It should be treated as a classic Bluetooth SPP/RFCOMM printer.

SPP validation:

- The P0 client was changed to create an RFCOMM socket using the standard Serial Port Profile UUID:

```text
00001101-0000-1000-8000-00805F9B34FB
```

- Android system logs showed the Bluetooth socket connecting.
- The app UI showed:

```text
已连接 P0-YF604020851
```

Final clean connection verification after removing temporary debug logs:

- `:app:assembleDebug` passed.
- APK installed successfully.
- App launched.
- Printer page opened.
- `P0-YF604020851` appeared.
- Tapping `连接` showed `已连接 P0-YF604020851`.

Print smoke-test verification:

- A `测试打印` action was added to the Printer screen.
- The action generates a fixed bitmap smoke label and sends it through `PrinterManager.printBitmap(...)`.
- Android verification steps:
  - `:app:assembleDebug` passed with the project-local `GRADLE_USER_HOME`.
  - APK installed successfully on `10AE2N0KTC004L2`.
  - App launched.
  - Printer page opened.
  - `P0-YF604020851` appeared.
  - Tapping `连接` showed `已连接 P0-YF604020851`.
  - Tapping `测试打印` showed `打印任务已发送。`.
- First centered horizontal test label printed only a small visible fragment (`B` and a left-parenthesis-like mark), which confirmed the protocol was working but the label content was outside the practical visible area for the current narrow medium.
- The smoke label was changed to a narrow, rotated layout:

```text
BOX01-L01
C17710
TEST PRINT
```

- The final physical test print succeeded after using the narrow rotated layout.
- Practical label-design conclusion: for the current P0 medium and box layer use case, label content should be laid out in a narrow band and rotated to run along the label length, not centered across a wide landscape bitmap.

## PC vs Android Testing Boundary

The PC can help with lower-level protocol probing, but it cannot fully replace Android app integration testing.

Use the Android device when verifying:

- Runtime Bluetooth permission behavior.
- Android scanning behavior.
- Android pairing behavior.
- App UI state.
- App lifecycle behavior.
- Compose flow from Printer screen to connection state.

Use the PC when verifying:

- Raw printer protocol bytes.
- Faster trial-and-error on print command sequences.
- Serial/SPP behavior if Windows is paired to the printer and has a COM port mapped.

Current PC observation:

- Windows has several existing Bluetooth serial COM ports.
- The P0 printer was not visible by name in the Windows Bluetooth device list during this session.
- To use the PC as a protocol probe, pair `P0-YF604020851` with Windows first and identify its mapped COM port.

## Implementation Changes From This Session

P0 connection:

- `P0BluetoothClient` now uses classic Bluetooth SPP/RFCOMM instead of BLE GATT.
- `P0Protocol` now owns the standard SPP UUID.
- `P0PrinterManager` still uses BLE scanning to discover P0 devices, then passes the discovered address to the SPP client.

P0 print smoke test:

- `PrinterScreen` exposes a `测试打印` action when a printer is connected.
- `PrinterSmokeTestLabel` generates the current P0 smoke-test bitmap.
- The accepted physical layout is a narrow rotated label, with text running along the label length.
- The smoke-test bitmap is now backed by the same reusable 10 mm box-layer label generator used by the Printer screen.
- `PrinterScreen` now has a first real 10 mm box-layer label tool with editable position and LCSC part fields, an on-screen bitmap preview, and a print action that sends the preview bitmap through the connected `PrinterManager`.
- The generator still outputs a ready-to-print `384 x 232` bitmap and keeps the verified rotated coordinate system; it does not ask `P0Protocol` to scale or crop a different label aspect ratio.

Navigation:

- Printer was restored as a visible top-level destination in the app.

Build hygiene:

- `.superpowers/` is ignored.
- `.gradle-user-home*/` is ignored.
- Global Scoop environment variables were left unchanged.

Temporary diagnostics:

- BLE/GATT debug logs were added during investigation.
- Those temporary `[DEBUG-P0SCAN]` and `[DEBUG-P0CONN]` logs were removed after the SPP path was verified.

## Current Engineering Position

For the next implementation step, the core path should be:

1. Keep P0 discovery through BLE scan.
2. Keep P0 connection through SPP.
3. Keep using `P0Protocol.buildBitmapPrintChunks(...)` as the current print byte stream.
4. Use the verified narrow rotated smoke-test layout as the starting point for real box-layer labels.
5. Add formal label profiles before expanding into full box-layer assignment:
   - 10 mm narrow layer label.
   - 12-18 mm layer label.
   - Larger fallback label.
6. If a future label profile fails, isolate whether the issue is:
   - bitmap layout,
   - page dimensions,
   - feed/gap behavior,
   - or command protocol.
7. Only bring in the vendor LPAPI if the raw protocol path becomes too uncertain or lacks required printer features.

## Open Questions

Printer/protocol:

- Does the current `P0Protocol` byte stream support all needed future label sizes for this exact `P0-YF604020851` firmware?
- Does the printer require any extra mode-select, page-size, gap, or feed command for non-smoke-test label profiles?
- Does the printer support status reads over SPP, or is it write-only for our purposes?
- Does the printer disconnect after idle or after a print job?

Label design:

- What exact label roll size is installed in the printer?
- The smoke test confirmed the useful orientation is along the layer length for the current narrow label.
- Should the 10 mm label contain QR, or should QR be reserved for wider labels?

NFC:

- Which NFC tag type will be bought from Taobao?
- Target should likely be NTAG213/215/216-compatible tags unless a different phone compatibility requirement appears.
- Where will the NFC tag be physically placed relative to the printed label?

BOM:

- Should unresolved BOM rows create draft components, or should they block assignment until resolved?
- Should BOM `Quantity` become recommended procurement count, usage count, or simply reference metadata?

Data model:

- Whether to introduce explicit `Box`, `BoxLayer`, and `LayerMaterial` tables now, or first adapt the existing `storage_location` model as a temporary bridge.

## Completed Smoke-Test Slice

Completed:

1. Added a simple test label action from the Printer screen.
2. Generated a small text/bitmap label:

```text
BOX01-L01
C17710
TEST PRINT
```

3. Sent it through the verified SPP connection.
4. Confirmed the printer feeds and prints.
5. Adjusted the layout after the first physical output showed only a cropped fragment.
6. Confirmed the final narrow rotated label prints correctly.

## Completed App-Level 10 mm Label Slice

Implemented in the app:

1. Promoted the verified smoke-test layout into a reusable 10 mm box-layer bitmap profile.
2. Kept the generator output at `384 x 232` dots to match the current P0/Q5 bitmap encoder assumptions.
3. Added text fitting and end ellipsis so long position or part strings do not run outside the rotated printable band.
4. Kept `测试打印` as a compatibility smoke action, but made it call the formal 10 mm label profile.
5. Added a Printer screen card for:
   - position, for example `BOX01-L03`,
   - LCSC part, for example `C17710`,
   - live label preview,
   - print action gated by the connected printer state.

Still pending:

- A new physical print check of the editable `打印盒层标签` action on `P0-YF604020851`.
- Real box/layer assignment data instead of temporary Printer screen fields.

## Recommended Next Slice

The next useful slice after the app-level 10 mm label tool is physical verification and then real box-layer assignment:

1. Print from the new editable `10mm 盒层标签` card with real layer/component values.
2. Confirm the printed output uses the same practical area as the successful smoke test.
3. If the physical output is good, introduce temporary or formal box/layer assignment data so the label fields come from a selected layer/component instead of manual inputs.
4. Keep QR out of the 10 mm profile unless a wider physical label is confirmed.
5. Only after the 10 mm profile is stable should the work move into BOM assignment and NFC write flow.

Example target label:

```text
BOX01-L03
C17710
```
