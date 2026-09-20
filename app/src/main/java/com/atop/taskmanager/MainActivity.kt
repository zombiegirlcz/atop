package com.atop.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atop.taskmanager.ui.ProcessListScreen
import com.atop.taskmanager.ui.theme.AtopTheme

/**
 * Vstupní bod. Sám nedělá nic jiného než zapojení UI vrstvy.
 * Veškerá logika je v ProcessListViewModel (a níž).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtopTheme {
                ProcessListScreen()
            }
        }
    }
}