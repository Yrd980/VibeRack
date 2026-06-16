package com.viberack.app.feature.search

import android.content.Context
import com.viberack.app.R
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object BomSpreadsheetParser {
    fun parse(
        context: Context,
        fileName: String,
        inputStream: InputStream
    ): ParsedBomDocument {
        inputStream.use { stream ->
            val workbook = WorkbookFactory.create(stream)
            workbook.use { wb ->
                val sheet = wb.getSheetAt(0) ?: error(context.getString(R.string.search_bom_no_sheet))
                val headerRow = sheet.getRow(sheet.firstRowNum)
                    ?: error(context.getString(R.string.search_bom_missing_header))
                val headerIndex = buildHeaderIndex(headerRow)

                val entries = buildList {
                    for (rowIndex in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val entry = row.toBomEntry(headerIndex) ?: continue
                        add(entry)
                    }
                }

                if (entries.isEmpty()) {
                    error(context.getString(R.string.search_bom_no_rows))
                }

                return ParsedBomDocument(
                    fileName = fileName,
                    entries = entries
                )
            }
        }
    }

    private fun buildHeaderIndex(headerRow: Row): Map<String, Int> {
        return buildMap {
            for (cell in headerRow) {
                val name = cell.stringCellValue?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    put(name, cell.columnIndex)
                }
            }
        }
    }

    private fun Row.toBomEntry(headerIndex: Map<String, Int>): BomSearchEntry? {
        val rowNumber = getString(headerIndex["No."])
        val quantity = getInt(headerIndex["Quantity"])
        val comment = getString(headerIndex["Comment"])
        val designator = getString(headerIndex["Designator"])
        val footprint = getString(headerIndex["Footprint"])
        val value = getString(headerIndex["Value"])
        val manufacturerPart = getString(headerIndex["Manufacturer Part"])
        val manufacturer = getString(headerIndex["Manufacturer"])
        val supplierPart = getString(headerIndex["Supplier Part"])
        val supplier = getString(headerIndex["Supplier"])

        if (
            rowNumber.isNullOrBlank() &&
            quantity == null &&
            comment.isNullOrBlank() &&
            designator.isNullOrBlank() &&
            footprint.isNullOrBlank() &&
            value.isNullOrBlank() &&
            manufacturerPart.isNullOrBlank() &&
            manufacturer.isNullOrBlank() &&
            supplierPart.isNullOrBlank() &&
            supplier.isNullOrBlank()
        ) {
            return null
        }

        return BomSearchEntry(
            rowNumber = rowNumber ?: (rowNum + 1).toString(),
            quantity = quantity,
            comment = comment,
            designator = designator,
            footprint = footprint,
            value = value,
            manufacturerPart = manufacturerPart,
            manufacturer = manufacturer,
            supplierPart = supplierPart,
            supplier = supplier
        )
    }

    private fun Row.getString(columnIndex: Int?): String? {
        if (columnIndex == null) return null
        val cell = getCell(columnIndex) ?: return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue?.trim()
            CellType.NUMERIC -> {
                val number = cell.numericCellValue
                if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.toString().trim()
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun Row.getInt(columnIndex: Int?): Int? {
        val raw = getString(columnIndex) ?: return null
        return raw.toDoubleOrNull()?.toInt()
    }
}
