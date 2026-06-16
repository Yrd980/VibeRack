package com.viberack.app.feature.search

import com.viberack.app.domain.model.ComponentBoxLayer
import com.viberack.app.domain.model.ContainerType
import com.viberack.app.domain.model.SearchInventoryRecord
import java.util.Locale

internal object BomWorkflow {
    fun buildRows(
        inventoryRecords: List<SearchInventoryRecord>,
        document: ParsedBomDocument?,
        persistentBindings: Map<String, String>,
        temporaryBindings: Map<String, String>,
        ignoredEntryKeys: Set<String>,
        boxLayers: List<ComponentBoxLayer>
    ): List<BomSearchRowUiModel> {
        if (document == null) {
            return emptyList()
        }

        return document.entries.mapNotNull { entry ->
            val entryKey = entryKey(entry)
            if (entryKey in ignoredEntryKeys) {
                return@mapNotNull null
            }

            val persistentBindingPartNumber = entry.supplierPart
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.let(persistentBindings::get)
            val temporaryBindingPartNumber = temporaryBindings[entryKey]
            val boundPartNumber = persistentBindingPartNumber ?: temporaryBindingPartNumber
            val resolvedPartNumber = boundPartNumber
                ?: entry.supplierPart
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    ?.takeIf { it.isNotBlank() }
            val matchedRecords = inventoryRecords.filter { record ->
                matchesBomEntry(
                    record = record,
                    entry = entry,
                    boundPartNumber = boundPartNumber
                )
            }
            val assignedLayers = resolvedPartNumber?.let { partNumber ->
                boxLayers.filter { layer ->
                    layer.partNumber?.trim()?.uppercase(Locale.ROOT) == partNumber
                }
            }.orEmpty()

            BomSearchRowUiModel(
                entry = entry,
                matchedResults = SearchInventoryWorkflow.groupRecords(matchedRecords),
                assignedLayers = assignedLayers,
                isBound = boundPartNumber != null,
                isPersistentBinding = persistentBindingPartNumber != null
            )
        }
    }

    fun buildPickSession(rows: List<BomSearchRowUiModel>): BomPickSessionUiModel? {
        val targets = rows.flatMap { row ->
            row.matchedResults.flatMap { result ->
                result.records.mapNotNull { record ->
                    val macAddress = record.containerMacAddress?.trim()?.uppercase(Locale.ROOT)
                    val slotNumber = record.slotNumber ?: 0
                    if (record.containerType != ContainerType.SMART_CHASSIS ||
                        macAddress.isNullOrBlank() ||
                        slotNumber !in 1..25
                    ) {
                        null
                    } else {
                        PickTargetCandidate(
                            containerCode = record.locationCode,
                            macAddress = macAddress,
                            slotNumber = slotNumber,
                            partNumber = record.partNumber,
                            designator = row.entry.designator
                        )
                    }
                }
            }
        }
            .distinctBy { "${it.macAddress}:${it.slotNumber}:${it.partNumber}" }

        if (targets.isEmpty()) {
            return null
        }

        val groups = targets
            .groupBy { it.macAddress }
            .map { (macAddress, groupTargets) ->
                BomPickGroupUiModel(
                    containerCode = groupTargets.first().containerCode,
                    macAddress = macAddress,
                    slots = groupTargets.map { it.slotNumber }.distinct().sorted(),
                    targets = groupTargets
                        .sortedWith(compareBy<PickTargetCandidate> { it.slotNumber }.thenBy { it.partNumber })
                        .map { target ->
                            BomPickTargetUiModel(
                                partNumber = target.partNumber,
                                slotNumber = target.slotNumber,
                                designator = target.designator
                            )
                        }
                )
            }
            .sortedBy { it.containerCode }

        return BomPickSessionUiModel(groups)
    }

    fun entryKey(entry: BomSearchEntry): String {
        return listOf(
            entry.rowNumber,
            entry.supplierPart.orEmpty(),
            entry.manufacturerPart.orEmpty(),
            entry.designator.orEmpty(),
            entry.value.orEmpty()
        ).joinToString("|") { it.trim().uppercase(Locale.ROOT) }
    }

