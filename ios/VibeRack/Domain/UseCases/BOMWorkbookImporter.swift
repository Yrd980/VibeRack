import Foundation
import ZIPFoundation

public enum BOMWorkbookImporter {
    public static func `import`(at url: URL) throws -> BOMWorkbook {
        switch url.pathExtension.lowercased() {
        case "xlsx":
            return try importXLSX(at: url)
        case "csv":
            return try importCSV(at: url)
        default:
            throw BOMWorkbookImportError.unsupportedFileExtension(url.pathExtension)
        }
    }

    public static func importXLSX(at url: URL) throws -> BOMWorkbook {
        let archive = try Archive(url: url, accessMode: .read)
        let sharedStrings = try readSharedStrings(from: archive)
        let sheet = try readFirstSheet(from: archive)
        let rows = try SheetXMLParser.parse(data: sheet.data, sharedStrings: sharedStrings)
        guard let headerRow = rows.first(where: { !$0.values.allSatisfy { $0.isEmpty } }) else {
            throw BOMWorkbookImportError.emptyWorkbook
        }

        let headers = headerRow.values
        let headerMap = HeaderMap(headers: headers)
        let lines = rows
            .filter { $0.number > headerRow.number }
            .compactMap { row in
                makeLine(from: row, headerMap: headerMap)
            }

        return BOMWorkbook(sheetName: sheet.name, headers: headers, lines: lines)
    }

    public static func importCSV(at url: URL) throws -> BOMWorkbook {
        let content = try String(contentsOf: url, encoding: .utf8)
        let rows = CSVParser.parse(content)
        guard let headerRow = rows.first else {
            throw BOMWorkbookImportError.emptyWorkbook
        }

        let headers = headerRow.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        let headerMap = HeaderMap(headers: headers)
        let lines = rows.dropFirst().enumerated().compactMap { offset, values in
            makeLine(
                from: SheetRow(number: offset + 2, valuesByColumn: Dictionary(uniqueKeysWithValues: values.enumerated().map { ($0.offset, $0.element) })),
                headerMap: headerMap
            )
        }
        return BOMWorkbook(sheetName: url.deletingPathExtension().lastPathComponent, headers: headers, lines: lines)
    }

    private static func readSharedStrings(from archive: Archive) throws -> [String] {
        guard let entry = archive["xl/sharedStrings.xml"] else {
            return []
        }
        let data = try archive.readData(for: entry)
        return try SharedStringsXMLParser.parse(data: data)
    }

    private static func readFirstSheet(from archive: Archive) throws -> XLSXSheet {
        guard let workbookEntry = archive["xl/workbook.xml"] else {
            throw BOMWorkbookImportError.missingWorkbook
        }
        let workbook = try WorkbookXMLParser.parse(data: archive.readData(for: workbookEntry))
        guard let firstSheet = workbook.sheets.first else {
            throw BOMWorkbookImportError.missingWorksheet
        }

        let relationships = try readWorkbookRelationships(from: archive)
        let target = relationships[firstSheet.relationshipID] ?? "worksheets/sheet1.xml"
        let normalizedTarget = target.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let sheetPath = normalizedTarget.hasPrefix("xl/") ? normalizedTarget : "xl/" + normalizedTarget
        guard let sheetEntry = archive[sheetPath] else {
            throw BOMWorkbookImportError.missingWorksheet
        }
        return XLSXSheet(name: firstSheet.name, data: try archive.readData(for: sheetEntry))
    }

    private static func readWorkbookRelationships(from archive: Archive) throws -> [String: String] {
        guard let entry = archive["xl/_rels/workbook.xml.rels"] else {
            return [:]
        }
        return try WorkbookRelationshipsXMLParser.parse(data: archive.readData(for: entry))
    }

