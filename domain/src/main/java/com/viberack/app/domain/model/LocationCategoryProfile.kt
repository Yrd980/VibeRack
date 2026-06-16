package com.viberack.app.domain.model

data class LocationCategoryProfile(
    val locationId: Long,
    val category: String?,
    val packageName: String?,
    val quantity: Int
)

data class DominantLocationCategoryProfile(
    val category: String?,
    val packageName: String?
)

fun calculateDominantLocationCategoryProfile(
    profiles: List<LocationCategoryProfile>
): DominantLocationCategoryProfile {
    val dominantCategory = profiles.dominantProfileValue(LocationCategoryProfile::category)
    val dominantPackageName = profiles.dominantProfileValue(LocationCategoryProfile::packageName)
    return DominantLocationCategoryProfile(
        category = dominantCategory,
        packageName = dominantPackageName
    )
}

private fun List<LocationCategoryProfile>.dominantProfileValue(
    selector: (LocationCategoryProfile) -> String?
): String? {
    return asSequence()
        .mapNotNull { profile ->
            val value = selector(profile)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.uppercase()
                ?: return@mapNotNull null
            value
        }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key }
        )
        .firstOrNull()
        ?.key
}
