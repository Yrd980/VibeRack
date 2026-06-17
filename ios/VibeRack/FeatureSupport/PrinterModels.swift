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
        isAvailable: true
    )
}

struct P0DirectBluetoothStatus: Equatable {
    let isAvailable: Bool
}
