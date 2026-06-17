import Foundation
import GRDB

@MainActor
@Observable
final class DependencyGraph {
    let chassisRepository: ChassisRepository
    let chassisWorkflow: SmartChassisWorkflow
    let simulatorClient: ChassisSimulatorClient?

    init(
        chassisRepository: ChassisRepository,
        chassisWorkflow: SmartChassisWorkflow,
        simulatorClient: ChassisSimulatorClient? = nil
    ) {
        self.chassisRepository = chassisRepository
        self.chassisWorkflow = chassisWorkflow
        self.simulatorClient = simulatorClient
    }

    static func simulatorPreview() -> DependencyGraph {
        do {
            let database = try DatabaseFactory.makeInMemoryQueue()
            let repository = GRDBChassisRepository(database: database)
            try repository.seedSimulatorData()
            let simulatorClient = ChassisSimulatorClient()
            return DependencyGraph(
                chassisRepository: repository,
                chassisWorkflow: SmartChassisWorkflow(
                    repository: repository,
                    client: simulatorClient
                ),
                simulatorClient: simulatorClient
            )
        } catch {
            preconditionFailure("Failed to create simulator dependencies: \(error)")
        }
    }

    static func live() -> DependencyGraph {
        do {
            let database = try DatabaseFactory.makeAppQueue()
            let repository = GRDBChassisRepository(database: database)
            try repository.seedSimulatorData()
            let simulatorClient = ChassisSimulatorClient()
            return DependencyGraph(
                chassisRepository: repository,
                chassisWorkflow: SmartChassisWorkflow(
                    repository: repository,
                    client: simulatorClient
                ),
                simulatorClient: simulatorClient
            )
        } catch {
            preconditionFailure("Failed to create app dependencies: \(error)")
        }
    }
}