    private static func makeLine(from row: SheetRow, headerMap: HeaderMap) -> BOMLine? {
        let supplierPart = row.value(at: headerMap.supplierPart)
        let manufacturerPart = row.value(at: headerMap.manufacturerPart)
        let designator = row.value(at: headerMap.designator)
        let comment = row.value(at: headerMap.comment)
        let footprint = row.value(at: headerMap.footprint)
        let value = row.value(at: headerMap.value)

        let hasContent = [supplierPart, manufacturerPart, designator, comment, footprint, value]
            .contains { !($0 ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        guard hasContent else {
            return nil
        }

        return BOMLine(
            rowNumber: row.number,
            quantity: row.integerValue(at: headerMap.quantity) ?? 1,
            comment: comment,
            footprint: footprint,
            value: value,
            supplierPart: supplierPart,
            manufacturerPart: manufacturerPart,
            manufacturer: row.value(at: headerMap.manufacturer),
            supplier: row.value(at: headerMap.supplier),
            designator: designator
        )
    }
}

public enum BOMWorkbookImportError: Error, Equatable {
    case emptyWorkbook
    case missingWorkbook
    case missingWorksheet
    case unsupportedFileExtension(String)
}

private struct XLSXSheet {
    let name: String
    let data: Data
}

private struct WorkbookSheet {
    let name: String
    let relationshipID: String
}

private struct Workbook {
    let sheets: [WorkbookSheet]
}

private struct HeaderMap {
    let quantity: Int?
    let comment: Int?
    let footprint: Int?
    let value: Int?
    let manufacturerPart: Int?
    let manufacturer: Int?
    let supplierPart: Int?
    let supplier: Int?
    let designator: Int?

    init(headers: [String]) {
        let normalized = Dictionary(uniqueKeysWithValues: headers.enumerated().map { index, header in
            (Self.normalize(header), index)
        })
        quantity = normalized["quantity"]
        comment = normalized["comment"]
        footprint = normalized["footprint"]
        value = normalized["value"]
        manufacturerPart = normalized["manufacturerpart"]
        manufacturer = normalized["manufacturer"]
        supplierPart = normalized["supplierpart"]
        supplier = normalized["supplier"]
        designator = normalized["designator"]
    }

    private static func normalize(_ value: String) -> String {
        value.lowercased().filter { $0.isLetter || $0.isNumber }
    }
}

private struct SheetRow {
    let number: Int
    let valuesByColumn: [Int: String]

    var values: [String] {
        guard let maxColumn = valuesByColumn.keys.max() else {
            return []
        }
        return (0...maxColumn).map { valuesByColumn[$0] ?? "" }
    }

    func value(at index: Int?) -> String? {
        guard let index else {
            return nil
        }
        return valuesByColumn[index]?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    func integerValue(at index: Int?) -> Int? {
        guard let value = value(at: index) else {
            return nil
        }
        return Int(Double(value) ?? 0)
    }
}

private final class SharedStringsXMLParser: NSObject, XMLParserDelegate {
    private var strings: [String] = []
    private var currentText = ""
    private var isInText = false

    static func parse(data: Data) throws -> [String] {
        let delegate = SharedStringsXMLParser()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? BOMWorkbookImportError.emptyWorkbook
        }
        return delegate.strings
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        if elementName == "si" {
            currentText = ""
        } else if elementName == "t" {
            isInText = true
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard isInText else {
            return
        }
        currentText += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if elementName == "t" {
            isInText = false
        } else if elementName == "si" {
            strings.append(currentText)
        }
    }
}

private final class WorkbookXMLParser: NSObject, XMLParserDelegate {
    private var sheets: [WorkbookSheet] = []

    static func parse(data: Data) throws -> Workbook {
        let delegate = WorkbookXMLParser()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? BOMWorkbookImportError.missingWorkbook
        }
        return Workbook(sheets: delegate.sheets)
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        guard elementName == "sheet",
              let name = attributeDict["name"],
              let relationshipID = attributeDict["r:id"]
        else {
            return
        }
        sheets.append(WorkbookSheet(name: name, relationshipID: relationshipID))
    }
}

private final class WorkbookRelationshipsXMLParser: NSObject, XMLParserDelegate {
    private var relationships: [String: String] = [:]

    static func parse(data: Data) throws -> [String: String] {
        let delegate = WorkbookRelationshipsXMLParser()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? BOMWorkbookImportError.missingWorkbook
        }
        return delegate.relationships
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        guard elementName == "Relationship",
              let id = attributeDict["Id"],
              let target = attributeDict["Target"]
        else {
            return
        }
        relationships[id] = target
    }
}

private final class SheetXMLParser: NSObject, XMLParserDelegate {
    private let sharedStrings: [String]
    private var rows: [SheetRow] = []
    private var currentRowNumber: Int?
    private var currentValues: [Int: String] = [:]
    private var currentColumn: Int?
    private var currentCellType: String?
    private var currentText = ""
    private var isInValue = false

