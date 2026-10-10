package com.example.datasheetanalyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.export.PreviewCrop
import com.example.datasheetanalyzer.ui.search.*
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ExtendedFeaturesUiTest {
    @get:Rule val rule=createComposeRule()
    private fun screenshot(name: String) {
        val path=System.getProperty("datasheet.qaDir") ?: return
        rule.runOnIdle {
            val dialog=ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            val view=(dialog?.window ?: activity.window).decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try { view.draw(Canvas(image));File(path,name).apply { parentFile!!.mkdirs();outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } }
            finally { image.recycle() }
        }
    }
    @Test fun exactVariantSelectionUsesNormalizedFullCode() {
        var selected="";var open=true
        rule.setContent { DatasheetAnalyzerTheme {
            if(open) PartVariantDialog("LM383",listOf(PartVariant("LM383N",1,"LM383N DIP")),{ selected=it },{ open=false })
        } }
        rule.onNodeWithText("BU VARYANTI ARA").assertIsNotEnabled()
        rule.onNodeWithText("Tam varyant kodu").performTextInput("lm383a")
        screenshot("ui-variants.png")
        rule.onNodeWithText("BU VARYANTI ARA").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("LM383A",selected) }
    }
    @Test fun pinSourceTextAndSourcePageRemainBoundToOriginalPackage() {
        val base=ExportFixture.document()
        val pages=listOf(PageText(1,"LM383\nPin Configuration\nDIP-8 TOP VIEW\n1 OUT\n2 GND"))
        val document=base.copy(sourceKind=DocumentSource.ARCHIVE_HTML,extraction=base.extraction.copy(pages=pages),sections=DatasheetSectionDetector().detect(pages))
        var opened=0
        rule.setContent { DatasheetAnalyzerTheme { PinoutDialog(document,"LM383",{ opened=it },{}) } }
        rule.onNodeWithText("PİN AÇIKLAMALARINI GÖSTER").performScrollTo().performClick()
        rule.onNodeWithText("Pin Configuration\nDIP-8 TOP VIEW\n1 OUT\n2 GND").performScrollTo().assertExists()
        screenshot("ui-pin-source.png")
        rule.onNodeWithText("KAYNAK SAYFAYI AÇ").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,opened) }
    }
    @Test fun conditionInputsRenderBoundResultAndSourceAction() {
        val report=OperatingConditions.report(ExportFixture.document())
        var opened=0
        rule.setContent { DatasheetAnalyzerTheme {
            var values by remember { mutableStateOf(List(4) { "" }) };var evaluated by remember { mutableStateOf(false) }
            ConditionsForm(report,values,{ i,s -> values=values.toMutableList().apply { this[i]=s };evaluated=false },evaluated,
                { evaluated=true },{ opened=it },{})
        } }
        rule.onNodeWithText("Besleme gerilimi (V)").performTextInput("12")
        rule.onNodeWithText("KOŞULLARI KONTROL ET").performScrollTo().performClick()
        rule.onNodeWithText("Belirtilen önerilen aralık içinde. Diğer test koşulları ve paket şartları ayrıca geçerli olmalı.").performScrollTo().assertExists()
        screenshot("ui-conditions.png")
        rule.onNodeWithText("KAYNAKTA KONTROL ET").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,opened) }
    }
    @Test fun graphAxesCanBeCalibratedAndTappedPointsRemainApproximate() {
        val image=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(android.graphics.Color.WHITE)
        val paint=Paint().apply { color=android.graphics.Color.BLACK;strokeWidth=4f }
        canvas.drawLine(100f,640f,900f,640f,paint);canvas.drawLine(100f,640f,100f,160f,paint)
        canvas.drawLine(100f,640f,900f,160f,paint)
        rule.setContent { DatasheetAnalyzerTheme { GraphReadingDialog(image,"Sentetik test grafiği",1,"LM383",{}) } }
        for((label,value) in listOf("X min" to "0","X maks" to "10","Y min" to "0","Y maks" to "100")) {
            rule.onNodeWithText(label).performScrollTo().performTextInput(value)
        }
        rule.onNodeWithText("EKSENLERİ AYARLA").performScrollTo().performClick()
        fun touch(x: Float,y: Float) {
            rule.onNodeWithContentDescription("Okuma için kaynak grafik").performTouchInput {
                val fit=PreviewCrop.fit(width.toFloat(),height.toFloat(),image.width,image.height)
                click(Offset(fit.left+x*fit.width,fit.top+y*fit.height))
            }
        }
        touch(.1f,.8f);touch(.9f,.2f);touch(.5f,.5f)
        rule.onNode(hasText("≈ X",substring=true)).assertExists()
        rule.onNodeWithText("YAKLAŞIK NOKTALARI CSV KAYDET").assertExists()
        screenshot("ui-graph-reading.png")
        rule.onNodeWithText("SON NOKTAYI SİL").performClick()
        rule.onNode(hasText("≈ X",substring=true)).assertDoesNotExist()
    }
}
