import SwiftUI

enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case chassis
    case stockIn
    case search
    case settings

    var id: String { rawValue }

    @MainActor
    @ViewBuilder
    func makeContentView(dependencies: DependencyGraph) -> some View {
        switch self {
        case .chassis:
            ChassisListView(repository: dependencies.chassisRepository)
        case .stockIn:
            StockInFlowView(repository: dependencies.chassisRepository)
        case .search:
            SearchView(repository: dependencies.chassisRepository)
        case .settings:
            SettingsView(repository: dependencies.chassisRepository)
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
        case .settings:
            Label("设置", systemImage: "gearshape")
        }
    }
}
