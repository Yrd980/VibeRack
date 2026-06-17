import CoreBluetooth
import XCTest
@testable import VibeRack

final class SmartChassisBluetoothTests: XCTestCase {
    func testServiceAndCharacteristicUUIDsMatchProtocolSpec() {
        XCTAssertEqual(SmartChassisBluetoothUUIDs.bindingTableService.uuidString, "7F4B0001-8D1A-4D45-9A4E-2B4A7C000000")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.bindingControlPoint.uuidString, "7F4B1001-8D1A-4D45-9A4E-2B4A7C000000")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.tableInfo.uuidString, "7F4B1002-8D1A-4D45-9A4E-2B4A7C000000")

        XCTAssertEqual(SmartChassisBluetoothUUIDs.lightService.uuidString, "7F4B0002-8D1A-4D45-9A4E-2B4A7C000000")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.lightCommand.uuidString, "7F4B2001-8D1A-4D45-9A4E-2B4A7C000000")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.lightStatus.uuidString, "7F4B2002-8D1A-4D45-9A4E-2B4A7C000000")

        XCTAssertEqual(SmartChassisBluetoothUUIDs.deviceHealthService.uuidString, "7F4B0003-8D1A-4D45-9A4E-2B4A7C000000")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.deviceHealth.uuidString, "7F4B3001-8D1A-4D45-9A4E-2B4A7C000000")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.batteryService.uuidString, "180F")
        XCTAssertEqual(SmartChassisBluetoothUUIDs.deviceInformationService.uuidString, "180A")
    }

    func testDiscoveryServiceSetIncludesM1RequiredServices() {
        XCTAssertEqual(
            SmartChassisBluetoothUUIDs.discoveryServices,
            [
                SmartChassisBluetoothUUIDs.bindingTableService,
                SmartChassisBluetoothUUIDs.lightService,
                SmartChassisBluetoothUUIDs.deviceHealthService,
                SmartChassisBluetoothUUIDs.batteryService,
                SmartChassisBluetoothUUIDs.deviceInformationService
            ]
        )
    }

    func testAdvertisementParserAcceptsIosManufacturerDataWithCompanyBytes() {
        let advertisement = SmartChassisAdvertisementParser.parse(
            [
                CBAdvertisementDataManufacturerDataKey: Data(hex: "FF FF 01 01 00 64 04 34 12 00 00")
            ]
        )

        XCTAssertEqual(advertisement?.companyId, SmartChassisProtocol.devCompanyId)
        XCTAssertEqual(advertisement?.protoVersion, 1)
        XCTAssertEqual(advertisement?.batchId, 1)
        XCTAssertEqual(advertisement?.batteryPct, 100)
        XCTAssertEqual(advertisement?.statusFlags, SmartChassisProtocol.advertisementLightActiveFlag)
        XCTAssertEqual(advertisement?.tableSeqLow16, 0x1234)
    }

    func testAdvertisementParserAcceptsRealVbrkScanPayloadWithoutAdvertisedServices() {
        let advertisement = SmartChassisAdvertisementParser.parse(
            [
                CBAdvertisementDataManufacturerDataKey: Data(hex: "FF FF 01 01 00 64 02 11 00 00 00"),
                CBAdvertisementDataLocalNameKey: "VBRK-0000"
            ]
        )

        XCTAssertEqual(advertisement?.protoVersion, 1)
        XCTAssertEqual(advertisement?.batchId, 1)
        XCTAssertEqual(advertisement?.batteryPct, 100)
        XCTAssertEqual(advertisement?.statusFlags, SmartChassisProtocol.advertisementHasUnboundSlotFlag)
        XCTAssertEqual(advertisement?.tableSeqLow16, 0x0011)
    }

    func testAdvertisementParserRejectsOtherCompanyIds() {
        let advertisement = SmartChassisAdvertisementParser.parse(
            [
                CBAdvertisementDataManufacturerDataKey: Data(hex: "4C 00 01 01 00 64 00 34 12")
            ]
        )

        XCTAssertNil(advertisement)
    }

    func testBluetoothUserMessageNamesPairingAndEncryptionFailures() {
        XCTAssertEqual(
            SmartChassisBluetoothError.insufficientAuthentication.userMessage,
            "智能底盘需要已认证的蓝牙配对，请重新配对后再写入。"
        )
        XCTAssertEqual(
            SmartChassisBluetoothError.insufficientEncryption.userMessage,
            "当前连接未加密。请断开后重新连接，并在系统弹窗中完成配对。"
        )
    }
}

private extension Data {
    init(hex: String) {
        let bytes = hex
            .split { $0 == " " || $0 == "\n" || $0 == "\t" }
            .compactMap { UInt8($0, radix: 16) }
        self.init(bytes)
    }
}
