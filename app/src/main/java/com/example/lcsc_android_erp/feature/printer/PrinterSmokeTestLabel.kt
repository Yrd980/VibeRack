package com.example.lcsc_android_erp.feature.printer

import android.graphics.Bitmap

object PrinterSmokeTestLabel {
    fun createBitmap(): Bitmap {
        return BoxLayerLabelBitmap.create10MmBitmap(
            positionCode = "BOX01-L01",
            partNumber = "C17710",
            note = "TEST PRINT",
        )
    }
}
