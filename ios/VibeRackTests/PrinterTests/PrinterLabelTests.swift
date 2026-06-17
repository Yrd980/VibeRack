import XCTest
@testable import VibeRack

final class PrinterLabelTests: XCTestCase {
    func testBoxLayerLabelNormalizesInputAndKeepsPrintSize() {
        let label = BoxLayerLabel(positionCode: " A-01 ", partNumber: " c2040 ")

        XCTAssertEqual(label.positionCode, "A-01")
        XCTAssertEqual(label.partNumber, "C2040")
        XCTAssertEqual(BoxLayerLabelRenderer.printSizePoints, CGSize(width: 232, height: 384))
    }

    func testBoxLayerLabelRendererProducesNonEmptyPreviewImage() throws {
        let label = BoxLayerLabel(positionCode: "A-01", partNumber: "C2040")

        let image = try BoxLayerLabelRenderer.render(label: label)

        XCTAssertEqual(image.size, BoxLayerLabelRenderer.printSizePoints)
        XCTAssertGreaterThan(image.pngData()?.count ?? 0, 1_000)
    }

    func testBoxLayerLabelRendererProducesP0PrintImageWithoutCroppingTextCanvas() throws {
        let label = BoxLayerLabel(positionCode: "BOX01-L01", partNumber: "C17710")

        let image = try BoxLayerLabelRenderer.renderP0PrintImage(label: label)

        XCTAssertEqual(image.size, BoxLayerLabelRenderer.p0PrintSizePoints)
        XCTAssertGreaterThan(image.pngData()?.count ?? 0, 1_000)
    }

    func testP0DirectBluetoothTransportDocumentsVerifiedBlePath() {
        XCTAssertEqual(
            P0DirectBluetoothSupport.current.userMessage,
            "已验证佟 P0 / 印立方可通过 BLE GATT 位图通道打印；iOS 使用 CoreBluetooth 写入 P0 位图协议，不走 Android 经典蓝牙 SPP/RFCOMM。"
        )
    }

    func testP0BleUUIDsMatchDiscoveredPrinterServices() {
        XCTAssertEqual(P0BlePrinterUUIDs.printService.uuidString, "49535343-FE7D-4AE5-8FA9-9FAFD205E455")
        XCTAssertEqual(P0BlePrinterUUIDs.notifyCharacteristic.uuidString, "49535343-1E4D-4BD9-BA61-23C647249616")
        XCTAssertEqual(P0BlePrinterUUIDs.writeCharacteristic.uuidString, "49535343-8841-43F4-A8D4-ECBE34729BB3")
        XCTAssertEqual(P0BlePrinterUUIDs.advertisedService.uuidString, "18F0")
    }

    func testP0BitmapProtocolBuildsPrintablePageCommands() throws {
        let label = BoxLayerLabel(positionCode: "A-01", partNumber: "C2040")
        let image = try BoxLayerLabelRenderer.renderP0PrintImage(label: label)

        let chunks = try P0BitmapProtocol.buildBitmapPrintChunks(image: image)

        XCTAssertEqual(chunks.first?.bytes.prefix(4), Data([0x1F, 0x20, 0x02, 0x00]))
        XCTAssertEqual(chunks.last?.bytes, Data([0x0C]))
        XCTAssertTrue(chunks.contains { $0.bytes.starts(with: Data([0x1F, 0x2B])) })
    }
}
