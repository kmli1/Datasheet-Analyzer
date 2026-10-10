package com.example.datasheetanalyzer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.datasheetanalyzer.sandbox.IsolatedPdfValidator
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SandboxIsolationTest {
    @Test fun staticFixtureValidatedByDifferentProcess() = runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val file=File(context.cacheDir,"sandbox-test.pdf")
        try {
            inst.context.assets.open("security/valid.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
            val result=IsolatedPdfValidator(context).validate(file)
            assertTrue(result.code,result.passed);assertEquals(1,result.pages)
        } finally { file.delete() }
    }
}
