import Foundation

public struct InboundQrPayload: Equatable {
    public let orderNo: String?
    public let partNumber: String
    public let manufacturerPartNo: String?
    public let quantity: Int
    public let rawText: String
    public let extraFields: [String: String]

    public init(
        orderNo: String?,
        partNumber: String,
        manufacturerPartNo: String?,
        quantity: Int,
        rawText: String,
        extraFields: [String: String]
    ) {
        self.orderNo = orderNo
        self.partNumber = partNumber
        self.manufacturerPartNo = manufacturerPartNo
        self.quantity = quantity
        self.rawText = rawText
        self.extraFields = extraFields
    }
}

public enum LcscQrParserError: LocalizedError, Equatable {
    case notLcscQr
    case missingPartNumber

    public var errorDescription: String? {
        switch self {
        case .notLcscQr:
            "不是可识别的 LCSC QR payload"
        case .missingPartNumber:
            "LCSC QR payload 缺少 pc 料号字段"
        }
    }
}

public enum LcscQrParser {
    public static func parse(_ rawText: String) throws -> InboundQrPayload {
        let normalized = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalized.hasPrefix("{"), normalized.hasSuffix("}") else {
            throw LcscQrParserError.notLcscQr
        }

        let content = String(normalized.dropFirst().dropLast())
        let fields = parseFields(content)
        guard let partNumber = fields.firstCaseInsensitiveValue("pc")?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased(),
            partNumber.hasPrefix("C")
        else {
            throw LcscQrParserError.missingPartNumber
        }

        let quantityText = fields.firstCaseInsensitiveValue("qty")?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let quantity = quantityText.flatMap { Int($0) } ?? 0

        return InboundQrPayload(
            orderNo: normalizedValue(fields.firstCaseInsensitiveValue("on")),
            partNumber: partNumber,
            manufacturerPartNo: normalizedValue(fields.firstCaseInsensitiveValue("pm")),
            quantity: quantity,
            rawText: rawText,
            extraFields: fields
        )
    }

    private static func parseFields(_ content: String) -> [String: String] {
        var fields: [String: String] = [:]
        for token in content.split(separator: ",", omittingEmptySubsequences: false) {
            let pieces = token.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
            guard pieces.count == 2 else { continue }
            let key = stripQuotes(String(pieces[0]).trimmingCharacters(in: .whitespacesAndNewlines))
            let value = stripQuotes(String(pieces[1]).trimmingCharacters(in: .whitespacesAndNewlines))
            guard key.isEmpty == false else { continue }
            fields[key] = value
        }
        return fields
    }

    private static func normalizedValue(_ value: String?) -> String? {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed?.isEmpty == true ? nil : trimmed
    }

    private static func stripQuotes(_ value: String) -> String {
        value.trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
    }
}

private extension Dictionary where Key == String, Value == String {
    func firstCaseInsensitiveValue(_ targetKey: String) -> String? {
        first(where: { $0.key.caseInsensitiveCompare(targetKey) == .orderedSame })?.value
    }
}
