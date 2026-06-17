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

    func testPrinterPreviewUsesReadableHorizontalCanvas() throws {
        let label = BoxLayerLabel(positionCode: "BOX01-L01", partNumber: "C17710")

        let preview = try BoxLayerLabelRenderer.renderPrinterPreview(label: label)
        let p0PrintImage = try BoxLayerLabelRenderer.renderP0PrintImage(label: label)

        XCTAssertEqual(preview.size, p0PrintImage.size)
        XCTAssertNotEqual(preview.pngData(), p0PrintImage.pngData())
        XCTAssertGreaterThan(blackPixelCount(in: preview, rect: CGRect(x: 20, y: 30, width: 344, height: 170)), 1_000)
    }

    func testP0DirectBluetoothTransportIsAvailable() {
        XCTAssertTrue(P0DirectBluetoothSupport.current.isAvailable)
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

    private func blackPixelCount(in image: UIImage, rect: CGRect) -> Int {
        guard let cgImage = image.cgImage else { return 0 }

        let width = cgImage.width
        let height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return 0
        }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        let minX = max(0, Int(rect.minX))
        let maxX = min(width, Int(rect.maxX))
        let minY = max(0, Int(rect.minY))
        let maxY = min(height, Int(rect.maxY))
        var count = 0
        for y in minY..<maxY {
            for x in minX..<maxX {
                let offset = (y * width + x) * 4
                let luminance = (Int(pixels[offset]) * 299 + Int(pixels[offset + 1]) * 587 + Int(pixels[offset + 2]) * 114) / 1000
                if luminance < 150 {
                    count += 1
                }
            }
        }
        return count
    }
}
