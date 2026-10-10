package com.example.datasheetanalyzer.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A single recognizer and in-flight image prevent repeated cold starts and timeout task accumulation. */
class DocumentOcr {
    private val mutex=Mutex()
    private val recognizerHolder=lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val recognizer get()=recognizerHolder.value
    @Volatile private var closed=false
    private var pending: CompletableDeferred<Unit>?=null
    suspend fun recognize(png: File): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                withTimeout(35_000) {
                    check(!closed) { "OCR_CLOSED" }
                    pending?.await()
                    check(!closed) { "OCR_CLOSED" }
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(png.absolutePath,bounds)
                    require(bounds.outWidth in 1..3200 && bounds.outHeight in 1..3200 &&
                        bounds.outWidth.toLong()*bounds.outHeight<=2_000_000 && png.length()<=8L*1024*1024)
                    val bitmap=requireNotNull(BitmapFactory.decodeFile(png.absolutePath))
                    val done=CompletableDeferred<Unit>();pending=done
                    suspendCancellableCoroutine { continuation ->
                        try {
                            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(Executor { it.run() }) { task ->
                                try {
                                    if(continuation.isActive) {
                                        if(task.isSuccessful) continuation.resume(task.result.text)
                                        else continuation.resumeWithException(task.exception ?: IllegalStateException("OCR_FAILED"))
                                    }
                                } finally { bitmap.recycle();done.complete(Unit);if(closed) recognizer.close() }
                            }
                        } catch(e: Exception) {
                            bitmap.recycle();done.complete(Unit)
                            if(continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            } finally { png.delete() }
        }
    }
    fun close() { closed=true;if(recognizerHolder.isInitialized() && pending?.isCompleted!=false) recognizer.close() }

}
