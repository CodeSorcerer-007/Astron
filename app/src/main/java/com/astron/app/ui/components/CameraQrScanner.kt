package com.astron.app.ui.components

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.astron.app.ui.theme.CosmicCyan
import com.astron.app.ui.theme.ElectricBlue
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun CameraQrScanner(
    onQrScanned: (rawContent: String) -> Unit,
    modifier: Modifier = Modifier,
    scanOverlayColor: Color = ElectricBlue
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderInstance by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8"
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderInstance?.unbindAll()
            } catch (_: Throwable) {}
            try {
                cameraExecutor.shutdown()
            } catch (_: Throwable) {}
        }
    }

    // Laser scan animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // CameraX Live Preview View
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                try {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            cameraProviderInstance = cameraProvider

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                try {
                                    val buffer = imageProxy.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)

                                    val width = imageProxy.width
                                    val height = imageProxy.height
                                    val rotation = imageProxy.imageInfo.rotationDegrees

                                    // Rotate byte array if needed for portrait orientation
                                    val (rotatedBytes, rotWidth, rotHeight) = if (rotation == 90) {
                                        Triple(rotateYuv90(bytes, width, height), height, width)
                                    } else {
                                        Triple(bytes, width, height)
                                    }

                                    val source = PlanarYUVLuminanceSource(
                                        rotatedBytes,
                                        rotWidth,
                                        rotHeight,
                                        0,
                                        0,
                                        rotWidth,
                                        rotHeight,
                                        false
                                    )
                                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                                    try {
                                        val result = reader.decodeWithState(binaryBitmap)
                                        if (result != null && result.text.isNotBlank()) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onQrScanned(result.text)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // QR code not found in current frame
                                    } finally {
                                        reader.reset()
                                    }
                                } catch (e: Throwable) {
                                    Log.e("CameraQrScanner", "Frame analysis error", e)
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (t: Throwable) {
                            Log.e("CameraQrScanner", "Camera initialization failed", t)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                } catch (t: Throwable) {
                    Log.e("CameraQrScanner", "ProcessCameraProvider error", t)
                }

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder reticle
        Box(
            modifier = Modifier
                .size(220.dp)
                .border(2.dp, scanOverlayColor.copy(alpha = 0.8f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Animated Scanning Laser Line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(2.dp)
                    .offset(y = laserOffset.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                scanOverlayColor,
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Torch toggle button in top-right corner
        IconButton(
            onClick = {
                try {
                    val nextState = !torchEnabled
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        torchEnabled = nextState
                        camera?.cameraControl?.enableTorch(nextState)
                    }
                } catch (t: Throwable) {
                    Log.w("CameraQrScanner", "Torch toggle failed", t)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = if (torchEnabled) "Turn off flashlight" else "Turn on flashlight",
                tint = if (torchEnabled) CosmicCyan else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Rotates Y-channel luminance 90 degrees clockwise for portrait camera preview.
 */
private fun rotateYuv90(data: ByteArray, width: Int, height: Int): ByteArray {
    val rotated = ByteArray(width * height)
    var i = 0
    for (x in 0 until width) {
        for (y in height - 1 downTo 0) {
            rotated[i] = data[y * width + x]
            i++
        }
    }
    return rotated
}
