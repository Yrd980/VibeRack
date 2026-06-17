import UIKit

struct P0PrintChunk: Equatable {
    let label: String
    let bytes: Data
    let delayAfterMilliseconds: UInt64
}

enum P0BitmapProtocol {
    static let targetWidthDots = 384
    static let targetHeightDots = 232

    private static let widthBytes = targetWidthDots / 8
    private static let commandPrefix: UInt8 = 0x1F
    private static let commandPageStart: UInt8 = 0x20
    private static let commandPageWidth: UInt8 = 0x27
    private static let commandBitmapPrint: UInt8 = 0x2B
    private static let commandBitmapRepeat: UInt8 = 0x2E
    private static let commandGapType: UInt8 = 0x42
    private static let commandDarkness: UInt8 = 0x43
    private static let commandSpeed: UInt8 = 0x44
    private static let fixedCrc: UInt8 = 0x88

    static func buildBitmapPrintChunks(image: UIImage) throws -> [P0PrintChunk] {
        let rows = try rasterize(image: image)
        var chunks: [P0PrintChunk] = [
            P0PrintChunk(
                label: "p0 page start",
                bytes: buildCommand(commandPageStart, payload: shortBytes(1)) +
                    buildCommand(commandPageWidth, payload: ebv(widthBytes)) +
                    buildCommand(commandGapType, payload: [2]) +
                    buildCommand(commandDarkness, payload: [5]) +
                    buildCommand(commandSpeed, payload: [2]),
                delayAfterMilliseconds: 80
            )
        ]

        var blankRows = 0
        for row in rows {
            if let lastBlackByteIndex = row.lastIndex(where: { $0 != 0 }) {
                if blankRows > 0 {
                    appendBlankRows(blankRows, to: &chunks)
                    blankRows = 0
                }
                let activeLength = lastBlackByteIndex + 1
                var payloadPrefix = Data()
                payloadPrefix.append(contentsOf: ebv(0))
                payloadPrefix.append(contentsOf: ebv(activeLength))
                chunks.append(
                    P0PrintChunk(
                        label: "p0 bitmap row",
                        bytes: Data([commandPrefix, commandBitmapPrint]) + payloadPrefix + row.prefix(activeLength),
                        delayAfterMilliseconds: 2
                    )
                )
            } else {
                blankRows += 1
            }
        }
        if blankRows > 0 {
            appendBlankRows(blankRows, to: &chunks)
        }
        chunks.append(
            P0PrintChunk(
                label: "p0 page print",
                bytes: Data([0x0C]),
                delayAfterMilliseconds: 120
            )
        )
        return chunks
    }

    private static func appendBlankRows(_ count: Int, to chunks: inout [P0PrintChunk]) {
        var remaining = count
        while remaining > 0 {
            let rows = min(remaining, 16_384)
            if rows <= 255 {
                chunks.append(
                    P0PrintChunk(
                        label: "p0 blank rows",
                        bytes: Data([0x1B, 0x4A, UInt8(rows)]),
                        delayAfterMilliseconds: 1
                    )
                )
            } else {
                chunks.append(
                    P0PrintChunk(
                        label: "p0 repeated blank rows",
                        bytes: buildCommand(commandBitmapRepeat, payload: ebv(rows - 1)),
                        delayAfterMilliseconds: 1
                    )
                )
            }
            remaining -= rows
        }
    }

    private static func rasterize(image: UIImage) throws -> [Data] {
        guard let cgImage = image.cgImage else {
            throw P0BitmapProtocolError.unreadableImage
        }

        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue
        var pixels = [UInt8](repeating: 0, count: targetWidthDots * targetHeightDots * 4)
        guard let context = CGContext(
            data: &pixels,
            width: targetWidthDots,
            height: targetHeightDots,
            bitsPerComponent: 8,
            bytesPerRow: targetWidthDots * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: bitmapInfo
        ) else {
            throw P0BitmapProtocolError.unreadableImage
        }

        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: targetWidthDots, height: targetHeightDots))
        let sourceSize = CGSize(width: cgImage.width, height: cgImage.height)
        let targetAspect = CGFloat(targetWidthDots) / CGFloat(targetHeightDots)
        let cropRect = centerCropRect(sourceSize: sourceSize, targetAspect: targetAspect)
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: targetWidthDots, height: targetHeightDots), byTiling: false)

        if cropRect != CGRect(origin: .zero, size: sourceSize) {
            context.setFillColor(UIColor.white.cgColor)
            context.fill(CGRect(x: 0, y: 0, width: targetWidthDots, height: targetHeightDots))
            if let cropped = cgImage.cropping(to: cropRect) {
                context.draw(cropped, in: CGRect(x: 0, y: 0, width: targetWidthDots, height: targetHeightDots))
            }
        }

        return (0..<targetHeightDots).map { y in
            var row = [UInt8](repeating: 0, count: widthBytes)
            for x in 0..<targetWidthDots {
                let offset = (y * targetWidthDots + x) * 4
                let red = Int(pixels[offset])
                let green = Int(pixels[offset + 1])
                let blue = Int(pixels[offset + 2])
                let alpha = Int(pixels[offset + 3])
                let luminance = (red * 299 + green * 587 + blue * 114) / 1000
                if alpha >= 128 && luminance < 150 {
                    row[x / 8] |= UInt8(0x80 >> (x % 8))
                }
            }
            return Data(row)
        }
    }

    private static func centerCropRect(sourceSize: CGSize, targetAspect: CGFloat) -> CGRect {
        let sourceAspect = sourceSize.width / sourceSize.height
        if sourceAspect > targetAspect {
            let croppedWidth = sourceSize.height * targetAspect
            return CGRect(
                x: (sourceSize.width - croppedWidth) / 2,
                y: 0,
                width: croppedWidth,
                height: sourceSize.height
            )
        }
        let croppedHeight = sourceSize.width / targetAspect
        return CGRect(
            x: 0,
            y: (sourceSize.height - croppedHeight) / 2,
            width: sourceSize.width,
            height: croppedHeight
        )
    }

    private static func buildCommand(_ command: UInt8, payload: [UInt8] = []) -> Data {
        if payload.count >= 192 {
            return Data([commandPrefix, command, UInt8((payload.count >> 8) | 0xC0), UInt8(payload.count & 0xFF)] + payload + [fixedCrc])
        }
        return Data([commandPrefix, command, UInt8(payload.count)] + payload + [fixedCrc])
    }

    private static func shortBytes(_ value: Int) -> [UInt8] {
        [UInt8((value >> 8) & 0xFF), UInt8(value & 0xFF)]
    }

    private static func ebv(_ value: Int) -> [UInt8] {
        value >= 192 ? [UInt8((value >> 8) | 0xC0), UInt8(value & 0xFF)] : [UInt8(value)]
    }
}

enum P0BitmapProtocolError: Error, Equatable {
    case unreadableImage
}
