import XCTest
@testable import VibeRack

final class SmartChassisProtocolVectorTests: XCTestCase {
    func testHardwareProtocolJsonVectors() throws {
        let vectors = try HardwareProtocolVectors.load()

        XCTAssertEqual(vectors.version, "0.1")
        XCTAssertEqual(vectors.slotCount, SmartChassisProtocol.slotCount)
        XCTAssertEqual(vectors.slotRecordSize, SmartChassisProtocol.slotRecordSize)
        XCTAssertEqual(vectors.lightCommandSize, SmartChassisProtocol.lightCommandSize)

        let record = SmartChassisCodec.encodeSlotRecord(
            slot: 1,
            partId: "C1234567",
            quantity: 12,
            flags: 0
        )

        XCTAssertEqual(
            SmartChassisCodec.encodeWriteOne(record: record),
            vectors.binding["write_one_slot_1_c1234567_qty_12"]?.data
        )
        XCTAssertEqual(SmartChassisCodec.encodeReadOne(slot: 1), vectors.binding["read_one_slot_1"]?.data)
        XCTAssertEqual(SmartChassisCodec.encodeReadAll(), vectors.binding["read_all"]?.data)
        XCTAssertEqual(
            SmartChassisCodec.parseBindingResult(try vectors.binding.requiredData("read_all_end"))?.payload,
            Data([SmartChassisProtocol.readAllEndMarker])
        )
        XCTAssertEqual(SmartChassisCodec.encodeFactoryReset(), vectors.binding["factory_reset"]?.data)

        XCTAssertEqual(
            SmartChassisCodec.encodeLightCommand(.findSlot1Green30s),
            vectors.light["find_slot_1_green_30s"]?.data
        )
        XCTAssertEqual(
            SmartChassisCodec.encodeLightCommand(.pickSlots1_7_25Green30s),
            vectors.light["pick_slots_1_7_25_green_30s"]?.data
        )
        XCTAssertEqual(SmartChassisCodec.encodeLightCommand(.off), vectors.light["off"]?.data)
    }

    func testHardwareBindingCommandVectors() {
        let record = SmartChassisCodec.encodeSlotRecord(
            slot: 1,
            partId: "C1234567",
            quantity: 12,
            flags: 0
        )

        XCTAssertEqual(
            SmartChassisCodec.encodeWriteOne(record: record),
            Data(hex: "10 01 43 31 32 33 34 35 36 37 00 00 0C 00 00 00 18")
        )
        XCTAssertEqual(SmartChassisCodec.encodeReadOne(slot: 1), Data(hex: "01 01"))
        XCTAssertEqual(SmartChassisCodec.encodeReadAll(), Data(hex: "02"))
        XCTAssertTrue(SmartChassisCodec.isReadAllEndPayload(Data(hex: "FF")))
        XCTAssertEqual(SmartChassisCodec.parseBindingResult(Data(hex: "02 00 FF"))?.payload, Data(hex: "FF"))
        XCTAssertEqual(SmartChassisCodec.encodeFactoryReset(), Data(hex: "F0 A5 A5 5A 5A"))
    }

    func testHardwareLightCommandVectors() {
        XCTAssertEqual(
            SmartChassisCodec.encodeLightCommand(
                LightCommand(
                    mode: .find,
                    maskA: SmartChassisCodec.slotMask(slot: 1),
                    maskB: 0,
                    colorA: RGBColor(red: 0, green: 255, blue: 0),
                    colorB: RGBColor(red: 0, green: 0, blue: 0),
                    timeoutSeconds: 30
                )
            ),
            Data(hex: "01 01 00 00 00 00 00 00 00 00 FF 00 00 00 00 1E 00")
        )

        XCTAssertEqual(
            SmartChassisCodec.encodeLightCommand(
                LightCommand(
                    mode: .pick,
                    maskA: SmartChassisCodec.slotMask(slot: 1) |
                        SmartChassisCodec.slotMask(slot: 7) |
                        SmartChassisCodec.slotMask(slot: 25),
                    maskB: 0,
                    colorA: RGBColor(red: 0, green: 255, blue: 0),
                    colorB: RGBColor(red: 0, green: 0, blue: 0),
                    timeoutSeconds: 30
                )
            ),
            Data(hex: "02 41 00 00 01 00 00 00 00 00 FF 00 00 00 00 1E 00")
        )

        XCTAssertEqual(
            SmartChassisCodec.encodeLightCommand(
                LightCommand(
                    mode: .off,
                    maskA: 0,
                    maskB: 0,
                    colorA: RGBColor(red: 0, green: 0, blue: 0),
                    colorB: RGBColor(red: 0, green: 0, blue: 0),
                    timeoutSeconds: 0
                )
            ),
            Data(hex: "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00")
        )
    }

