package com.viberack.app.core.printer

object PrinterNameMatcher {
    fun isDeliQ5(name: String): Boolean {
        return name.contains("Q5", ignoreCase = true) ||
            name.contains("DELI", ignoreCase = true) ||
            name.contains("得力", ignoreCase = true)
    }

    fun isDetongerP0(name: String): Boolean {
        return name.contains("P0", ignoreCase = true) ||
            name.contains("DETONGER", ignoreCase = true) ||
            name.contains("DOTHANTECH", ignoreCase = true) ||
            name.contains("印立方", ignoreCase = true) ||
            name.contains("德佟", ignoreCase = true)
    }
}
