package com.viberack.app.core.printer

import android.graphics.Typeface

object PrintTypeface {
    private const val FONT_FAMILY = "sans-serif"

    val regular: Typeface by lazy(LazyThreadSafetyMode.NONE) {
        Typeface.create(FONT_FAMILY, Typeface.NORMAL)
    }

    val bold: Typeface by lazy(LazyThreadSafetyMode.NONE) {
        Typeface.create(FONT_FAMILY, Typeface.BOLD)
    }
}
