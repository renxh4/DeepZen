package com.renxh.deepzen

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.renxh.deepzen.ui.theme.DeepZenTheme

class MainActivity : ComponentActivity() {
    private var pendingSlot = 0
    private lateinit var pickAppLauncher: ActivityResultLauncher<Intent>
    private val whitelistPackagesState = mutableStateOf(listOf<String?>(null, null, null, null, null, null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val prefs = getSharedPreferences("focus_prefs", MODE_PRIVATE)
        whitelistPackagesState.value = listOf(
            prefs.getString("whitelist_1", null),
            prefs.getString("whitelist_2", null),
            prefs.getString("whitelist_3", null),
            prefs.getString("whitelist_4", null),
            prefs.getString("whitelist_5", null),
            prefs.getString("whitelist_6", null)
        )
        pickAppLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
                val data = result.data ?: return@registerForActivityResult
                val component = data.component ?: return@registerForActivityResult
                val pkg = component.packageName
                saveWhitelistApp(pendingSlot, pkg)
            }
        setContent {
            DeepZenTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        whitelistPackages = whitelistPackagesState.value,
                        onStartFocus = { minutes ->
                            handleStartFocus(minutes)
                        },
                        onSelectWhitelistSlot = { index ->
                            openAppPicker(index)
                        }
                    )
                }
            }
        }
    }

    private fun openAppPicker(slot: Int) {
        pendingSlot = slot
        val baseIntent = Intent(Intent.ACTION_MAIN)
        baseIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY)
        pickIntent.putExtra(Intent.EXTRA_INTENT, baseIntent)
        pickAppLauncher.launch(pickIntent)
    }

    private fun saveWhitelistApp(slot: Int, packageName: String) {
        val prefs = getSharedPreferences("focus_prefs", MODE_PRIVATE)
        val key = when (slot) {
            0 -> "whitelist_1"
            1 -> "whitelist_2"
            2 -> "whitelist_3"
            3 -> "whitelist_4"
            4 -> "whitelist_5"
            else -> "whitelist_6"
        }
        prefs.edit().putString(key, packageName).apply()
        val current = whitelistPackagesState.value.toMutableList()
        if (slot in 0..5) {
            current[slot] = packageName
            whitelistPackagesState.value = current
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
fun HomeScreen(
    modifier: Modifier = Modifier,
    whitelistPackages: List<String?> = listOf(null, null, null, null, null, null),
    onStartFocus: (Int) -> Unit = {},
    onSelectWhitelistSlot: (Int) -> Unit = {}
) {
    var selectedMinutes by remember { mutableStateOf(25) }
    val options = listOf(5, 15, 25)
    val context = LocalContext.current
    val pm = context.packageManager
    val whitelistUi: List<Pair<String, androidx.compose.ui.graphics.ImageBitmap?>> =
        whitelistPackages.map { pkg ->
            if (pkg.isNullOrEmpty()) {
                "选择应用" to null
            } else {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val bitmap = icon.toBitmap(64, 64).asImageBitmap()
                    label to bitmap
                } catch (e: Exception) {
                    pkg to null
                }
            }
        }
    Column(
        modifier = modifier.background(ComposeColor.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "选择专注时长", color = ComposeColor.White)
        Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))
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
        Spacer(modifier = androidx.compose.ui.Modifier.height(32.dp))
        Button(
            onClick = { onStartFocus(selectedMinutes) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor.White,
                contentColor = ComposeColor.Black
            )
        ) {
            Text(text = "开始专注")
        }
        Spacer(modifier = androidx.compose.ui.Modifier.height(32.dp))
        Text(text = "白名单应用", color = ComposeColor.White)
        Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            whitelistUi.forEachIndexed { index, (label, iconBitmap) ->
                val hasApp = !whitelistPackages.getOrNull(index).isNullOrEmpty()
                OutlinedButton(
                    onClick = { onSelectWhitelistSlot(index) },
                    border = if (hasApp) null else BorderStroke(1.dp, ComposeColor.White),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ComposeColor.White
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = label,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                        }
                        Text(text = label)
                    }
                }
            }
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
