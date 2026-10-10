package com.example.datasheetanalyzer.camera

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.datasheetanalyzer.ocr.ComponentTextAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onLines: (List<String>) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnLines by rememberUpdatedState(onLines)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var displayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }

    // A 180-degree turn may not recreate the Activity. Rebind the shared viewport.
    DisposableEffect(previewView) {
        var active = true
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (active && previewView.display?.displayId == displayId) {
                    displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                }
            }
        }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        previewView.doOnLayout {
            if (active) displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        }
        onDispose {
            active = false
            manager?.unregisterDisplayListener(listener)
        }
    }

    DisposableEffect(previewView, owner, displayRotation) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var analyzer: ComponentTextAnalyzer? = null
        var camera: Camera? = null
        val cameraObserver = Observer<CameraState> { cameraState ->
            if (!disposed && cameraState.error != null) {
                currentOnError("Kamera kullanılamıyor. Başka bir kamera uygulamasını kapatıp yeniden dene.")
            }
        }

        previewView.doOnLayout {
            if (!disposed) {
                try {
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        if (!disposed) {
                            try {
                                val readyProvider = future.get()
                                provider = readyProvider
                                require(readyProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                                val resolution = ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(AspectRatioStrategy(
                                        AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO
                                    ))
                                    .setResolutionStrategy(ResolutionStrategy(
                                        Size(1280, 960),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )).build()
                                val readyPreview = Preview.Builder()
                                    .setResolutionSelector(resolution)
                                    .setTargetRotation(rotation).build()
                                preview = readyPreview
                                readyPreview.setSurfaceProvider(previewView.surfaceProvider)

                                val readyAnalyzer = ComponentTextAnalyzer(
                                    callbackExecutor = mainExecutor,
                                    onLines = { if (!disposed) currentOnLines(it) },
                                    onError = { if (!disposed) currentOnError(it) }
                                )
                                analyzer = readyAnalyzer
                                val readyAnalysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(resolution)
                                    .setTargetRotation(rotation)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .build()
                                analysis = readyAnalysis
                                readyAnalysis.setAnalyzer(analysisExecutor, readyAnalyzer)

                                // Shared viewport makes analysis.cropRect match visible preview.
                                val viewport = requireNotNull(previewView.viewPort)
                                val group = UseCaseGroup.Builder().setViewPort(viewport)
                                    .addUseCase(readyPreview).addUseCase(readyAnalysis).build()
                                val readyCamera = readyProvider.bindToLifecycle(
                                    owner, CameraSelector.DEFAULT_BACK_CAMERA, group
                                )
                                camera = readyCamera
                                readyCamera.cameraInfo.cameraState.observe(owner, cameraObserver)
                                previewView.setOnTouchListener { view, event ->
                                    if (event.action == MotionEvent.ACTION_UP) {
                                        view.performClick()
                                        val point = previewView.meteringPointFactory
                                            .createPoint(event.x, event.y)
                                        val focus = FocusMeteringAction.Builder(point,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        // Unsupported focus is non-fatal; automatic focus remains.
                                        runCatching { readyCamera.cameraControl.startFocusAndMetering(focus) }
                                    }
                                    true
                                }
                            } catch (_: Exception) {
                                if (!disposed) currentOnError(
                                    "Arka kamera başlatılamadı. İzni kontrol et veya kodu elle gir."
                                )
                            }
                        }
                    }, mainExecutor)
                } catch (_: Exception) {
                    currentOnError("Kamera servisi başlatılamadı. Yeniden dene veya kodu elle gir.")
                }
            }
        }

        onDispose {
            disposed = true
            previewView.setOnTouchListener(null)
            camera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
            analysis?.clearAnalyzer()
            analyzer?.close()
            // Unbind this screen's use cases only, never another screen's camera.
            val ownedUseCases = listOfNotNull(preview, analysis).toTypedArray()
            if (ownedUseCases.isNotEmpty()) runCatching { provider?.unbind(*ownedUseCases) }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val left = size.width * ScanRegion.LEFT
            val right = size.width * ScanRegion.RIGHT
            val top = size.height * ScanRegion.TOP
            val bottom = size.height * ScanRegion.BOTTOM
            val shade = Color.Black.copy(alpha = 0.48f)
            drawRect(shade, size = ComposeSize(size.width, top))
            drawRect(shade, Offset(0f, bottom), ComposeSize(size.width, size.height - bottom))
            drawRect(shade, Offset(0f, top), ComposeSize(left, bottom - top))
            drawRect(shade, Offset(right, top), ComposeSize(size.width - right, bottom - top))
            drawRect(Color(0xFFFF8A32), Offset(left, top),
                ComposeSize(right - left, bottom - top), style = Stroke(2.dp.toPx()))
        }
    }
}
