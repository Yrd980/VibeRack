import Foundation

public protocol ChassisRepository {
    func seedSimulatorData() throws
    func fetchChassisList() throws -> [SmartChassisSummary]
    func fetchSlots(chassisID: String) throws -> [ChassisSlotState]
    func searchStock(query: String) throws -> [StockSearchResult]
    func bindSlot(
        chassisID: String,
        slotNumber: Int,
        protocolPartId: String,
        quantity: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws
    func setQuantity(
        chassisID: String,
        slotNumber: Int,
        quantity: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws
    func clearSlot(
        chassisID: String,
        slotNumber: Int,
        source: StockOperationSource,
        bleOpcode: UInt8?,
        bleStatus: UInt8?
    ) throws
    func fetchStockOperations(chassisID: String) throws -> [StockOperationRecord]
}
