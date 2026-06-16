import Foundation

public enum SmartChassisCodec {
    public static func parseAdvertisement(_ data: Data) -> SmartChassisAdvertisement? {
        guard data.count >= SmartChassisProtocol.advertisementCoreSize else {
            return nil
        }

        return SmartChassisAdvertisement(
            companyId: data.u16Le(at: 0),
            protoVersion: data.u8(at: 2),
            batchId: data.u16Le(at: 3),
            batteryPct: data.u8(at: 5),
            statusFlags: data.u8(at: 6),
            tableSeqLow16: data.u16Le(at: 7)
        )
    }

    public static func parseAndroidManufacturerPayload(
        companyId: UInt16,
        payload: Data
    ) -> SmartChassisAdvertisement? {
        guard payload.count >= SmartChassisProtocol.androidManufacturerPayloadCoreSize else {
            return nil
        }

        return SmartChassisAdvertisement(
            companyId: companyId,
            protoVersion: payload.u8(at: 0),
            batchId: payload.u16Le(at: 1),
            batteryPct: payload.u8(at: 3),
            statusFlags: payload.u8(at: 4),
            tableSeqLow16: payload.u16Le(at: 5)
        )
    }

    public static func encodeSlotRecord(
        slot: Int,
        partId: String,
        quantity: Int,
        flags: Int
    ) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= slot, "slot must be 1..25")
        precondition(0...0xFFFF ~= quantity, "quantity must fit uint16")
        precondition(0...0xFF ~= flags, "flags must fit uint8")

        let normalizedPartId = partId.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard let partBytes = normalizedPartId.data(using: .ascii) else {
            preconditionFailure("part_id must be printable ASCII")
        }
        precondition(!partBytes.isEmpty, "part_id must not be empty")
        precondition(partBytes.count <= 10, "part_id must be at most 10 ASCII bytes")
        precondition(partBytes.allSatisfy { 0x21...0x7E ~= $0 }, "part_id must be printable ASCII")

        var bytes = Data(repeating: 0, count: SmartChassisProtocol.slotRecordSize)
        bytes[0] = UInt8(slot)
        bytes.replaceSubrange(1..<(1 + partBytes.count), with: partBytes)
        bytes.putU16Le(UInt16(quantity), at: 11)
        bytes[13] = UInt8(flags)
        bytes[14] = 0
        bytes[15] = crc8Maxim(bytes, offset: 0, length: SmartChassisProtocol.slotRecordSize - 1)
        return bytes
    }

    public static func parseSlotRecord(
        _ data: Data,
        requireValidCRC: Bool = true
    ) -> SlotRecord? {
        guard data.count == SmartChassisProtocol.slotRecordSize else {
            return nil
        }

        let crc = data.u8(at: 15)
        let computed = crc8Maxim(data, offset: 0, length: SmartChassisProtocol.slotRecordSize - 1)
        if requireValidCRC && crc != computed {
            return nil
        }

        let slot = Int(data.u8(at: 0))
        guard slot <= SmartChassisProtocol.slotCount else {
            return nil
        }

        return SlotRecord(
            slot: slot,
            partId: data.decodePartId(),
            quantity: Int(data.u16Le(at: 11)),
            flags: data.u8(at: 13),
            crc8: crc
        )
    }

    public static func encodeReadOne(slot: Int) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= slot, "slot must be 1..25")
        return Data([BindingOp.readOne.code, UInt8(slot)])
    }

    public static func encodeReadAll() -> Data {
        Data([BindingOp.readAll.code])
    }

    public static func encodeWriteOne(record: Data) -> Data {
        precondition(record.count == SmartChassisProtocol.slotRecordSize, "record must be 16 bytes")
        precondition(1...SmartChassisProtocol.slotCount ~= Int(record.u8(at: 0)), "record slot must be 1..25")
        return Data([BindingOp.writeOne.code]) + record
    }

    public static func encodeClearOne(slot: Int) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= slot, "slot must be 1..25")
        return Data([BindingOp.clearOne.code, UInt8(slot)])
    }

    public static func encodeInsertAt(slot: Int, record: Data) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= slot, "slot must be 1..25")
        precondition(record.count == SmartChassisProtocol.slotRecordSize, "record must be 16 bytes")
        precondition(1...SmartChassisProtocol.slotCount ~= Int(record.u8(at: 0)), "record slot must be 1..25")
        return Data([BindingOp.insertAt.code, UInt8(slot)]) + record
    }

    public static func encodeRemoveAt(slot: Int) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= slot, "slot must be 1..25")
        return Data([BindingOp.removeAt.code, UInt8(slot)])
    }

    public static func encodeMoveBlock(from: Int, to: Int, length: Int) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= from, "from must be 1..25")
        precondition(1...SmartChassisProtocol.slotCount ~= to, "to must be 1..25")
        precondition(1...SmartChassisProtocol.slotCount ~= length, "length must be 1..25")
        precondition(from + length - 1 <= SmartChassisProtocol.slotCount, "from + length must stay within 25 slots")
        precondition(to + length - 1 <= SmartChassisProtocol.slotCount, "to + length must stay within 25 slots")
        return Data([BindingOp.moveBlock.code, UInt8(from), UInt8(to), UInt8(length)])
    }

    public static func encodeSetQuantity(slot: Int, quantity: Int) -> Data {
        precondition(1...SmartChassisProtocol.slotCount ~= slot, "slot must be 1..25")
        precondition(0...0xFFFF ~= quantity, "quantity must fit uint16")

        var bytes = Data(repeating: 0, count: 4)
        bytes[0] = BindingOp.setQuantity.code
        bytes[1] = UInt8(slot)
        bytes.putU16Le(UInt16(quantity), at: 2)
        return bytes
    }

    public static func encodeFactoryReset() -> Data {
        var bytes = Data(repeating: 0, count: 5)
        bytes[0] = BindingOp.factoryReset.code
        bytes.putU32Le(SmartChassisProtocol.factoryResetMagic, at: 1)
        return bytes
    }

    public static func parseBindingResult(_ data: Data) -> BindingResult? {
        guard data.count >= 2 else {
            return nil
        }

        let rawOp = data.u8(at: 0)
        let rawStatus = data.u8(at: 1)
        return BindingResult(
            op: BindingOp(rawValue: rawOp),
            rawOp: rawOp,
            status: BindingStatus(rawValue: rawStatus),
            rawStatus: rawStatus,
            payload: data.dropFirst(2).data
        )
    }

    public static func isReadAllEndPayload(_ data: Data) -> Bool {
        data.count == 1 && data.u8(at: 0) == SmartChassisProtocol.readAllEndMarker
    }

    public static func parseTableInfo(_ data: Data) -> TableInfo? {
        guard data.count == SmartChassisProtocol.tableInfoSize else {
            return nil
        }

        return TableInfo(
            tableSeq: data.u32Le(at: 0),
            crc16: data.u16Le(at: 4),
            slotCount: Int(data.u8(at: 6))
        )
    }

    public static func encodeLightCommand(_ command: LightCommand) -> Data {
        precondition(!command.mode.isUnknown, "mode must be a known light mode")
        precondition(command.maskA <= SmartChassisProtocol.slotMaskMax, "maskA only supports 25 slots")
        precondition(command.maskB <= SmartChassisProtocol.slotMaskMax, "maskB only supports 25 slots")
        precondition(
            0...SmartChassisProtocol.maxLightTimeoutSeconds ~= command.timeoutSeconds,
            "timeoutSeconds must be 0..300"
        )
        precondition(
            command.mode != .fx || 0...SmartChassisProtocol.maxFxTimeoutSeconds ~= command.timeoutSeconds,
            "FX timeoutSeconds must be 0..10"
        )

        var bytes = Data(repeating: 0, count: SmartChassisProtocol.lightCommandSize)
        bytes[0] = command.mode.code
        bytes.putU32Le(command.maskA, at: 1)
        bytes.putU32Le(command.maskB, at: 5)
        bytes.putRGB(command.colorA, at: 9)
        bytes.putRGB(command.colorB, at: 12)
        bytes.putU16Le(UInt16(command.timeoutSeconds), at: 15)
        return bytes
    }

    public static func parseLightStatus(_ data: Data) -> LightStatus? {
        guard data.count == 3 else {
            return nil
        }

        let rawMode = data.u8(at: 0)
        return LightStatus(
            mode: LightMode(rawValue: rawMode),
            rawMode: rawMode,
            remainingSeconds: Int(data.u16Le(at: 1))
        )
    }

    public static func slotMask(slot: Int) -> UInt32 {
        guard 1...SmartChassisProtocol.slotCount ~= slot else {
            return 0
        }

        return 1 << UInt32(slot - 1)
    }

    public static func crc8Maxim(
        _ data: Data,
        offset: Int = 0,
        length: Int? = nil
    ) -> UInt8 {
        let byteCount = length ?? data.count
        precondition(offset >= 0 && byteCount >= 0 && offset + byteCount <= data.count, "CRC range out of bounds")

        var crc: UInt8 = 0x00
        for index in offset..<(offset + byteCount) {
            crc ^= data.u8(at: index)
            for _ in 0..<8 {
                if crc & 0x01 != 0 {
                    crc = (crc >> 1) ^ 0x8C
                } else {
                    crc >>= 1
                }
            }
        }
        return crc
    }

    public static func crc16CcittFalse(
        _ data: Data,
        offset: Int = 0,
        length: Int? = nil
    ) -> UInt16 {
        let byteCount = length ?? data.count
        precondition(offset >= 0 && byteCount >= 0 && offset + byteCount <= data.count, "CRC range out of bounds")

        var crc: UInt16 = 0xFFFF
        for index in offset..<(offset + byteCount) {
            crc ^= UInt16(data.u8(at: index)) << 8
            for _ in 0..<8 {
                if crc & 0x8000 != 0 {
                    crc = (crc << 1) ^ 0x1021
                } else {
                    crc <<= 1
                }
            }
        }
        return crc
    }

    public static func parseDeviceHealth(_ data: Data) -> DeviceHealth? {
        guard data.count == 4 else {
            return nil
        }

        return DeviceHealth(
            batteryPct: data.u8(at: 0),
            resetReason: data.u16Le(at: 1),
            healthFlags: data.u8(at: 3)
        )
    }
}