    init(sharedStrings: [String]) {
        self.sharedStrings = sharedStrings
    }

    static func parse(data: Data, sharedStrings: [String]) throws -> [SheetRow] {
        let delegate = SheetXMLParser(sharedStrings: sharedStrings)
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? BOMWorkbookImportError.missingWorksheet
        }
        return delegate.rows
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        switch elementName {
        case "row":
            currentRowNumber = attributeDict["r"].flatMap(Int.init)
            currentValues = [:]
        case "c":
            currentColumn = attributeDict["r"].flatMap(Self.columnIndex(from:))
            currentCellType = attributeDict["t"]
            currentText = ""
        case "v", "t":
            isInValue = true
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard isInValue else {
            return
        }
        currentText += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        switch elementName {
        case "v", "t":
            isInValue = false
        case "c":
            if let column = currentColumn {
                currentValues[column] = resolvedCurrentText()
            }
            currentColumn = nil
            currentCellType = nil
            currentText = ""
        case "row":
            if let rowNumber = currentRowNumber {
                rows.append(SheetRow(number: rowNumber, valuesByColumn: currentValues))
            }
            currentRowNumber = nil
            currentValues = [:]
        default:
            break
        }
    }

    private func resolvedCurrentText() -> String {
        if currentCellType == "s", let index = Int(currentText), sharedStrings.indices.contains(index) {
            return sharedStrings[index]
        }
        return currentText
    }

    private static func columnIndex(from reference: String) -> Int? {
        let letters = reference.prefix { $0.isLetter }
        guard !letters.isEmpty else {
            return nil
        }
        return letters.reduce(0) { total, character in
            total * 26 + Int(character.uppercaseScalarValue - Character("A").uppercaseScalarValue + 1)
        } - 1
    }
}

private extension Archive {
    func readData(for entry: Entry) throws -> Data {
        var data = Data()
        _ = try extract(entry) { chunk in
            data.append(chunk)
        }
        return data
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

private extension Character {
    var uppercaseScalarValue: UInt32 {
        String(self).unicodeScalars.first?.value ?? 0
    }
}

private enum CSVParser {
    static func parse(_ content: String) -> [[String]] {
        var rows: [[String]] = []
        var row: [String] = []
        var field = ""
        var isQuoted = false
        var index = content.startIndex

        while index < content.endIndex {
            let character = content[index]
            content.formIndex(after: &index)

            if character == "\"" {
                if isQuoted, index < content.endIndex, content[index] == "\"" {
                    field.append("\"")
                    content.formIndex(after: &index)
                } else {
                    isQuoted.toggle()
                }
            } else if character == "," && !isQuoted {
                row.append(field)
                field = ""
            } else if character == "\n" && !isQuoted {
                row.append(field)
                rows.append(row)
                row = []
                field = ""
            } else if character == "\r" && !isQuoted {
                continue
            } else {
                field.append(character)
            }
        }

        if !field.isEmpty || !row.isEmpty {
            row.append(field)
            rows.append(row)
        }
        return rows
    }
}
