package com.reelpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reelpilot.app.ui.ReelPilotApp
import com.reelpilot.app.ui.theme.ReelPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ReelPilotTheme {
                ReelPilotApp()
            }
        }
    }
}
