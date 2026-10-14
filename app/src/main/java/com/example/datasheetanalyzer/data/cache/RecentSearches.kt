package com.example.datasheetanalyzer.data.cache

import android.content.Context
import android.util.Base64
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*

data class SavedSearch(val part: String,val criteria: Set<Criterion>,val custom: String,val timestamp: Long,val favorite: Boolean=false,val graphTitles: String="")
class RecentSearches(context: Context) {
    private val preferences=context.applicationContext.getSharedPreferences("recent-searches",Context.MODE_PRIVATE)
    private val flow=MutableStateFlow(read())
    val searches=flow.asStateFlow()
    @Synchronized fun record(part: String,criteria: Set<Criterion>,custom: String,graphTitles: String="") {
        val old=flow.value.firstOrNull { it.part==part }
        store((listOf(SavedSearch(part,criteria,custom.take(500),System.currentTimeMillis(),old?.favorite==true,graphTitles.take(500)))+flow.value.filter { it.part!=part }).take(50))
    }
    @Synchronized fun favorite(part: String) { store(flow.value.map { if(it.part==part) it.copy(favorite=!it.favorite) else it }) }
    @Synchronized fun remove(part: String) { store(flow.value.filter { it.part!=part }) }
    @Synchronized fun clear() { store(emptyList()) }
    private fun store(items: List<SavedSearch>) {
        val encoded=items.map { item ->
            val bytes=ByteArrayOutputStream();DataOutputStream(bytes).use {
                it.writeUTF(item.part);it.writeUTF(item.criteria.joinToString(",") { c -> c.name });it.writeUTF(item.custom)
                it.writeLong(item.timestamp);it.writeBoolean(item.favorite);it.writeUTF(item.graphTitles)
            };Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)
        }.toSet()
        preferences.edit().putStringSet("items",encoded).apply();flow.value=items
    }
    private fun read()=preferences.getStringSet("items",emptySet()).orEmpty().take(50).mapNotNull { raw ->
        runCatching {
            require(raw.length<=5000)
            DataInputStream(ByteArrayInputStream(Base64.decode(raw,Base64.NO_WRAP))).use {
                val part=it.readUTF();require(PartNumberNormalizer.isValid(part))
                val criteria=it.readUTF().split(',').filter { n -> n.isNotEmpty() }.map(Criterion::valueOf).toSet()
                val custom=it.readUTF();require(custom.length<=500)
                val timestamp=it.readLong();val favorite=it.readBoolean()
                val graphs=if(it.available()>0) it.readUTF() else ""
                require(graphs.length<=500 && it.available()==0)
                SavedSearch(part,criteria,custom,timestamp,favorite,graphs)
            }
        }.getOrNull()
    }.sortedByDescending { it.timestamp }
}
