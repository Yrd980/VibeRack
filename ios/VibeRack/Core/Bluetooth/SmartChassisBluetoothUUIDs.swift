import CoreBluetooth
import Foundation

public enum SmartChassisBluetoothUUIDs {
    public static let bindingTableService = CBUUID(string: "7f4b0001-8d1a-4d45-9a4e-2b4a7c000000")
    public static let bindingControlPoint = CBUUID(string: "7f4b1001-8d1a-4d45-9a4e-2b4a7c000000")
    public static let tableInfo = CBUUID(string: "7f4b1002-8d1a-4d45-9a4e-2b4a7c000000")

    public static let lightService = CBUUID(string: "7f4b0002-8d1a-4d45-9a4e-2b4a7c000000")
    public static let lightCommand = CBUUID(string: "7f4b2001-8d1a-4d45-9a4e-2b4a7c000000")
    public static let lightStatus = CBUUID(string: "7f4b2002-8d1a-4d45-9a4e-2b4a7c000000")

    public static let deviceHealthService = CBUUID(string: "7f4b0003-8d1a-4d45-9a4e-2b4a7c000000")
    public static let deviceHealth = CBUUID(string: "7f4b3001-8d1a-4d45-9a4e-2b4a7c000000")

    public static let batteryService = CBUUID(string: "180F")
    public static let batteryLevel = CBUUID(string: "2A19")

    public static let deviceInformationService = CBUUID(string: "180A")
    public static let manufacturerName = CBUUID(string: "2A29")
    public static let modelNumber = CBUUID(string: "2A24")
    public static let serialNumber = CBUUID(string: "2A25")
    public static let hardwareRevision = CBUUID(string: "2A27")
    public static let firmwareRevision = CBUUID(string: "2A26")
    public static let softwareRevision = CBUUID(string: "2A28")

    public static let discoveryServices: [CBUUID] = [
        bindingTableService,
        lightService,
        deviceHealthService,
        batteryService,
        deviceInformationService
    ]

    public static let requiredReadCharacteristics: Set<CBUUID> = [
        tableInfo,
        lightStatus,
        deviceHealth
    ]

    public static let deviceInformationCharacteristics: [CBUUID] = [
        manufacturerName,
        modelNumber,
        serialNumber,
        hardwareRevision,
        firmwareRevision,
        softwareRevision
    ]
}
