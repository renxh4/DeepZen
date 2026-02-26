package com.renxh.deepzen

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.renxh.deepzen.ui.theme.DeepZenTheme
import kotlinx.coroutines.delay

class GuardActivity : ComponentActivity() {
    companion object {
        const val PREFS_NAME = "focus_prefs"
        const val KEY_FOCUS_ACTIVE = "focus_active"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setFocusActive(true)
        startLockTaskSafe()
        startOverlayIfAllowed()
        setContent {
            DeepZenTheme {
                CountdownScreen(
                    totalSeconds = 5 * 60,
                    modifier = Modifier.fillMaxSize()
                ) {
                    stopLockTaskSafe()
                    setFocusActive(false)
                    finish()
                }
            }
        }
    }

    private fun startLockTaskSafe() {
        try {
            startLockTask()
        } catch (_: IllegalStateException) {
        }
    }

    private fun stopLockTaskSafe() {
        try {
            stopLockTask()
        } catch (_: IllegalStateException) {
        }
    }

    private fun setFocusActive(active: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, active).apply()
    }

    private fun startOverlayIfAllowed() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FocusOverlayService::class.java)
            startService(intent)
        }
    }
}

@Composable
fun CountdownScreen(
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(totalSeconds) }
    val updatedOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(totalSeconds) {
        remainingSeconds = totalSeconds
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
        updatedOnFinished()
    }

    BackHandler(enabled = true) {
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatSeconds(remainingSeconds),
            style = MaterialTheme.typography.displayLarge
        )
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun CountdownScreenPreview() {
    DeepZenTheme {
        CountdownScreen(totalSeconds = 5 * 60) {}
    }
}
