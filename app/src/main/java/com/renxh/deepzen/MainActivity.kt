package com.renxh.deepzen

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.renxh.deepzen.ui.theme.DeepZenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContent {
            DeepZenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) { minutes ->
                        handleStartFocus(minutes)
                    }
                }
            }
        }
    }

    private fun handleStartFocus(minutes: Int) {
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } else {
            val serviceIntent = Intent(FocusAccessibilityService.ACTION_START_FOCUS)
            serviceIntent.setPackage(packageName)
            serviceIntent.putExtra("seconds", minutes * 60)
            sendBroadcast(serviceIntent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, FocusAccessibilityService::class.java)
        val enabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (_: Settings.SettingNotFoundException) {
            0
        }
        if (enabled != 1) return false
        val enabledServices =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val splitter = enabledServices.split(":")
        return splitter.any { it.equals(expectedComponentName.flattenToString(), ignoreCase = true) }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onStartFocus: (Int) -> Unit = {}) {
    var selectedMinutes by remember { mutableStateOf(25) }
    val options = listOf(5, 15, 25)
    Column(
        modifier = modifier.background(ComposeColor.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "选择专注时长", color = ComposeColor.White)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { minutes ->
                val selected = minutes == selectedMinutes
                if (selected) {
                    Button(
                        onClick = { selectedMinutes = minutes },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor.White,
                            contentColor = ComposeColor.Black
                        )
                    ) {
                        Text(text = "${minutes}分钟")
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedMinutes = minutes },
                        border = BorderStroke(1.dp, ComposeColor.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ComposeColor.White
                        )
                    ) {
                        Text(text = "${minutes}分钟")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onStartFocus(selectedMinutes) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor.White,
                contentColor = ComposeColor.Black
            )
        ) {
            Text(text = "开始专注")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DeepZenTheme {
        HomeScreen()
    }
}
