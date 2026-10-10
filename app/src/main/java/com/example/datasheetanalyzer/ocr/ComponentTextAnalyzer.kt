package com.example.datasheetanalyzer.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.datasheetanalyzer.camera.FrameCropper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ComponentTextAnalyzer(
    private val callbackExecutor: Executor,
    private val onLines: (List<String>) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val gate = AnalysisGate(intervalMs = 400L)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val recognizerClosed = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!gate.tryAcquire(SystemClock.elapsedRealtime())) {
            image.close()
            return
        }

        val bitmap: Bitmap
        try {
            bitmap = FrameCropper.crop(image)
        } catch (_: Exception) {
            callbackExecutor.execute {
                if (!gate.isStopped()) onError("Kamera görüntüsü okunamadı. Yeniden taramayı dene.")
            }
            complete()
            return
        } finally {
            // ML Kit receives an owned bitmap, so the camera buffer is released now.
            image.close()
        }

        if (gate.isStopped()) {
            bitmap.recycle()
            complete()
            return
        }

        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener(callbackExecutor) { task ->
                    try {
                        if (!gate.isStopped()) {
                            if (task.isSuccessful) {
                                val lines = task.result.textBlocks.flatMap { it.lines }
                                    .take(24).map { it.text.take(256) }
                                onLines(lines)
                            } else {
                                onError("Yazı tanıma tamamlanamadı. Yeniden dene veya kodu elle gir.")
                            }
                        }
                    } finally {
                        bitmap.recycle()
                        complete()
                    }
                }
        } catch (_: Exception) {
            bitmap.recycle()
            callbackExecutor.execute {
                if (!gate.isStopped()) onError("Yazı tanıma başlatılamadı. Yeniden taramayı dene.")
            }
            complete()
        }
    }

    private fun complete() {
        if (gate.complete()) closeRecognizer()
    }

    override fun close() {
        if (gate.stop()) closeRecognizer()
    }

    private fun closeRecognizer() {
        if (recognizerClosed.compareAndSet(false, true)) recognizer.close()
    }
}