private extension Data.SubSequence {
    var data: Data {
        Data(self)
    }
}

private extension LightMode {
    var isUnknown: Bool {
        if case .unknown = self {
            return true
        }
        return false
    }
}

private extension Data {
    func u8(at offset: Int) -> UInt8 {
        self[startIndex + offset]
    }

    func u16Le(at offset: Int) -> UInt16 {
        UInt16(u8(at: offset)) | (UInt16(u8(at: offset + 1)) << 8)
    }

    func u32Le(at offset: Int) -> UInt32 {
        UInt32(u8(at: offset)) |
            (UInt32(u8(at: offset + 1)) << 8) |
            (UInt32(u8(at: offset + 2)) << 16) |
            (UInt32(u8(at: offset + 3)) << 24)
    }

    mutating func putU16Le(_ value: UInt16, at offset: Int) {
        self[startIndex + offset] = UInt8(value & 0x00FF)
        self[startIndex + offset + 1] = UInt8((value >> 8) & 0x00FF)
    }

    mutating func putU32Le(_ value: UInt32, at offset: Int) {
        self[startIndex + offset] = UInt8(value & 0x0000_00FF)
        self[startIndex + offset + 1] = UInt8((value >> 8) & 0x0000_00FF)
        self[startIndex + offset + 2] = UInt8((value >> 16) & 0x0000_00FF)
        self[startIndex + offset + 3] = UInt8((value >> 24) & 0x0000_00FF)
    }

    mutating func putRGB(_ color: RGBColor, at offset: Int) {
        self[startIndex + offset] = color.red
        self[startIndex + offset + 1] = color.green
        self[startIndex + offset + 2] = color.blue
    }

    func decodePartId() -> String {
        let partBytes = self[(startIndex + 1)..<(startIndex + 11)]
        let endIndex = partBytes.firstIndex(of: 0) ?? partBytes.endIndex
        return String(data: partBytes[..<endIndex], encoding: .ascii)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
}
