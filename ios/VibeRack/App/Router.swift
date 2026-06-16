import SwiftUI
import Observation

@MainActor
@Observable
final class RouterPath {
    var path: [Route] = []
}

enum Route: Hashable {
    case chassisDetail(id: String)
}

@MainActor
@Observable
final class TabRouter {
    private let routers: [AppTab: RouterPath]

    var configuredTabs: [AppTab] {
        Array(routers.keys)
    }

    init(tabs: [AppTab] = AppTab.allCases) {
        self.routers = Dictionary(uniqueKeysWithValues: tabs.map { ($0, RouterPath()) })
    }

    func router(for tab: AppTab) -> RouterPath {
        guard let router = routers[tab] else {
            preconditionFailure("No router configured for \(tab.rawValue)")
        }
        return router
    }

    func binding(for tab: AppTab) -> Binding<[Route]> {
        let router = router(for: tab)
        return Binding(
            get: { router.path },
            set: { router.path = $0 }
        )
    }
}
