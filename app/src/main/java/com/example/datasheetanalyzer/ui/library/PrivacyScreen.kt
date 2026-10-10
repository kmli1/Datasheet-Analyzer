package com.example.datasheetanalyzer.ui.library
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
@Composable fun PrivacyScreen(onBack: ()->Unit) {
 val context=LocalContext.current
 val privacy=remember(context) { context.assets.open("privacy-tr.txt").bufferedReader().use { it.readText() } }
 var licenses by remember { mutableStateOf(false) }
 Scaffold { inset -> Column(Modifier.fillMaxSize().padding(inset).padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  TextButton(onClick=onBack) { Text("← GERİ") };Text("Gizlilik ve bilgi",style=MaterialTheme.typography.headlineLarge);Text(privacy)
  OutlinedButton(onClick={ licenses=true }) { Text("ÜÇÜNCÜ TARAF LİSANSLARI") }
 } }
 if(licenses) AlertDialog(onDismissRequest={ licenses=false },title={ Text("Üçüncü taraf lisansları") },text={ Text(remember { context.assets.open("third-party-notices.txt").bufferedReader().use { it.readText() } },Modifier.verticalScroll(rememberScrollState())) },confirmButton={ TextButton(onClick={ licenses=false }) { Text("KAPAT") } })
}
