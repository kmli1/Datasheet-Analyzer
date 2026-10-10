package com.example.datasheetanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.datasheetanalyzer.ui.navigation.DatasheetApp
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(11, 11, 14))
        )
        setContent {
            DatasheetAnalyzerTheme {
                DatasheetApp()
            }
        }
    }
}
