package com.example.datasheetanalyzer.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.camera.CameraPreview

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed -> granted = allowed }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasCameraPermission()
                resumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!granted && !requested) {
            requested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Komponent tara", style = MaterialTheme.typography.headlineMedium)
            Text("Yazıyı çerçevenin içine getir. Netlemek için görüntüye dokun.")

            if (!granted) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kamera izni gerekli. İzin vermeden manuel giriş kullanabilirsin.")
                        Button(onClick = {
                            requested = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text("KAMERA İZNİ VER") }
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {
                                viewModel.onFailure("Uygulama ayarları açılamadı. Telefonun Ayarlar menüsünü kullan.")
                            }
                        }) { Text("UYGULAMA AYARLARI") }
                    }
                }
            } else if (state.scanning && resumed) {
                CameraPreview(
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    onLines = viewModel::onLines,
                    onError = viewModel::onFailure
                )
                Text(
                    if (!state.hasProcessedFrame) "Kamera ve yazı tanıma hazırlanıyor…"
                    else if (state.candidates.isEmpty()) "Henüz kod adayı bulunamadı. Işığı ve mesafeyi ayarla."
                    else "Bir aday seç; ardından kodu kontrol et.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!state.scanning) {
                Text("Tarama durduruldu. Kodu aşağıdan düzeltebilirsin.")
            }

            if (state.candidates.isNotEmpty()) {
                Text("Kod adayları", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.candidates.forEach { candidate ->
                        AssistChip(onClick = { viewModel.selectCandidate(candidate.code) },
                            label = { Text(candidate.code) })
                    }
                }
            }
            if (state.detectedLines.isNotEmpty()) {
                Text("Algılanan yazı", style = MaterialTheme.typography.titleSmall)
                Text(state.detectedLines.take(8).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::editDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kodu kontrol et / düzelt") },
                placeholder = { Text("Örnek: LM383") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = { Text("O/0, I/1 ve S/5 otomatik değiştirilmez.") }
            )
            Text("Adaylar doğrulanmış parça kimliği değildir; tarih veya üretim kodu da olabilir.",
                style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { viewModel.confirmedCode()?.let(onConfirm) },
                enabled = state.draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("CONTINUE · KODU KULLAN") }
            if (!state.scanning) {
                OutlinedButton(onClick = viewModel::rescan, modifier = Modifier.fillMaxWidth()) {
                    Text("YENİDEN TARA")
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("MANUEL GİRİŞE DÖN")
            }
        }
    }
}
