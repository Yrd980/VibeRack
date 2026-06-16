import Foundation
import GRDB

@MainActor
@Observable
final class DependencyGraph {
    let chassisRepository: ChassisRepository

    init(chassisRepository: ChassisRepository) {
        self.chassisRepository = chassisRepository
    }

    static func simulatorPreview() -> DependencyGraph {
        do {
            let database = try DatabaseFactory.makeInMemoryQueue()
            let repository = GRDBChassisRepository(database: database)
            try repository.seedSimulatorData()
            return DependencyGraph(chassisRepository: repository)
        } catch {
            preconditionFailure("Failed to create simulator dependencies: \(error)")
        }
    }

    static func live() -> DependencyGraph {
        do {
            let database = try DatabaseFactory.makeAppQueue()
            let repository = GRDBChassisRepository(database: database)
            try repository.seedSimulatorData()
            return DependencyGraph(chassisRepository: repository)
        } catch {
            preconditionFailure("Failed to create app dependencies: \(error)")
        }
    }
}
