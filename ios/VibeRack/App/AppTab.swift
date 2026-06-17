import SwiftUI

enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case chassis
    case stockIn
    case search
    case printer
    case settings

    var id: String { rawValue }

    @MainActor
    @ViewBuilder
    func makeContentView(dependencies: DependencyGraph) -> some View {
        switch self {
        case .chassis:
            ChassisListView(repository: dependencies.chassisRepository)
        case .stockIn:
            StockInFlowView(
                repository: dependencies.chassisRepository,
                workflow: dependencies.chassisWorkflow
            )
        case .search:
            SearchView(
                repository: dependencies.chassisRepository,
                workflow: dependencies.chassisWorkflow
            )
        case .printer:
            PrinterView()
        case .settings:
            SettingsView(
                repository: dependencies.chassisRepository,
                simulatorClient: dependencies.simulatorClient
            )
        }
    }

    @ViewBuilder
    var label: some View {
        switch self {
        case .chassis:
            Label("底盘", systemImage: "square.grid.3x3.square")
        case .stockIn:
            Label("入库", systemImage: "tray.and.arrow.down")
        case .search:
            Label("搜索", systemImage: "magnifyingglass")
        case .printer:
            Label("打印", systemImage: "printer")
        case .settings:
            Label("设置", systemImage: "gearshape")
        }
    }
}
