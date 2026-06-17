import XCTest
@testable import VibeRack

final class BOMPickPlannerTests: XCTestCase {
    func testBuildPickPlanGroupsMatchedSmartChassisSlotsAndBuildsPickCommands() throws {
        let planner = BOMPickPlanner()
        let rows = [
            BOMLine(rowNumber: 1, supplierPart: "c1111111", manufacturerPart: nil, designator: "R1"),
            BOMLine(rowNumber: 2, supplierPart: "C3333333", manufacturerPart: nil, designator: "C5"),
            BOMLine(rowNumber: 3, supplierPart: "C9999999", manufacturerPart: nil, designator: "U1")
        ]
        let stock = [
            StockSearchResult(
                id: "stock-1",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-1",
                slotNumber: 1,
                protocolPartId: "C1111111",
                quantity: 5,
                flags: 0
            ),
            StockSearchResult(
                id: "stock-3",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-3",
                slotNumber: 3,
                protocolPartId: "C3333333",
                quantity: 9,
                flags: 0
            ),
            StockSearchResult(
                id: "stock-4",
                chassisID: "chassis-b",
                chassisCode: "VBRK-0001",
                chassisDisplayName: "VBRK-0001",
                slotID: "slot-4",
                slotNumber: 4,
                protocolPartId: "C3333333",
                quantity: 2,
                flags: 0
            )
        ]

        let plan = planner.buildPickPlan(lines: rows, stock: stock)

        XCTAssertEqual(plan.matchedLineCount, 2)
        XCTAssertEqual(plan.unmatchedLines.map(\.supplierPart), ["C9999999"])
        XCTAssertEqual(plan.groups.map(\.chassisCode), ["VBRK-0000", "VBRK-0001"])

        let firstGroup = try XCTUnwrap(plan.groups.first)
        XCTAssertEqual(firstGroup.slotNumbers, [1, 3])
        XCTAssertEqual(firstGroup.targets.map(\.designator), ["R1", "C5"])
        XCTAssertEqual(firstGroup.makePickLightCommand().mode, .pick)
        XCTAssertEqual(
            firstGroup.makePickLightCommand().maskA,
            SmartChassisCodec.slotMask(slot: 1) | SmartChassisCodec.slotMask(slot: 3)
        )
        XCTAssertEqual(firstGroup.makePickLightCommand().colorA, RGBColor(0, 255, 0))
        XCTAssertEqual(firstGroup.makePickLightCommand().timeoutSeconds, SmartChassisProtocol.defaultLightTimeoutSeconds)

        let secondGroup = try XCTUnwrap(plan.groups.dropFirst().first)
        XCTAssertEqual(secondGroup.slotNumbers, [4])
        XCTAssertEqual(secondGroup.targets.map(\.designator), ["C5"])
        XCTAssertEqual(secondGroup.makePickLightCommand().maskA, SmartChassisCodec.slotMask(slot: 4))
    }

    func testBuildPickPlanMatchesImportedSupplierPartRows() throws {
        let planner = BOMPickPlanner()
        let workbook = try BOMWorkbookImporter.importXLSX(at: sampleBOMURL())
        let importedLine = try XCTUnwrap(workbook.lines.first { $0.supplierPart == "C2829702" })
        let stock = [
            StockSearchResult(
                id: "stock-c2829702",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-7",
                slotNumber: 7,
                protocolPartId: "C2829702",
                quantity: 8,
                flags: 0
            )
        ]

        let plan = planner.buildPickPlan(lines: [importedLine], stock: stock)

        XCTAssertEqual(plan.matchedLineCount, 1)
        XCTAssertTrue(plan.unmatchedLines.isEmpty)
        let group = try XCTUnwrap(plan.groups.first)
        XCTAssertEqual(group.slotNumbers, [7])
        XCTAssertEqual(group.targets.first?.designator, "J2")
        XCTAssertEqual(group.makePickLightCommand().maskA, SmartChassisCodec.slotMask(slot: 7))
    }

    func testBuildRemainingPickPlanOmitsCompletedLineMasks() throws {
        let planner = BOMPickPlanner()
        let rows = [
            BOMLine(id: "line-1", rowNumber: 1, supplierPart: "C1111111", manufacturerPart: nil, designator: "R1"),
            BOMLine(id: "line-2", rowNumber: 2, supplierPart: "C3333333", manufacturerPart: nil, designator: "C5")
        ]
        let stock = [
            StockSearchResult(
                id: "stock-1",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-1",
                slotNumber: 1,
                protocolPartId: "C1111111",
                quantity: 5,
                flags: 0
            ),
            StockSearchResult(
                id: "stock-3",
                chassisID: "chassis-a",
                chassisCode: "VBRK-0000",
                chassisDisplayName: "VBRK-0000",
                slotID: "slot-3",
                slotNumber: 3,
                protocolPartId: "C3333333",
                quantity: 9,
                flags: 0
            )
        ]

        let plan = planner.buildPickPlan(lines: rows, stock: stock, completedLineIDs: ["line-1"])

        let group = try XCTUnwrap(plan.groups.first)
        XCTAssertEqual(group.slotNumbers, [3])
        XCTAssertEqual(group.makePickLightCommand().maskA, SmartChassisCodec.slotMask(slot: 3))
        XCTAssertFalse(group.makePickLightCommand().maskA & SmartChassisCodec.slotMask(slot: 1) != 0)
    }

    private func sampleBOMURL() throws -> URL {
        if let bundledURL = Bundle.main.url(forResource: "bom", withExtension: "xlsx") {
            return bundledURL
        }

        if let testBundleURL = Bundle(for: Self.self).url(forResource: "bom", withExtension: "xlsx") {
            return testBundleURL
        }

        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("assets/bom.xlsx")
    }
}
