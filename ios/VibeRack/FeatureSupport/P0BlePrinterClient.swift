import CoreBluetooth
import Foundation
import Observation
import UIKit

enum P0BlePrinterUUIDs {
    static let advertisedService = CBUUID(string: "18F0")
    static let printService = CBUUID(string: "49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    static let notifyCharacteristic = CBUUID(string: "49535343-1E4D-4BD9-BA61-23C647249616")
    static let writeCharacteristic = CBUUID(string: "49535343-8841-43F4-A8D4-ECBE34729BB3")
}

struct P0BlePrinter: Identifiable, Equatable {
    let id: UUID
    let name: String
    let rssi: Int
}

@MainActor
@Observable
final class P0BlePrinterClient: NSObject {
    private var central: CBCentralManager?
    private var peripheralsByID: [UUID: CBPeripheral] = [:]
    private var connectedPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var pendingPrint: PendingPrint?
    private var writeQueue: [QueuedWrite] = []
    private var writeIndex = 0
    private var scanStopTask: Task<Void, Never>?

    private(set) var printers: [P0BlePrinter] = []
    private(set) var isScanning = false
    private(set) var isConnected = false
    private(set) var isPrinting = false
    private(set) var statusMessage = "未连接"
    private(set) var connectedName: String?

    override init() {
        super.init()
    }

    func scan() {
        ensureCentral()
        guard let central else { return }
        printers = []
        peripheralsByID = [:]
        statusMessage = "正在扫描 P0 打印机..."
        isScanning = true
        if central.state == .poweredOn {
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
            scheduleScanStop()
        }
    }

    func connect(to printer: P0BlePrinter) {
        ensureCentral()
        guard let central, let peripheral = peripheralsByID[printer.id] else {
            statusMessage = "未找到打印机"
            return
        }
        statusMessage = "正在连接 \(printer.name)..."
        connectedName = printer.name
        peripheral.delegate = self
        central.stopScan()
        isScanning = false
        central.connect(peripheral)
    }

    func disconnect() {
        if let connectedPeripheral {
            central?.cancelPeripheralConnection(connectedPeripheral)
        }
        resetConnection(message: "已断开")
    }

    func print(image: UIImage) {
        guard isConnected, let connectedPeripheral, writeCharacteristic != nil else {
            statusMessage = "请先连接 P0 打印机"
            return
        }
        do {
            let chunks = try P0BitmapProtocol.buildBitmapPrintChunks(image: image)
            pendingPrint = PendingPrint(chunks: chunks)
            startPrint(peripheral: connectedPeripheral)
        } catch {
            statusMessage = "标签渲染失败：\(error.localizedDescription)"
        }
    }

    private func ensureCentral() {
        if central == nil {
            central = CBCentralManager(delegate: self, queue: .main)
        }
    }

    private func scheduleScanStop() {
        scanStopTask?.cancel()
        scanStopTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 8_000_000_000)
            await MainActor.run {
                guard let self else { return }
                self.central?.stopScan()
                self.isScanning = false
                if self.printers.isEmpty {
                    self.statusMessage = "未发现 P0 打印机"
                } else if !self.isConnected {
                    self.statusMessage = "发现 \(self.printers.count) 台 P0 打印机"
                }
            }
        }
    }

    private func startPrint(peripheral: CBPeripheral) {
        guard let pendingPrint else { return }
        isPrinting = true
        statusMessage = "正在发送标签..."
        writeIndex = 0
        writeQueue = pendingPrint.chunks.map {
            QueuedWrite(data: $0.bytes, delayAfterMilliseconds: $0.delayAfterMilliseconds)
        }
        sendNext(peripheral: peripheral)
    }

    private func sendNext(peripheral: CBPeripheral) {
        guard let writeCharacteristic else { return }
        guard writeIndex < writeQueue.count else {
            isPrinting = false
            statusMessage = "标签已发送"
            pendingPrint = nil
            return
        }

        let item = writeQueue[writeIndex]
        peripheral.writeValue(item.data, for: writeCharacteristic, type: .withResponse)
        if item.delayAfterMilliseconds > 0 {
            statusMessage = "正在发送标签 \(writeIndex + 1)/\(writeQueue.count)"
        }
    }

    private func resetConnection(message: String) {
        connectedPeripheral = nil
        writeCharacteristic = nil
        pendingPrint = nil
        writeQueue = []
        writeIndex = 0
        isConnected = false
        isPrinting = false
        connectedName = nil
        statusMessage = message
    }

    private static func isP0Printer(name: String) -> Bool {
        name.localizedCaseInsensitiveContains("P0") ||
            name.localizedCaseInsensitiveContains("印立方") ||
            name.localizedCaseInsensitiveContains("德佟") ||
            name.localizedCaseInsensitiveContains("DETONGER") ||
            name.localizedCaseInsensitiveContains("DOTHANTECH")
    }
}

