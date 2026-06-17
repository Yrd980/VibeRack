import SwiftUI

struct AppView: View {
    @State private var selectedTab: AppTab = .chassis
    @State private var tabRouter = TabRouter()
    @State private var dependencies = DependencyGraph.live()

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack(path: tabRouter.binding(for: tab)) {
                    tab.makeContentView(dependencies: dependencies)
                        .navigationDestination(for: Route.self) { route in
                            switch route {
                            case .chassisDetail(let id):
                                ChassisDetailView(
                                    chassisID: id,
                                    repository: dependencies.chassisRepository,
                                    workflow: dependencies.chassisWorkflow
                                )
                            }
                        }
                }
                .environment(tabRouter.router(for: tab))
                .tabItem { tab.label }
                .tag(tab)
            }
        }
    }
}

#Preview {
    AppView()
}
