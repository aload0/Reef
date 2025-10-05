package dev.pranav.reef.util

import android.app.Activity
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat


fun Activity.setDarkStatusBar() {
    val windowInsetsController =
        WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.isAppearanceLightStatusBars = false
}

fun applyWindowInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
}

fun ComponentActivity.applyDefaults() {
    setDarkStatusBar()
    enableEdgeToEdge()
}