    func testAdvertisementAndTableInfoParsing() {
        let advertisement = SmartChassisCodec.parseAdvertisement(
            Data(hex: "FF FF 01 E9 03 58 04 34 12 00 00")
        )

        XCTAssertEqual(advertisement?.companyId, SmartChassisProtocol.devCompanyId)
        XCTAssertEqual(advertisement?.protoVersion, 1)
        XCTAssertEqual(advertisement?.batchId, 1001)
        XCTAssertEqual(advertisement?.batteryPct, 88)
        XCTAssertEqual(advertisement?.statusFlags, SmartChassisProtocol.advertisementLightActiveFlag)
        XCTAssertEqual(advertisement?.tableSeqLow16, 0x1234)

        let androidPayload = SmartChassisCodec.parseAndroidManufacturerPayload(
            companyId: SmartChassisProtocol.devCompanyId,
            payload: Data(hex: "01 D2 04 61 02 78 56")
        )
        XCTAssertEqual(androidPayload?.batchId, 1234)
        XCTAssertEqual(androidPayload?.batteryPct, 97)
        XCTAssertEqual(androidPayload?.tableSeqLow16, 0x5678)

        let tableInfo = SmartChassisCodec.parseTableInfo(Data(hex: "78 56 34 12 CD AB 19"))
        XCTAssertEqual(tableInfo?.tableSeq, 0x12345678)
        XCTAssertEqual(tableInfo?.crc16, 0xABCD)
        XCTAssertEqual(tableInfo?.slotCount, 25)
    }

    func testSlotRecordAndCrcParsing() {
        let encoded = SmartChassisCodec.encodeSlotRecord(
            slot: 7,
            partId: "c12345",
            quantity: 330,
            flags: Int(SmartChassisProtocol.slotFlagLowStock)
        )

        XCTAssertEqual(encoded.count, SmartChassisProtocol.slotRecordSize)
        XCTAssertEqual(SmartChassisCodec.crc8Maxim(encoded.dropLast()), encoded.last)

        let parsed = SmartChassisCodec.parseSlotRecord(encoded)
        XCTAssertEqual(parsed?.slot, 7)
        XCTAssertEqual(parsed?.partId, "C12345")
        XCTAssertEqual(parsed?.quantity, 330)
        XCTAssertEqual(parsed?.flags, SmartChassisProtocol.slotFlagLowStock)

        var corrupted = encoded
        corrupted[11] = 0
        XCTAssertNil(SmartChassisCodec.parseSlotRecord(corrupted))
    }

    func testKnownCrcCheckValuesAndDeviceHealth() {
        let check = Data("123456789".utf8)

        XCTAssertEqual(SmartChassisCodec.crc8Maxim(check), 0xA1)
        XCTAssertEqual(SmartChassisCodec.crc16CcittFalse(check), 0x29B1)

        let health = SmartChassisCodec.parseDeviceHealth(Data(hex: "64 02 00 00"))
        XCTAssertEqual(health?.batteryPct, 100)
        XCTAssertEqual(health?.resetReason, 0x0002)
        XCTAssertEqual(health?.healthFlags, 0x00)
    }

    func testLightStatusAndReadAllEndParsing() {
        let status = SmartChassisCodec.parseLightStatus(Data(hex: "02 1E 00"))
        XCTAssertEqual(status?.mode, .pick)
        XCTAssertEqual(status?.remainingSeconds, 30)

        XCTAssertTrue(SmartChassisCodec.isReadAllEndPayload(Data(hex: "FF")))
        XCTAssertFalse(SmartChassisCodec.isReadAllEndPayload(Data(hex: "FF 00")))
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

private struct HardwareProtocolVectors: Decodable {
    let version: String
    let slotCount: Int
    let slotRecordSize: Int
    let lightCommandSize: Int
    let binding: [String: HardwareProtocolVector]
    let light: [String: HardwareProtocolVector]

    enum CodingKeys: String, CodingKey {
        case version
        case slotCount = "slot_count"
        case slotRecordSize = "slot_record_size"
        case lightCommandSize = "light_command_size"
        case binding
        case light
    }

    static func load() throws -> Self {
        let bundle = Bundle(for: SmartChassisProtocolVectorTests.self)
        let url = try XCTUnwrap(
            bundle.url(
                forResource: "test-vectors",
                withExtension: "json",
                subdirectory: "ProtocolFixtures"
            )
        )
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(Self.self, from: data)
    }
}

private struct HardwareProtocolVector: Decodable {
    let hex: String
    let description: String

    var data: Data {
        Data(hex: hex)
    }
}

private extension Dictionary where Key == String, Value == HardwareProtocolVector {
    func requiredData(_ key: String) throws -> Data {
        try XCTUnwrap(self[key], "Missing hardware protocol vector: \(key)").data
    }
}

private extension LightCommand {
    static let findSlot1Green30s = LightCommand(
        mode: .find,
        maskA: SmartChassisCodec.slotMask(slot: 1),
        maskB: 0,
        colorA: .green,
        colorB: .black,
        timeoutSeconds: 30
    )

    static let pickSlots1_7_25Green30s = LightCommand(
        mode: .pick,
        maskA: SmartChassisCodec.slotMask(slot: 1) |
            SmartChassisCodec.slotMask(slot: 7) |
            SmartChassisCodec.slotMask(slot: 25),
        maskB: 0,
        colorA: .green,
        colorB: .black,
        timeoutSeconds: 30
    )

    static let off = LightCommand(
        mode: .off,
        maskA: 0,
        maskB: 0,
        colorA: .black,
        colorB: .black,
        timeoutSeconds: 0
    )
}

private extension RGBColor {
    static let green = RGBColor(red: 0, green: 255, blue: 0)
    static let black = RGBColor(red: 0, green: 0, blue: 0)
}
