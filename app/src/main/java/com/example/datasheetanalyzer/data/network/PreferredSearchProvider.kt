package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Independent providers allow a blocked archive to fall back without changing document validation. */
class PreferredSearchProvider(private val primary: SearchProvider, private val web: SearchProvider,
    private val clock: () -> Long = { System.nanoTime()/1_000_000 },
    private val alternatives: List<SearchProvider> = emptyList()): SearchProvider {
    private val mutex=Mutex();private var last: Long?=null
    override suspend fun searchDatasheets(partNumber: String): List<SearchCandidate> {
        mutex.withLock {
            val now=clock()
            if(last?.let { now-it<3000 }==true) throw SearchFailure(SearchFailureKind.RATE_LIMITED,"En az 3 saniye sonra yeniden dene.")
            last=now
        }
        return coroutineScope {
            suspend fun attempt(provider: SearchProvider)=try {
                Result.success(withTimeout(12_000) { provider.searchDatasheets(partNumber) })
            } catch(c: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive(); Result.failure<List<SearchCandidate>>(c)
            } catch(c: CancellationException) { throw c }
              catch(e: Exception) { Result.failure<List<SearchCandidate>>(e) }
            val results = (listOf(primary, web) + alternatives).map { async { attempt(it) } }.map { it.await() }
            if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
            results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.url }
        }
    }
}
