package com.example.datasheetanalyzer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.model.CriterionGroups
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme
import com.example.datasheetanalyzer.ui.theme.ElectricOrange
import com.example.datasheetanalyzer.ui.workspace.*
import com.example.datasheetanalyzer.data.workspace.WorkspaceStore
import com.example.datasheetanalyzer.domain.model.AnalysisTemplate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPartNumberChanged: (String) -> Unit,
    onCustomCriterionChanged: (String) -> Unit,
    onCriterionToggled: (Criterion) -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit,
    onLibrary: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onGraphTitlesChanged: (String) -> Unit = {},
    workspace: WorkspaceStore?=null,
    onApplyTemplate: (AnalysisTemplate)->Unit = {},
    onProjects: ()->Unit = {},
    onCompare: ()->Unit = {},
    onSensors: ()->Unit = {}
) {
    val focus = LocalFocusManager.current
    var guide by remember { mutableStateOf(false) }
    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(ElectricOrange), contentAlignment = Alignment.Center) {
                    Text("ϟ", color = Color(0xFF211005), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("DATASHEET", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("ANALYZER", color = ElectricOrange, style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 4.sp)
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF241C16), Color(0xFF151518))))) {
                Canvas(Modifier.matchParentSize()) {
                    val wire = ElectricOrange.copy(alpha = 0.18f)
                    val y = size.height * 0.22f
                    drawLine(wire, Offset(size.width * 0.65f, 0f), Offset(size.width * 0.65f, y), 2.dp.toPx())
                    drawLine(wire, Offset(size.width * 0.65f, y), Offset(size.width * 0.88f, y), 2.dp.toPx())
                    drawLine(wire, Offset(size.width * 0.88f, y), Offset(size.width * 0.88f, size.height), 2.dp.toPx())
                    drawCircle(wire, 5.dp.toPx(), Offset(size.width * 0.65f, y))
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("KOMPONENTTEN KAYNAĞA", color = ElectricOrange,
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Text("Devreni tanı.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Kodunu gir, datasheet kaynaklarını bul.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InputCard {
                SectionLabel("01", "Komponent kodu")
                OutlinedTextField(
                    value = state.partNumber, onValueChange = onPartNumberChanged,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Parça numarası") },
                    placeholder = { Text("LM383") }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    supportingText = { Text("Elle yaz veya kamerayla oku · ${state.partNumber.length}/64") },
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
                )
                OutlinedButton(onClick = { focus.clearFocus(); onScan() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.loading,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text("KAMERAYLA TARA")
                }
                OutlinedButton(onClick={ focus.clearFocus();onSensors() },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!state.loading) {
                    Text("MUADİL SENSÖRLER")
                }
            }
            InputCard {
                SectionLabel("02", "Ne öğrenmek istiyorsun?")
                workspace?.let { TemplateControls(state,it,onApplyTemplate) }
                TextButton(onClick={ guide=true }) { Text("TÜRKÇE TERİM REHBERİ") }
                OutlinedTextField(
                    value = state.customCriterion, onValueChange = onCustomCriterionChanged,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Kendi kriterini yaz") },
                    placeholder = { Text("Besleme gerilimi, pinout…") }, minLines = 2, maxLines = 4,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
                )
                Text("HIZLI SEÇİM  /  ${state.selectedCriteria.size} SEÇİLİ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                CriterionGroups.groups.forEach { group ->
                    Text(group.label,style=MaterialTheme.typography.titleSmall,color=ElectricOrange)
                    group.criteria.chunked(2).forEach { choices ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            choices.forEach { criterion ->
                                FilterChip(
                                    selected=criterion in state.selectedCriteria,onClick={ onCriterionToggled(criterion) },
                                    label={ Text(criterion.label,style=MaterialTheme.typography.bodySmall) },enabled=!state.loading,
                                    modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=56.dp),shape=RoundedCornerShape(12.dp),
                                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer),
                                    leadingIcon=if(criterion in state.selectedCriteria) { { Text("✓") } } else null
                                )
                            }
                            if(choices.size==1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                OutlinedTextField(value=state.graphTitles,onValueChange=onGraphTitlesChanged,
                    modifier=Modifier.fillMaxWidth(),label={ Text("Grafik başlığı (isteğe bağlı)") },
                    placeholder={ Text("Output Power vs Supply Voltage") },minLines=1,maxLines=3,
                    enabled=!state.loading,supportingText={ Text("Datasheet'teki başlığı yaz. En fazla 3 grafik; her başlık ayrı satırda.") },
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={ focus.clearFocus() }))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(onClick = { focus.clearFocus(); onSearch() }, enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                Text("DATASHEET BUL  →", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onProjects,modifier=Modifier.weight(1f)) { Text("PROJELERİM") }
                OutlinedButton(onClick=onCompare,modifier=Modifier.weight(1f)) { Text("KARŞILAŞTIR") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onLibrary,modifier=Modifier.weight(1f)) { Text("KAYITLARIM") }
                OutlinedButton(onClick=onPrivacy,modifier=Modifier.weight(1f)) { Text("GİZLİLİK") }
            }
            Text("Kaynağı bul; seçtiğin teknik değerleri, koşullarını ve datasheet sayfalarını incele.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(guide) TermGuideDialog(onDismiss={ guide=false })
}

@Composable
private fun InputCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SectionLabel(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = ElectricOrange, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge)
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun HomeScreenPreview() {
    DatasheetAnalyzerTheme {
        HomeScreen(HomeUiState(partNumber = "LM383", selectedCriteria = setOf(Criterion.SUPPLY_VOLTAGE)), {}, {}, {}, {}, {})
    }
}
