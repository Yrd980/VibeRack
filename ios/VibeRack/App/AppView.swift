import SwiftUI

struct AppView: View {
    @State private var selectedTab: AppTab = .chassis
    @State private var tabRouter = TabRouter()

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack(path: tabRouter.binding(for: tab)) {
                    tab.makeContentView()
                        .navigationDestination(for: Route.self) { route in
                            switch route {
                            case .chassisDetail(let id):
                                ChassisDetailView(chassisID: id)
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
