package com.viberack.app.feature.inbound

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun QrScannerPreview(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    torchEnabled: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(cameraExecutor) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
        update = { view ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        bindCameraUseCases(
                            previewView = view,
                            cameraProvider = cameraProvider,
                            lifecycleOwner = lifecycleOwner,
                            cameraExecutor = cameraExecutor,
                            enabled = enabled,
                            torchEnabled = torchEnabled,
                            onTorchAvailabilityChanged = onTorchAvailabilityChanged,
                            onQrCodeDetected = onQrCodeDetected
                        )
                    }.onFailure { throwable ->
                        Log.e("QrScannerPreview", "Failed to initialize scanner camera", throwable)
                        onTorchAvailabilityChanged(false)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun bindCameraUseCases(
    previewView: PreviewView,
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    enabled: Boolean,
    torchEnabled: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onQrCodeDetected: (String) -> Unit
) {
    runCatching {
        bindCameraUseCasesOrThrow(
            previewView = previewView,
            cameraProvider = cameraProvider,
            lifecycleOwner = lifecycleOwner,
            cameraExecutor = cameraExecutor,
            enabled = enabled,
            torchEnabled = torchEnabled,
            onTorchAvailabilityChanged = onTorchAvailabilityChanged,
            onQrCodeDetected = onQrCodeDetected
        )
    }.onFailure { throwable ->
        Log.e("QrScannerPreview", "Failed to bind scanner camera use cases", throwable)
        onTorchAvailabilityChanged(false)
        cameraProvider.unbindAll()
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun bindCameraUseCasesOrThrow(
    previewView: PreviewView,
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    enabled: Boolean,
    torchEnabled: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onQrCodeDetected: (String) -> Unit
) {
    if (!enabled) {
        cameraProvider.unbindAll()
        onTorchAvailabilityChanged(false)
        return
    }

    val preview = Preview.Builder().build().apply {
        surfaceProvider = previewView.surfaceProvider
    }

    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    val scanner = BarcodeScanning.getClient(options)

    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return@setAnalyzer
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val value = barcodes
                    .firstOrNull { !it.rawValue.isNullOrBlank() }
                    ?.rawValue
                if (!value.isNullOrBlank()) {
                    onQrCodeDetected(value)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    cameraProvider.unbindAll()
    val camera: Camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        imageAnalysis
    )
    val hasFlashUnit = camera.cameraInfo.hasFlashUnit()
    onTorchAvailabilityChanged(hasFlashUnit)
    camera.cameraControl.enableTorch(hasFlashUnit && torchEnabled)
}
