import XCTest
@testable import VibeRack

final class LcscQrParserTests: XCTestCase {
    func testParsesLcscPartNumberQuantityAndManufacturerPartNumber() throws {
        let payload = try LcscQrParser.parse("{on:SO123,pc:C2829702,pm:STM32G030F6P6,qty:25,mc:,cc:,pdi:,hp:}")

        XCTAssertEqual(payload.orderNo, "SO123")
        XCTAssertEqual(payload.partNumber, "C2829702")
        XCTAssertEqual(payload.manufacturerPartNo, "STM32G030F6P6")
        XCTAssertEqual(payload.quantity, 25)
        XCTAssertEqual(payload.extraFields["pc"], "C2829702")
    }

    func testParsesQuotedKeysCaseInsensitivelyAndDefaultsMissingQuantityToZero() throws {
        let payload = try LcscQrParser.parse("{\"PC\":\"c7654321\",\"PM\":\"NE555DR\",\"qty\":\"\"}")

        XCTAssertEqual(payload.partNumber, "C7654321")
        XCTAssertEqual(payload.manufacturerPartNo, "NE555DR")
        XCTAssertEqual(payload.quantity, 0)
    }

    func testRejectsNonLcscQrWithoutMisidentifyingIt() {
        XCTAssertThrowsError(try LcscQrParser.parse("https://example.com/C1234567")) { error in
            XCTAssertEqual(error as? LcscQrParserError, .notLcscQr)
        }
    }

    func testMissingPcFieldProducesUnderstandableError() {
        XCTAssertThrowsError(try LcscQrParser.parse("{on:SO123,qty:12}")) { error in
            XCTAssertEqual(error as? LcscQrParserError, .missingPartNumber)
        }
    }

    func testErrorDescriptionsAreHumanReadable() {
        XCTAssertEqual(LcscQrParserError.notLcscQr.errorDescription, "不是可识别的 LCSC QR payload")
        XCTAssertEqual(LcscQrParserError.missingPartNumber.errorDescription, "LCSC QR payload 缺少 pc 料号字段")
    }
}
