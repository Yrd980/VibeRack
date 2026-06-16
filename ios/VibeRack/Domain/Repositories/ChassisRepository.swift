import Foundation

public protocol ChassisRepository {
    func seedSimulatorData() throws
    func fetchChassisList() throws -> [SmartChassisSummary]
    func fetchSlots(chassisID: String) throws -> [ChassisSlotState]
}
