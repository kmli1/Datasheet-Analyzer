package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.ui.home.HomeUiState

@Composable
fun SearchPreviewScreen(state: HomeUiState, onBack: () -> Unit) {
    Scaffold { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Arama özeti", style = MaterialTheme.typography.headlineMedium)
            Text(state.partNumber, style = MaterialTheme.typography.headlineSmall)
            Text("Seçilen kriterler", style = MaterialTheme.typography.titleMedium)
            Criterion.entries.filter { it in state.selectedCriteria }.forEach {
                Text("• ${it.label}")
            }
            if (state.customCriterion.isNotBlank()) {
                Text("Serbest kriter", style = MaterialTheme.typography.titleMedium)
                Text(state.customCriterion)
            }
            Card {
                Text(
                    "Girişlerin alındı. Bu ekran yalnızca formu ve ekran geçişini doğrular. " +
                        "Datasheet araması Aşama 3'te eklenecek. Henüz teknik sonuç üretilmedi.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("GİRDİLERİ DÜZENLE")
            }
        }
    }
}
