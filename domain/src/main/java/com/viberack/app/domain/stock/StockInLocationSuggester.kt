package com.viberack.app.domain.stock

import com.viberack.app.domain.model.ComponentDetail
import com.viberack.app.domain.model.ExistingStockLocation
import com.viberack.app.domain.model.LegacyLocationCode
import com.viberack.app.domain.model.LocationCategoryProfile
import com.viberack.app.domain.model.StorageLocation

class StockInLocationSuggester {
    fun suggestLocationCode(
        component: ComponentDetail,
        existingStockLocations: List<ExistingStockLocation>,
        availableLocations: List<StorageLocation>,
        locationCategoryProfiles: List<LocationCategoryProfile>,
        fallbackCode: String
    ): String {
        existingStockLocations.firstOrNull { existing ->
            availableLocations.any { location ->
                location.code.equals(existing.locationCode, ignoreCase = true)
            }
        }?.let { existing ->
            return existing.locationCode
        }

        val sortedLocations = availableLocations.sortedBy { LegacyLocationCode(it.code) }
        val locationCategoryLookup = buildLocationCategoryLookup(locationCategoryProfiles)
        val componentCategory = component.category.normalizedProfileValue()
        if (componentCategory != null) {
            val categoryMatchedLocations = sortedLocations.filter { location ->
                locationCategoryLookup[location.id]?.category == componentCategory
            }
            when (categoryMatchedLocations.size) {
                0 -> Unit
                1 -> return categoryMatchedLocations.first().code
                else -> {
                    val componentPackageName = component.packageName.normalizedProfileValue()
                    val packageMatchedLocation = componentPackageName?.let { packageName ->
                        categoryMatchedLocations.firstOrNull { location ->
                            locationCategoryLookup[location.id]?.packageName == packageName
                        }
                    }
                    return packageMatchedLocation?.code ?: categoryMatchedLocations.first().code
                }
            }
        }

        val normalizedCategory = component.category?.trim().orEmpty()
        if (normalizedCategory.isEmpty()) {
            return fallbackCode
        }

        val mapping = categoryLocationMappings.firstOrNull { rule ->
            rule.keywords.any { keyword -> normalizedCategory.contains(keyword, ignoreCase = true) }
        } ?: return fallbackCode

        sortedLocations.firstOrNull { location ->
            location.code.trim().uppercase().startsWith(mapping.prefix)
        }?.let { location ->
            return location.code
        }

        return mapping.prefix + "1"
    }

    private fun buildLocationCategoryLookup(
        profiles: List<LocationCategoryProfile>
    ): Map<Long, LocationCategoryMatch> {
        return profiles.mapNotNull { profile ->
            val category = profile.category.normalizedProfileValue()
            val packageName = profile.packageName.normalizedProfileValue()
            if (category == null && packageName == null) {
                null
            } else {
                profile.locationId to LocationCategoryMatch(
                    category = category,
                    packageName = packageName
                )
            }
        }.toMap()
    }

    private data class LocationCategoryMatch(
        val category: String?,
        val packageName: String?
    )

    private data class CategoryLocationMapping(
        val keywords: List<String>,
        val prefix: String
    )

    private companion object {
        val categoryLocationMappings = listOf(
            CategoryLocationMapping(listOf("电阻"), "R"),
            CategoryLocationMapping(listOf("电容"), "C"),
            CategoryLocationMapping(listOf("二极管", "LED", "TVS"), "D"),
            CategoryLocationMapping(listOf("电感"), "L"),
            CategoryLocationMapping(listOf("三极管", "晶体管", "MOS"), "Q"),
            CategoryLocationMapping(listOf("晶振", "振荡器"), "Y"),
            CategoryLocationMapping(listOf("保险丝"), "F"),
            CategoryLocationMapping(listOf("连接器", "接插件"), "J"),
            CategoryLocationMapping(listOf("继电器"), "K"),
            CategoryLocationMapping(listOf("开关", "按键"), "S"),
            CategoryLocationMapping(listOf("传感器"), "T"),
            CategoryLocationMapping(listOf("集成电路", "接口芯片", "逻辑芯片", "放大器", "驱动器", "存储器", "处理器", "单片机"), "U")
        )
    }
}

private fun String?.normalizedProfileValue(): String? {
    return this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
}
