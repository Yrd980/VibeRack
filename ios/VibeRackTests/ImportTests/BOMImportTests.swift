import XCTest
@testable import VibeRack

final class BOMImportTests: XCTestCase {
    func testImportsSampleXlsxWithExpectedHeadersAndFiftyDataRows() throws {
        let workbook = try BOMWorkbookImporter.importXLSX(at: sampleBOMURL())

        XCTAssertEqual(workbook.sheetName, "BOM_原版（已验证）_PCB1_2_2026-06-10")
        XCTAssertEqual(workbook.headers, [
            "No.",
            "Quantity",
            "Comment",
            "Footprint",
            "Value",
            "Manufacturer Part",
            "Manufacturer",
            "Supplier Part",
            "Supplier",
            "Designator"
        ])
        XCTAssertEqual(workbook.lines.count, 50)
    }

    func testImportsKnownSupplierPartRowFromSampleXlsx() throws {
        let workbook = try BOMWorkbookImporter.importXLSX(at: sampleBOMURL())

        let line = try XCTUnwrap(workbook.lines.first { $0.supplierPart == "C2829702" })
        XCTAssertEqual(line.quantity, 1)
        XCTAssertEqual(line.comment, "1.25-2A")
        XCTAssertEqual(line.footprint, "CONN-TH_1.25-2A")
        XCTAssertEqual(line.value, nil)
        XCTAssertEqual(line.manufacturerPart, "1.25-2A")
        XCTAssertEqual(line.manufacturer, "FG(富港)")
        XCTAssertEqual(line.supplier, "LCSC")
        XCTAssertEqual(line.designator, "J2")
    }

    func testKeepsPassiveRowsWithoutSupplierPartForLaterMatching() throws {
        let workbook = try BOMWorkbookImporter.importXLSX(at: sampleBOMURL())

        let line = try XCTUnwrap(workbook.lines.first { $0.designator == "C29" })
        XCTAssertNil(line.supplierPart)
        XCTAssertEqual(line.quantity, 1)
        XCTAssertEqual(line.comment, "0.01UF")
        XCTAssertEqual(line.footprint, "0603")
        XCTAssertEqual(line.value, "0.01UF")
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