extension P0BlePrinterClient: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            switch central.state {
            case .poweredOn:
                statusMessage = "蓝牙已开启"
                if isScanning {
                    central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
                    scheduleScanStop()
                }
            case .poweredOff:
                statusMessage = "蓝牙未开启"
            case .unauthorized:
                statusMessage = "没有蓝牙权限"
            case .unsupported:
                statusMessage = "此设备不支持蓝牙"
            default:
                statusMessage = "蓝牙状态不可用"
            }
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        Task { @MainActor in
            let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String
            let name = localName ?? peripheral.name ?? "未知 P0"
            guard Self.isP0Printer(name: name) else { return }
            peripheralsByID[peripheral.identifier] = peripheral
            let printer = P0BlePrinter(id: peripheral.identifier, name: name, rssi: RSSI.intValue)
            printers.removeAll { $0.id == printer.id }
            printers.append(printer)
            printers.sort { $0.rssi > $1.rssi }
            statusMessage = "发现 \(name)"
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        Task { @MainActor in
            connectedPeripheral = peripheral
            peripheral.delegate = self
            statusMessage = "正在发现打印服务..."
            peripheral.discoverServices([P0BlePrinterUUIDs.printService])
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            statusMessage = "连接失败：\(error?.localizedDescription ?? "未知错误")"
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            resetConnection(message: error == nil ? "已断开" : "连接断开：\(error?.localizedDescription ?? "未知错误")")
        }
    }
}

extension P0BlePrinterClient: CBPeripheralDelegate {
    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        Task { @MainActor in
            if let error {
                statusMessage = "发现服务失败：\(error.localizedDescription)"
                return
            }
            guard let service = peripheral.services?.first(where: { $0.uuid == P0BlePrinterUUIDs.printService }) else {
                statusMessage = "未找到 P0 打印服务"
                return
            }
            peripheral.discoverCharacteristics(
                [P0BlePrinterUUIDs.notifyCharacteristic, P0BlePrinterUUIDs.writeCharacteristic],
                for: service
            )
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        Task { @MainActor in
            if let error {
                statusMessage = "发现特征失败：\(error.localizedDescription)"
                return
            }
            for characteristic in service.characteristics ?? [] {
                if characteristic.uuid == P0BlePrinterUUIDs.notifyCharacteristic {
                    peripheral.setNotifyValue(true, for: characteristic)
                } else if characteristic.uuid == P0BlePrinterUUIDs.writeCharacteristic {
                    writeCharacteristic = characteristic
                }
            }
            if writeCharacteristic != nil {
                isConnected = true
                statusMessage = "已连接 \(connectedName ?? peripheral.name ?? "P0")"
            } else {
                statusMessage = "未找到 P0 写入特征"
            }
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        Task { @MainActor in
            if let error {
                isPrinting = false
                statusMessage = "发送失败：\(error.localizedDescription)"
                return
            }
            guard writeIndex < writeQueue.count else { return }
            let delay = writeQueue[writeIndex].delayAfterMilliseconds
            writeIndex += 1
            if delay > 0 {
                try? await Task.sleep(nanoseconds: delay * 1_000_000)
            }
            sendNext(peripheral: peripheral)
        }
    }
}

private struct PendingPrint {
    let chunks: [P0PrintChunk]
}

private struct QueuedWrite {
    let data: Data
    let delayAfterMilliseconds: UInt64
}
