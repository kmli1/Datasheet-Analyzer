package com.example.datasheetanalyzer
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.datasheetanalyzer.data.cache.DocumentCache
import com.example.datasheetanalyzer.data.repository.ResolverDependencies
import com.example.datasheetanalyzer.domain.security.QuarantinedPdf
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage7NavigationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun verifiedCacheOpensSeparateResultsAndReturnsToSources()=runBlocking<Unit> {
        val context=rule.activity.applicationContext;val local=ResolverDependencies.local(context);local.cache.clear();local.recent.clear()
        val file=File(context.cacheDir,"stage7-nav-original.pdf");Stage7Fixture.pdf(file)
        val sha=DocumentCache.digest(file);val input=QuarantinedPdf(file,"local://document/$sha",file.length(),sha);val pipeline=ResolverDependencies.pipeline(context)
        try {
            val result=pipeline.analyze("LM383",DocumentCache.localCandidate("LM383",input.finalUrl),emptyList(),{},localPdf=input);assertTrue(result.verification.automaticEligible)
            rule.onNodeWithText("Parça numarası").performTextInput("LM383")
            rule.onNodeWithText("Supply Voltage").performScrollTo().performClick()
            rule.onNodeWithText("DATASHEET BUL  →").performScrollTo().performClick()
            rule.waitUntil(30000) { rule.onAllNodesWithText("Analiz sonuçları").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Analiz sonuçları").assertExists();rule.onNodeWithText("Kayıtlı belgeden açıldı · çevrimdışı kullanım").assertExists()
            rule.onNodeWithText("← KAYNAKLAR").performClick();rule.onNodeWithText("Kaynaklar").assertExists()
        } finally { pipeline.close();file.delete();local.cache.clear();local.recent.clear() }
    }
    @Test fun privacyAndHistoryRoutesAreReachable() {
        rule.onNodeWithText("GİZLİLİK").performScrollTo().performClick();rule.onNodeWithText("Gizlilik ve bilgi").assertExists()
        rule.onNodeWithText("← GERİ").performClick();rule.onNodeWithText("KAYITLARIM").performScrollTo().performClick();rule.onNodeWithText("Kayıtlarım").assertExists()
    }
}
