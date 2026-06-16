import CoreBluetooth
import Foundation
import Observation

public struct SmartChassisReadSnapshot: Equatable {
    public let tableInfo: TableInfo
    public let lightStatus: LightStatus
    public let deviceHealth: DeviceHealth
    public let batteryLevel: UInt8?
    public let deviceInformation: SmartChassisDeviceInformation

    public init(
        tableInfo: TableInfo,
        lightStatus: LightStatus,
        deviceHealth: DeviceHealth,
        batteryLevel: UInt8?,
        deviceInformation: SmartChassisDeviceInformation
    ) {
        self.tableInfo = tableInfo
        self.lightStatus = lightStatus
        self.deviceHealth = deviceHealth
        self.batteryLevel = batteryLevel
        self.deviceInformation = deviceInformation
    }
}

public enum SmartChassisConnectionPhase: Equatable {
    case disconnected
    case connecting
    case discovering
    case connected
    case disconnecting
}

@MainActor
@Observable
public final class SmartChassisCentral: NSObject {
    public private(set) var phase: SmartChassisConnectionPhase = .disconnected
    public private(set) var bluetoothState: CBManagerState = .unknown
    public private(set) var connectedDiscovery: SmartChassisDiscovery?
    public private(set) var lastError: SmartChassisBluetoothError?

    @ObservationIgnored private let queue: DispatchQueue?
    @ObservationIgnored private lazy var central = CBCentralManager(delegate: self, queue: queue)
    @ObservationIgnored private var peripheral: CBPeripheral?
    @ObservationIgnored private var discoveredCharacteristics: [CBUUID: CBCharacteristic] = [:]
    @ObservationIgnored private var pendingCharacteristicServices: Set<CBUUID> = []
    @ObservationIgnored private var connectContinuation: CheckedContinuation<Void, Error>?
    @ObservationIgnored private var serviceDiscoveryContinuation: CheckedContinuation<Void, Error>?
    @ObservationIgnored private var readContinuations: [CBUUID: CheckedContinuation<Data, Error>] = [:]
    @ObservationIgnored private var writeContinuations: [CBUUID: CheckedContinuation<Void, Error>] = [:]

    public init(queue: DispatchQueue? = nil) {
        self.queue = queue
        super.init()
        _ = central
    }

    public func connect(to discovery: SmartChassisDiscovery, peripheral: CBPeripheral) async throws {
        lastError = nil
        guard central.state == .poweredOn else {
            throw fail(Self.error(for: central.state) ?? .bluetoothPoweredOff)
        }

        self.peripheral = peripheral
        self.connectedDiscovery = discovery
        self.discoveredCharacteristics.removeAll()
        self.pendingCharacteristicServices.removeAll()
        peripheral.delegate = self
        phase = .connecting

        try await withCheckedThrowingContinuation { continuation in
            connectContinuation = continuation
            central.connect(peripheral)
        }

        phase = .discovering
        try await discoverRequiredServicesAndCharacteristics(on: peripheral)
        phase = .connected
    }

    public func disconnect() {
        guard let peripheral else {
            phase = .disconnected
            connectedDiscovery = nil
            return
        }
        phase = .disconnecting
        central.cancelPeripheralConnection(peripheral)
    }

    public func readTableInfo() async throws -> TableInfo {
        let data = try await readCharacteristic(SmartChassisBluetoothUUIDs.tableInfo)
        guard let tableInfo = SmartChassisCodec.parseTableInfo(data) else {
            throw fail(.invalidPayload(SmartChassisBluetoothUUIDs.tableInfo))
        }
        return tableInfo
    }

    public func readLightStatus() async throws -> LightStatus {
        let data = try await readCharacteristic(SmartChassisBluetoothUUIDs.lightStatus)
        guard let status = SmartChassisCodec.parseLightStatus(data) else {
            throw fail(.invalidPayload(SmartChassisBluetoothUUIDs.lightStatus))
        }
        return status
    }

    public func readDeviceHealth() async throws -> DeviceHealth {
        let data = try await readCharacteristic(SmartChassisBluetoothUUIDs.deviceHealth)
        guard let health = SmartChassisCodec.parseDeviceHealth(data) else {
            throw fail(.invalidPayload(SmartChassisBluetoothUUIDs.deviceHealth))
        }
        return health
    }

    public func readBatteryLevel() async throws -> UInt8? {
        guard discoveredCharacteristics[SmartChassisBluetoothUUIDs.batteryLevel] != nil else {
            return nil
        }
        return try await readCharacteristic(SmartChassisBluetoothUUIDs.batteryLevel).first
    }