    fun rawPayload(entry: BomSearchEntry): String {
        return listOf(
            "row=${entry.rowNumber}",
            "quantity=${entry.quantity ?: 0}",
            "supplierPart=${entry.supplierPart.orEmpty()}",
            "manufacturerPart=${entry.manufacturerPart.orEmpty()}",
            "manufacturer=${entry.manufacturer.orEmpty()}",
            "value=${entry.value.orEmpty()}",
            "footprint=${entry.footprint.orEmpty()}",
            "designator=${entry.designator.orEmpty()}"
        ).joinToString(";")
    }

    private fun matchesBomEntry(
        record: SearchInventoryRecord,
        entry: BomSearchEntry,
        boundPartNumber: String?
    ): Boolean {
        if (!boundPartNumber.isNullOrBlank()) {
            return record.partNumber.trim().uppercase(Locale.ROOT) == boundPartNumber
        }
        val supplierPart = entry.supplierPart?.trim()?.uppercase(Locale.ROOT)
        if (!supplierPart.isNullOrEmpty()) {
            return record.partNumber.trim().uppercase(Locale.ROOT) == supplierPart
        }

        val manufacturerPart = entry.manufacturerPart?.trim()?.uppercase(Locale.ROOT)
        if (!manufacturerPart.isNullOrEmpty()) {
            val mpn = record.mpn?.trim()?.uppercase(Locale.ROOT)
            return mpn == manufacturerPart
        }

        return matchesPassiveFootprintEntry(record, entry)
    }

    private fun matchesPassiveFootprintEntry(
        record: SearchInventoryRecord,
        entry: BomSearchEntry
    ): Boolean {
        val footprint = entry.footprint?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val passiveType = parsePassiveFootprintType(footprint) ?: return false
        val normalizedPackage = parsePassiveFootprintPackage(footprint) ?: return false
        val recordPackage = normalizePackageName(record.packageName) ?: return false
        if (recordPackage != normalizedPackage) {
            return false
        }

        val category = record.category?.trim().orEmpty()
        val matchesCategory = when (passiveType) {
            PassiveFootprintType.Resistor -> category.contains("电阻", ignoreCase = true)
            PassiveFootprintType.Capacitor -> category.contains("电容", ignoreCase = true)
        }
        if (!matchesCategory) {
            return false
        }

        val bomValue = normalizePassiveValue(
            entry.value?.takeIf { it.isNotBlank() } ?: entry.comment.orEmpty()
        )
        if (bomValue.isEmpty()) {
            return false
        }

        val specificationKeys = when (passiveType) {
            PassiveFootprintType.Resistor -> listOf("阻值")
            PassiveFootprintType.Capacitor -> listOf("容值")
        }

        val recordValue = specificationKeys.asSequence()
            .mapNotNull { key -> record.specifications[key] }
            .map(::normalizePassiveValue)
            .firstOrNull { it.isNotEmpty() }
            ?: return false

        return recordValue == bomValue
    }

    private fun parsePassiveFootprintType(footprint: String): PassiveFootprintType? {
        return when {
            footprint.matches(Regex("^R\\d{4}$")) -> PassiveFootprintType.Resistor
            footprint.matches(Regex("^C\\d{4}$")) -> PassiveFootprintType.Capacitor
            else -> null
        }
    }

    private fun parsePassiveFootprintPackage(footprint: String): String? {
        return footprint.drop(1).takeIf { it.length == 4 && it.all(Char::isDigit) }
    }

    private fun normalizePackageName(packageName: String?): String? {
        val normalized = packageName?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val match = Regex("(\\d{4})").find(normalized)
        return match?.groupValues?.getOrNull(1)
    }

    private fun normalizePassiveValue(value: String): String {
        return value
            .trim()
            .uppercase(Locale.ROOT)
            .replace(" ", "")
            .replace("Ω", "")
            .replace("OHM", "")
            .replace("欧姆", "")
            .replace("µ", "U")
            .replace("μ", "U")
    }

    private enum class PassiveFootprintType {
        Resistor,
        Capacitor
    }

    private data class PickTargetCandidate(
        val containerCode: String,
        val macAddress: String,
        val slotNumber: Int,
        val partNumber: String,
        val designator: String?
    )
}
