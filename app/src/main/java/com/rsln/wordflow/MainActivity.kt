package com.rsln.wordflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rsln.wordflow.ui.navigation.WordFlowNavHost
import com.rsln.wordflow.ui.theme.Background
import com.rsln.wordflow.ui.theme.WordFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WordFlowApp
        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            WordFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    WordFlowNavHost(app = app, startRoute = navigateTo)
                }
            }
        }
    }
}
