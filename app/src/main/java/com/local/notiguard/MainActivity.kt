package com.local.notiguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.local.notiguard.shizuku.ShizukuManager
import com.local.notiguard.ui.MainScreen
import com.local.notiguard.ui.theme.NotiGuardTheme

class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotiGuardTheme {
                MainScreen(model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission/availability may have changed while we were in the Shizuku app.
        ShizukuManager.refresh()
        // Back from a settings page opened for a failed check → re-read that app's state.
        model.onResume()
    }
}
