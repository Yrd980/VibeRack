import XCTest
@testable import VibeRack

@MainActor
final class RouterTests: XCTestCase {
    func testTabRouterCreatesStableIndependentRouterForEveryTab() {
        let tabRouter = TabRouter()

        XCTAssertEqual(Set(tabRouter.configuredTabs), Set(AppTab.allCases))

        let routers = AppTab.allCases.map { tabRouter.router(for: $0) }
        XCTAssertEqual(routers.count, AppTab.allCases.count)
        XCTAssertEqual(Set(routers.map(ObjectIdentifier.init)).count, AppTab.allCases.count)

        tabRouter.router(for: .chassis).path.append(.chassisDetail(id: "simulator"))

        XCTAssertEqual(tabRouter.router(for: .chassis).path, [.chassisDetail(id: "simulator")])
        XCTAssertEqual(tabRouter.router(for: .stockIn).path, [])
        XCTAssertEqual(tabRouter.router(for: .search).path, [])
        XCTAssertEqual(tabRouter.router(for: .settings).path, [])
    }

    func testBindingUpdatesOnlySelectedTabPath() {
        let tabRouter = TabRouter()
        let chassisBinding = tabRouter.binding(for: .chassis)

        chassisBinding.wrappedValue = [.chassisDetail(id: "VBRK-0000")]

        XCTAssertEqual(tabRouter.router(for: .chassis).path, [.chassisDetail(id: "VBRK-0000")])
        XCTAssertEqual(tabRouter.router(for: .search).path, [])
    }
}
