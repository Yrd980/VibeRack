import CoreBluetooth
import Foundation
import Observation

@MainActor
@Observable
public final class SmartChassisScanner: NSObject {
    public private(set) var discoveries: [SmartChassisDiscovery] = []
    public private(set) var isScanning = false
    public private(set) var bluetoothState: CBManagerState = .unknown
    public private(set) var lastError: SmartChassisBluetoothError?

    @ObservationIgnored private let queue: DispatchQueue?
    @ObservationIgnored private lazy var central = CBCentralManager(delegate: self, queue: queue)
    @ObservationIgnored private var peripheralsByIdentifier: [UUID: CBPeripheral] = [:]
    @ObservationIgnored private var scanStopTask: Task<Void, Never>?

    public init(queue: DispatchQueue? = nil) {
        self.queue = queue
        super.init()
        _ = central
    }

    public func startScan(duration: Duration? = .seconds(8)) {
        lastError = nil
        guard central.state == .poweredOn else {
            lastError = Self.error(for: central.state)
            isScanning = false
            return
        }

        discoveries.removeAll()
        peripheralsByIdentifier.removeAll()
        isScanning = true
        central.scanForPeripherals(
            withServices: nil,
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )

        scanStopTask?.cancel()
        if let duration {
            scanStopTask = Task { @MainActor [weak self] in
                try? await Task.sleep(for: duration)
                self?.stopScan()
            }
        }
    }

    public func stopScan() {
        scanStopTask?.cancel()
        scanStopTask = nil
        central.stopScan()
        isScanning = false
    }

    public func peripheral(for discovery: SmartChassisDiscovery) -> CBPeripheral? {
        peripheralsByIdentifier[discovery.peripheralIdentifier]
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

extension SmartChassisScanner: CBCentralManagerDelegate {
    public nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            bluetoothState = central.state
            lastError = Self.error(for: central.state)
            if central.state != .poweredOn {
                stopScan()
            }
        }
    }

    public nonisolated func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard let discovery = SmartChassisAdvertisementParser.discovery(
            peripheral: peripheral,
            advertisementData: advertisementData,
            rssi: RSSI
        ) else {
            return
        }

        Task { @MainActor in
            peripheralsByIdentifier[peripheral.identifier] = peripheral
            discoveries = (discoveries.filter { $0.peripheralIdentifier != peripheral.identifier } + [discovery])
                .sorted { lhs, rhs in
                    lhs.displayName.localizedStandardCompare(rhs.displayName) == .orderedAscending
                }
            lastError = nil
        }
    }
}
