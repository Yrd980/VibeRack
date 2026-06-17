import UIKit

enum BoxLayerLabelRenderError: Error, Equatable {
    case emptyPositionCode
    case emptyPartNumber
}

enum BoxLayerLabelRenderer {
    static let printSizePoints = CGSize(width: 232, height: 384)
    static let p0PrintSizePoints = CGSize(width: 384, height: 232)

    static func render(label: BoxLayerLabel) throws -> UIImage {
        guard !label.positionCode.isEmpty else {
            throw BoxLayerLabelRenderError.emptyPositionCode
        }
        guard !label.partNumber.isEmpty else {
            throw BoxLayerLabelRenderError.emptyPartNumber
        }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true

        let renderer = UIGraphicsImageRenderer(size: printSizePoints, format: format)
        return renderer.image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: printSizePoints))

            UIColor.black.setStroke()
            let border = CGRect(origin: CGPoint(x: 8, y: 8), size: CGSize(width: printSizePoints.width - 16, height: printSizePoints.height - 16))
            UIBezierPath(roundedRect: border, cornerRadius: 10).stroke()

            drawCentered(
                label.positionCode,
                in: CGRect(x: 18, y: 38, width: printSizePoints.width - 36, height: 96),
                font: .systemFont(ofSize: 44, weight: .bold)
            )
            drawCentered(
                label.partNumber,
                in: CGRect(x: 18, y: 168, width: printSizePoints.width - 36, height: 116),
                font: .monospacedSystemFont(ofSize: 34, weight: .semibold)
            )
            drawCentered(
                "VibeRack",
                in: CGRect(x: 18, y: 316, width: printSizePoints.width - 36, height: 30),
                font: .systemFont(ofSize: 18, weight: .medium),
                color: .darkGray
            )
        }
    }

    static func renderP0PrintImage(label: BoxLayerLabel) throws -> UIImage {
        guard !label.positionCode.isEmpty else {
            throw BoxLayerLabelRenderError.emptyPositionCode
        }
        guard !label.partNumber.isEmpty else {
            throw BoxLayerLabelRenderError.emptyPartNumber
        }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true

        let renderer = UIGraphicsImageRenderer(size: p0PrintSizePoints, format: format)
        return renderer.image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: p0PrintSizePoints))

            let cgContext = context.cgContext
            cgContext.saveGState()
            cgContext.translateBy(x: 8, y: p0PrintSizePoints.height - 10)
            cgContext.rotate(by: -.pi / 2)

            drawFittedLine(
                label.positionCode,
                baseline: 68,
                maxWidth: p0PrintSizePoints.height - 12,
                font: .systemFont(ofSize: 58, weight: .black),
                minFontSize: 34
            )
            drawFittedLine(
                label.partNumber,
                baseline: 130,
                maxWidth: p0PrintSizePoints.height - 12,
                font: .monospacedSystemFont(ofSize: 54, weight: .black),
                minFontSize: 32
            )

            cgContext.restoreGState()
        }
    }

    static func renderPrinterPreview(label: BoxLayerLabel) throws -> UIImage {
        guard !label.positionCode.isEmpty else {
            throw BoxLayerLabelRenderError.emptyPositionCode
        }
        guard !label.partNumber.isEmpty else {
            throw BoxLayerLabelRenderError.emptyPartNumber
        }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true

        return UIGraphicsImageRenderer(size: p0PrintSizePoints, format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: p0PrintSizePoints))

            drawCentered(
                label.positionCode,
                in: CGRect(x: 4, y: 0, width: p0PrintSizePoints.width - 8, height: 112),
                font: .systemFont(ofSize: 96, weight: .black)
            )
            drawCentered(
                label.partNumber,
                in: CGRect(x: 4, y: 104, width: p0PrintSizePoints.width - 8, height: 124),
                font: .monospacedSystemFont(ofSize: 94, weight: .black)
            )
        }
    }

    private static func drawCentered(
        _ text: String,
        in rect: CGRect,
        font: UIFont,
        color: UIColor = .black
    ) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineBreakMode = .byTruncatingMiddle
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: color,
            .paragraphStyle: paragraph
        ]
        let attributed = NSAttributedString(string: text, attributes: attributes)
        let measured = attributed.boundingRect(
            with: CGSize(width: rect.width, height: rect.height),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            context: nil
        )
        let drawRect = CGRect(
            x: rect.minX,
            y: rect.midY - ceil(measured.height) / 2,
            width: rect.width,
            height: min(rect.height, ceil(measured.height) + 2)
        )
        attributed.draw(with: drawRect, options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
    }

    private static func drawFittedLine(
        _ text: String,
        baseline: CGFloat,
        maxWidth: CGFloat,
        font: UIFont,
        minFontSize: CGFloat
    ) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var fontSize = font.pointSize
        var fittedFont = font
        while fontSize > minFontSize && measuredWidth(trimmed, font: fittedFont) > maxWidth {
            fontSize -= 1
            fittedFont = font.withSize(fontSize)
        }
        let fitted = ellipsizeEnd(trimmed, font: fittedFont, maxWidth: maxWidth)
        fitted.draw(
            at: CGPoint(x: 0, y: baseline - fittedFont.ascender),
            withAttributes: [
                .font: fittedFont,
                .foregroundColor: UIColor.black
            ]
        )
    }

    private static func ellipsizeEnd(_ text: String, font: UIFont, maxWidth: CGFloat) -> String {
        guard measuredWidth(text, font: font) > maxWidth else {
            return text
        }
        let ellipsis = "..."
        var endIndex = text.endIndex
        while endIndex > text.startIndex {
            let candidate = String(text[..<endIndex]) + ellipsis
            if measuredWidth(candidate, font: font) <= maxWidth {
                return candidate
            }
            endIndex = text.index(before: endIndex)
        }
        return ellipsis
    }

    private static func measuredWidth(_ text: String, font: UIFont) -> CGFloat {
        (text as NSString).size(withAttributes: [.font: font]).width
    }
}
