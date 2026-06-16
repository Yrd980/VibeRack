import CoreBluetooth
import Foundation

public struct SmartChassisDiscovery: Identifiable, Equatable {
    public let id: UUID
    public let peripheralIdentifier: UUID
    public let name: String?
    public let rssi: Int
    public let advertisement: SmartChassisAdvertisement
    public let discoveredAt: Date
    public let serviceUUIDs: [CBUUID]
    public let localName: String?

    public var displayName: String {
        localName ?? name ?? "VBRK-\(String(format: "%04X", advertisement.batchId))"
    }

    public var isSupportedProtocol: Bool {
        advertisement.protoVersion == SmartChassisProtocol.protocolVersion
    }

    public var requiresAppUpgrade: Bool {
        advertisement.protoVersion > SmartChassisProtocol.protocolVersion
    }

    public var requiresFirmwareUpgrade: Bool {
        advertisement.protoVersion < SmartChassisProtocol.protocolVersion
    }

    public init(
        peripheralIdentifier: UUID,
        name: String?,
        rssi: Int,
        advertisement: SmartChassisAdvertisement,
        discoveredAt: Date = Date(),
        serviceUUIDs: [CBUUID] = [],
        localName: String? = nil
    ) {
        self.id = peripheralIdentifier
        self.peripheralIdentifier = peripheralIdentifier
        self.name = name
        self.rssi = rssi
        self.advertisement = advertisement
        self.discoveredAt = discoveredAt
        self.serviceUUIDs = serviceUUIDs
        self.localName = localName
    }
}

public enum SmartChassisAdvertisementParser {
    public static func parse(_ advertisementData: [String: Any]) -> SmartChassisAdvertisement? {
        if let manufacturer = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data {
            if let parsed = SmartChassisCodec.parseAdvertisement(manufacturer),
               parsed.companyId == SmartChassisProtocol.devCompanyId {
                return parsed
            }
            if manufacturer.count >= 2 {
                let companyId = UInt16(manufacturer[manufacturer.startIndex])
                    | (UInt16(manufacturer[manufacturer.startIndex + 1]) << 8)
                let payload = manufacturer.dropFirst(2)
                if let parsed = SmartChassisCodec.parseAndroidManufacturerPayload(
                    companyId: companyId,
                    payload: Data(payload)
                ),
                    parsed.companyId == SmartChassisProtocol.devCompanyId {
                    return parsed
                }
            }
        }
        return nil
    }

    public static func discovery(
        peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi: NSNumber
    ) -> SmartChassisDiscovery? {
        guard let advertisement = parse(advertisementData) else {
            return nil
        }

        return SmartChassisDiscovery(
            peripheralIdentifier: peripheral.identifier,
            name: peripheral.name,
            rssi: rssi.intValue,
            advertisement: advertisement,
            serviceUUIDs: advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? [],
            localName: advertisementData[CBAdvertisementDataLocalNameKey] as? String
        )
    }
}

public struct SmartChassisDeviceInformation: Equatable {
    public var manufacturerName: String?
    public var modelNumber: String?
    public var serialNumber: String?
    public var hardwareRevision: String?
    public var firmwareRevision: String?
    public var softwareRevision: String?

    public init(
        manufacturerName: String? = nil,
        modelNumber: String? = nil,
        serialNumber: String? = nil,
        hardwareRevision: String? = nil,
        firmwareRevision: String? = nil,
        softwareRevision: String? = nil
    ) {
        self.manufacturerName = manufacturerName
        self.modelNumber = modelNumber
        self.serialNumber = serialNumber
        self.hardwareRevision = hardwareRevision
        self.firmwareRevision = firmwareRevision
        self.softwareRevision = softwareRevision
    }
}
