package com.example.datasheetanalyzer.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.example.datasheetanalyzer.domain.security.*
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class IsolatedPdfValidator(context: Context,private val config: SecurityConfig=SecurityConfig()): PdfStructureValidator {
    private val app=context.applicationContext
    override suspend fun validate(file: File): SandboxResult = withContext(Dispatchers.Main.immediate) {
        withTimeout(config.sandboxTimeoutMs) {
            suspendCancellableCoroutine { c ->
                var bound=false;var remote: IPdfSandbox?=null
                lateinit var connection: ServiceConnection
                fun release(cancel: Boolean) {
                    if(cancel) runCatching { remote?.cancel() }
                    if(bound) { bound=false;runCatching { app.unbindService(connection) } }
                }
                fun finish(result: SandboxResult) {
                    if(c.isActive) c.resume(result)
                    release(false)
                }
                connection=object: ServiceConnection {
                    override fun onServiceConnected(name: ComponentName,binder: IBinder) {
                        if(!c.isActive) { release(true);return }
                        remote=IPdfSandbox.Stub.asInterface(binder)
                        try {
                            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                remote!!.inspect(fd,object: IPdfSandboxCallback.Stub() {
                                    override fun completed(passed: Boolean,code: String,pages: Int,processId: Int) {
                                        android.os.Handler(app.mainLooper).post {
                                            val isolated=processId!=Process.myPid()
                                            finish(SandboxResult(passed && isolated,if(isolated) code else "NOT_ISOLATED",pages))
                                        }
                                    }
                                })
                            }
                        } catch(_: Exception) { finish(SandboxResult(false,"SANDBOX_BIND")) }
                    }
                    override fun onServiceDisconnected(name: ComponentName) { finish(SandboxResult(false,"SANDBOX_DIED")) }
                    override fun onBindingDied(name: ComponentName) { finish(SandboxResult(false,"SANDBOX_DIED")) }
                    override fun onNullBinding(name: ComponentName) { finish(SandboxResult(false,"SANDBOX_BIND")) }
                }
                c.invokeOnCancellation {
                    android.os.Handler(app.mainLooper).post { release(true) }
                }
                bound=app.bindService(Intent(app,PdfSandboxService::class.java),connection,Context.BIND_AUTO_CREATE)
                if(!bound) finish(SandboxResult(false,"SANDBOX_BIND"))
            }
        }
    }
}
