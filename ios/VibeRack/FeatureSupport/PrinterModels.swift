import Foundation

struct BoxLayerLabel: Equatable {
    let positionCode: String
    let partNumber: String

    init(positionCode: String, partNumber: String) {
        self.positionCode = positionCode.trimmingCharacters(in: .whitespacesAndNewlines)
        self.partNumber = partNumber
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
    }
}

enum P0DirectBluetoothSupport {
    static let current = P0DirectBluetoothStatus(
        userMessage: "已验证佟 P0 / 印立方可通过 BLE GATT 位图通道打印；iOS 使用 CoreBluetooth 写入 P0 位图协议，不走 Android 经典蓝牙 SPP/RFCOMM。"
    )
}

struct P0DirectBluetoothStatus: Equatable {
    let userMessage: String
}