    public func readDeviceInformation() async throws -> SmartChassisDeviceInformation {
        var info = SmartChassisDeviceInformation()
        for uuid in SmartChassisBluetoothUUIDs.deviceInformationCharacteristics
            where discoveredCharacteristics[uuid] != nil {
            let data = try await readCharacteristic(uuid)
            let value = String(data: data, encoding: .utf8)
            switch uuid {
            case SmartChassisBluetoothUUIDs.manufacturerName:
                info.manufacturerName = value
            case SmartChassisBluetoothUUIDs.modelNumber:
                info.modelNumber = value
            case SmartChassisBluetoothUUIDs.serialNumber:
                info.serialNumber = value
            case SmartChassisBluetoothUUIDs.hardwareRevision:
                info.hardwareRevision = value
            case SmartChassisBluetoothUUIDs.firmwareRevision:
                info.firmwareRevision = value
            case SmartChassisBluetoothUUIDs.softwareRevision:
                info.softwareRevision = value
            default:
                break
            }
        }
        return info
    }

    public func readStatusSnapshot() async throws -> SmartChassisReadSnapshot {
        async let tableInfo = readTableInfo()
        async let lightStatus = readLightStatus()
        async let deviceHealth = readDeviceHealth()
        async let batteryLevel = readBatteryLevel()
        async let deviceInformation = readDeviceInformation()
        return try await SmartChassisReadSnapshot(
            tableInfo: tableInfo,
            lightStatus: lightStatus,
            deviceHealth: deviceHealth,
            batteryLevel: batteryLevel,
            deviceInformation: deviceInformation
        )
    }

    public func writeEncryptedBindingCommand(_ data: Data) async throws {
        try await write(
            data,
            to: SmartChassisBluetoothUUIDs.bindingControlPoint,
            type: .withResponse
        )
    }

    public func sendLightCommand(_ command: LightCommand) async throws {
        try await write(
            SmartChassisCodec.encodeLightCommand(command),
            to: SmartChassisBluetoothUUIDs.lightCommand,
            type: .withoutResponse
        )
    }

    private func discoverRequiredServicesAndCharacteristics(on peripheral: CBPeripheral) async throws {
        try await withCheckedThrowingContinuation { continuation in
            serviceDiscoveryContinuation = continuation
            peripheral.discoverServices(SmartChassisBluetoothUUIDs.discoveryServices)
        }
    }

    private func readCharacteristic(_ uuid: CBUUID) async throws -> Data {
        guard let peripheral else {
            throw fail(.peripheralUnavailable)
        }
        guard let characteristic = discoveredCharacteristics[uuid] else {
            throw fail(.characteristicMissing(uuid))
        }
        return try await withCheckedThrowingContinuation { continuation in
            readContinuations[uuid] = continuation
            peripheral.readValue(for: characteristic)
        }
    }

    private func write(_ data: Data, to uuid: CBUUID, type: CBCharacteristicWriteType) async throws {
        guard let peripheral else {
            throw fail(.peripheralUnavailable)
        }
        guard let characteristic = discoveredCharacteristics[uuid] else {
            throw fail(.characteristicMissing(uuid))
        }
        try await withCheckedThrowingContinuation { continuation in
            writeContinuations[uuid] = continuation
            peripheral.writeValue(data, for: characteristic, type: type)
        }
    }

    private func fail(_ error: SmartChassisBluetoothError) -> SmartChassisBluetoothError {
        lastError = error
        return error
    }

    private static func error(for state: CBManagerState) -> SmartChassisBluetoothError? {
        switch state {
        case .unknown, .resetting:
            return .bluetoothUnsupportedState(state)
        case .unsupported:
            return .bluetoothUnavailable
        case .unauthorized:
            return .bluetoothUnauthorized
        case .poweredOff:
            return .bluetoothPoweredOff
        case .poweredOn:
            return nil
        @unknown default:
            return .bluetoothUnsupportedState(state)
        }
    }
}

extension SmartChassisCentral: CBCentralManagerDelegate {
    public nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            bluetoothState = central.state
            if let error = Self.error(for: central.state) {
                lastError = error
                if phase != .disconnected {
                    disconnect()
                }
            }
        }
    }

    public nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        Task { @MainActor in
            connectContinuation?.resume()
            connectContinuation = nil
        }
    }

    public nonisolated func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        Task { @MainActor in
            let mapped = SmartChassisBluetoothError.mapCoreBluetoothError(error) ?? .connectionFailed("unknown")
            connectContinuation?.resume(throwing: fail(mapped))
            connectContinuation = nil
            phase = .disconnected
        }
    }

    public nonisolated func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        Task { @MainActor in
            if let error {
                lastError = .disconnected(error.localizedDescription)
            }
            self.peripheral = nil
            discoveredCharacteristics.removeAll()
            connectedDiscovery = nil
            phase = .disconnected
        }
    }
}

