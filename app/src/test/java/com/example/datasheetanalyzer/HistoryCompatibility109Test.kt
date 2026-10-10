package com.example.datasheetanalyzer

import android.content.Context
import android.util.Base64
import com.example.datasheetanalyzer.data.cache.DocumentCache
import com.example.datasheetanalyzer.data.cache.RecentSearches
import com.example.datasheetanalyzer.domain.model.Criterion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class HistoryCompatibility109Test {
    private val context: Context get()=RuntimeEnvironment.getApplication()
    @Before fun clearHistory() { context.getSharedPreferences("recent-searches",Context.MODE_PRIVATE).edit().clear().commit() }
    private fun legacyEntry(graphs: String?=null,trailing: Boolean=false): String {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeUTF("TMP102");it.writeUTF(Criterion.entries.first().name);it.writeUTF("Supply Voltage")
            it.writeLong(1700000000000L);it.writeBoolean(true)
            graphs?.let { graph -> it.writeUTF(graph) }
            if(trailing) it.writeByte(1)
        }
        return Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)
    }
    private fun seed(value: String) { context.getSharedPreferences("recent-searches",Context.MODE_PRIVATE).edit().putStringSet("items",setOf(value)).commit() }
    @Test fun historicalRecordsWithoutGraphTitlesStillOpen() {
        seed(legacyEntry());val record=RecentSearches(context).searches.value.single()
        assertEquals("TMP102",record.part);assertEquals("",record.graphTitles);assertTrue(record.favorite)
    }
    @Test fun existingGraphQueriesAndFavoritesSurviveReopening() {
        seed(legacyEntry("Temperature accuracy"));val recent=RecentSearches(context)
        recent.record("TMP102",setOf(Criterion.entries.first()),"Supply Voltage","Temperature accuracy")
        val restored=RecentSearches(context).searches.value.single()
        assertEquals("Temperature accuracy",restored.graphTitles);assertTrue(restored.favorite)
        assertEquals(704,DocumentCache.POLICY)
    }
    @Test fun malformedOrOversizedOptionalTailIsIgnored() {
        seed(legacyEntry("graph",trailing=true));assertTrue(RecentSearches(context).searches.value.isEmpty())
        seed(legacyEntry("x".repeat(501)));assertTrue(RecentSearches(context).searches.value.isEmpty())
    }
}