extension SmartChassisCentral: CBPeripheralDelegate {
    public nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverServices error: Error?
    ) {
        Task { @MainActor in
            if let mapped = SmartChassisBluetoothError.mapCoreBluetoothError(error) {
                serviceDiscoveryContinuation?.resume(throwing: fail(mapped))
                serviceDiscoveryContinuation = nil
                return
            }

            for service in peripheral.services ?? [] {
                let characteristicUUIDs: [CBUUID]?
                switch service.uuid {
                case SmartChassisBluetoothUUIDs.bindingTableService:
                    characteristicUUIDs = [
                        SmartChassisBluetoothUUIDs.bindingControlPoint,
                        SmartChassisBluetoothUUIDs.tableInfo
                    ]
                case SmartChassisBluetoothUUIDs.lightService:
                    characteristicUUIDs = [
                        SmartChassisBluetoothUUIDs.lightCommand,
                        SmartChassisBluetoothUUIDs.lightStatus
                    ]
                case SmartChassisBluetoothUUIDs.deviceHealthService:
                    characteristicUUIDs = [SmartChassisBluetoothUUIDs.deviceHealth]
                case SmartChassisBluetoothUUIDs.batteryService:
                    characteristicUUIDs = [SmartChassisBluetoothUUIDs.batteryLevel]
                case SmartChassisBluetoothUUIDs.deviceInformationService:
                    characteristicUUIDs = SmartChassisBluetoothUUIDs.deviceInformationCharacteristics
                default:
                    characteristicUUIDs = nil
                }
                peripheral.discoverCharacteristics(characteristicUUIDs, for: service)
                pendingCharacteristicServices.insert(service.uuid)
            }

            for uuid in requiredServiceUUIDs where !(peripheral.services ?? []).contains(where: { $0.uuid == uuid }) {
                serviceDiscoveryContinuation?.resume(throwing: fail(.serviceMissing(uuid)))
                serviceDiscoveryContinuation = nil
                return
            }
        }
    }

    public nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        Task { @MainActor in
            if let mapped = SmartChassisBluetoothError.mapCoreBluetoothError(error) {
                serviceDiscoveryContinuation?.resume(throwing: fail(mapped))
                serviceDiscoveryContinuation = nil
                return
            }

            for characteristic in service.characteristics ?? [] {
                discoveredCharacteristics[characteristic.uuid] = characteristic
            }

            pendingCharacteristicServices.remove(service.uuid)
            guard pendingCharacteristicServices.isEmpty else {
                return
            }

            if let missing = missingRequiredCharacteristic {
                serviceDiscoveryContinuation?.resume(throwing: fail(.characteristicMissing(missing)))
                serviceDiscoveryContinuation = nil
            } else {
                serviceDiscoveryContinuation?.resume()
                serviceDiscoveryContinuation = nil
            }
        }
    }

    public nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        Task { @MainActor in
            let uuid = characteristic.uuid
            guard let continuation = readContinuations.removeValue(forKey: uuid) else {
                return
            }
            if let mapped = SmartChassisBluetoothError.mapCoreBluetoothError(error, characteristic: uuid) {
                continuation.resume(throwing: fail(mapped))
                return
            }
            guard let value = characteristic.value else {
                continuation.resume(throwing: fail(.invalidPayload(uuid)))
                return
            }
            continuation.resume(returning: value)
        }
    }

    public nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        Task { @MainActor in
            let uuid = characteristic.uuid
            guard let continuation = writeContinuations.removeValue(forKey: uuid) else {
                return
            }
            if let mapped = SmartChassisBluetoothError.mapCoreBluetoothError(error, characteristic: uuid) {
                continuation.resume(throwing: fail(mapped))
                return
            }
            continuation.resume()
        }
    }

    private var hasDiscoveredRequiredCharacteristics: Bool {
        missingRequiredCharacteristic == nil
    }

    private var missingRequiredCharacteristic: CBUUID? {
        SmartChassisBluetoothUUIDs.requiredReadCharacteristics.first {
            discoveredCharacteristics[$0] == nil
        }
    }

    private var requiredServiceUUIDs: [CBUUID] {
        [
            SmartChassisBluetoothUUIDs.bindingTableService,
            SmartChassisBluetoothUUIDs.lightService,
            SmartChassisBluetoothUUIDs.deviceHealthService
        ]
    }
}
