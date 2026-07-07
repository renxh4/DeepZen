package com.renxh.deepzen

import android.app.Activity
import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private companion object {
        const val WHITELIST_SLOT_COUNT = 9
    }

    private var pendingSlot = 0
    private lateinit var pickAppLauncher: ActivityResultLauncher<Intent>
    private val whitelistPackagesState = mutableStateOf(List<String?>(WHITELIST_SLOT_COUNT) { null })
    private val calendarRecordsState = mutableStateOf<List<Long>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val prefs = getSharedPreferences("focus_prefs", MODE_PRIVATE)
        whitelistPackagesState.value = loadWhitelistPackages(prefs)
        calendarRecordsState.value = loadCalendarRecords()
        val autoFocusDurationDays = if (savedInstanceState == null) {
            getAutoFocusDurationDays(calendarRecordsState.value)
        } else {
            null
        }
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
                var selectedTab by remember { mutableStateOf(MainTab.Focus) }
                var autoFocusDialogState by remember {
                    mutableStateOf(autoFocusDurationDays?.let { AutoFocusDialogState(5, it) })
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(containerColor = ComposeColor.Black) {
                            NavigationBarItem(
                                selected = selectedTab == MainTab.Focus,
                                onClick = { selectedTab = MainTab.Focus },
                                label = { Text(text = "专注") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.Calendar,
                                onClick = { selectedTab = MainTab.Calendar },
                                label = { Text(text = "日历") },
                                icon = {}
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        MainTab.Focus -> HomeScreen(
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

                        MainTab.Calendar -> CalendarScreen(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            records = calendarRecordsState.value,
                            onPickDate = {
                                openDatePicker()
                            }
                        )
                    }
                }
                val dialogState = autoFocusDialogState
                if (dialogState != null) {
                    LaunchedEffect(dialogState.durationDays) {
                        for (remaining in 5 downTo 1) {
                            autoFocusDialogState = autoFocusDialogState?.copy(seconds = remaining)
                            delay(1000)
                        }
                        autoFocusDialogState = null
                        handleStartFocus(5)
                    }
                    AutoFocusCountdownDialog(
                        seconds = dialogState.seconds,
                        durationDays = dialogState.durationDays,
                        onConfirm = {
                            autoFocusDialogState = null
                            handleStartFocus(5)
                        },
                        onCancel = {
                            autoFocusDialogState = null
                        }
                    )
                }
            }
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                saveCalendarRecord(selectedDate.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadCalendarRecords(): List<Long> {
        val raw = getSharedPreferences("focus_prefs", MODE_PRIVATE)
            .getString("calendar_records", null)
            ?: return emptyList()
        return raw.split(",")
            .mapNotNull { it.toLongOrNull() }
            .distinct()
            .sorted()
    }

    private fun saveCalendarRecord(timeMillis: Long) {
        val records = (calendarRecordsState.value + timeMillis)
            .distinct()
            .sorted()
        getSharedPreferences("focus_prefs", MODE_PRIVATE)
            .edit()
            .putString("calendar_records", records.joinToString(","))
            .apply()
        calendarRecordsState.value = records
    }

    private fun loadWhitelistPackages(
        prefs: android.content.SharedPreferences
    ): List<String?> {
        return (1..WHITELIST_SLOT_COUNT).map { index ->
            prefs.getString("whitelist_$index", null)
        }
    }

    private fun getAutoFocusDurationDays(records: List<Long>): Long? {
        val latestRecord = records.maxOrNull() ?: return null
        val durationDays = elapsedDaysSince(latestRecord)
        return durationDays.takeIf { it < 7 }
    }

    private enum class MainTab {
        Focus,
        Calendar
    }

    private data class AutoFocusDialogState(
        val seconds: Int,
        val durationDays: Long
    )

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
        val key = "whitelist_${slot + 1}"
        prefs.edit().putString(key, packageName).apply()
        val current = whitelistPackagesState.value.toMutableList()
        if (slot in 0 until WHITELIST_SLOT_COUNT) {
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
    whitelistPackages: List<String?> = List(9) { null },
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(whitelistUi) { index, (label, iconBitmap) ->
                val hasApp = !whitelistPackages.getOrNull(index).isNullOrEmpty()
                if (hasApp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = label,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                        }
                        Text(text = label, color = ComposeColor.White)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelectWhitelistSlot(index) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        border = BorderStroke(1.dp, ComposeColor.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ComposeColor.White
                        )
                    ) {
                        Text(text = "选择应用")
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoFocusCountdownDialog(
    seconds: Int,
    durationDays: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor.White,
                    contentColor = ComposeColor.Black
                )
            ) {
                Text(text = "确认")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, ComposeColor.White),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ComposeColor.White
                )
            ) {
                Text(text = "取消")
            }
        },
        containerColor = ComposeColor(0xFF171717),
        title = {
            Text(text = "即将开始专注", color = ComposeColor.White)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "已经坚持 ${durationDays}天",
                    color = ComposeColor.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${seconds}s",
                    color = ComposeColor.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "5秒后自动开始5分钟专注",
                    color = ComposeColor(0xFFBDBDBD)
                )
            }
        }
    )
}

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    records: List<Long> = emptyList(),
    onPickDate: () -> Unit = {}
) {
    val sortedRecords = records.sorted()
    val displayRecords = sortedRecords.mapIndexed { index, record ->
        record to sortedRecords.getOrNull(index - 1)
    }.asReversed()
    val latestDurationDays = remember(sortedRecords) {
        sortedRecords.lastOrNull()?.let { latestRecord ->
            elapsedDaysSince(latestRecord)
        }
    }

    Column(
        modifier = modifier
            .background(ComposeColor.Black)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "坚持统计", color = ComposeColor.White)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposeColor(0xFF171717))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "这次和上次坚持了多久",
                    color = ComposeColor(0xFFBDBDBD)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = latestDurationDays?.let { "${it}天" } ?: "暂无记录",
                    color = ComposeColor.White
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPickDate,
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor.White,
                contentColor = ComposeColor.Black
            )
        ) {
            Text(text = "选择时间")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "选择记录",
            color = ComposeColor.White,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (displayRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无选择时间", color = ComposeColor(0xFFBDBDBD))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(displayRecords) { index, (record, previous) ->
                    CalendarRecordRow(
                        dateMillis = record,
                        durationDays = previous?.let { daysBetween(it, record) }
                    )
                    if (index != displayRecords.lastIndex) {
                        HorizontalDivider(color = ComposeColor(0xFF2A2A2A))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRecordRow(
    dateMillis: Long,
    durationDays: Long?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = formatDate(dateMillis), color = ComposeColor.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "坚持时间",
                color = ComposeColor(0xFFBDBDBD)
            )
        }
        Text(
            text = durationDays?.let { "${it}天" } ?: "首次记录",
            color = ComposeColor.White
        )
    }
}

private fun formatDate(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date(timeMillis))
}

private fun daysBetween(startMillis: Long, endMillis: Long): Long {
    val start = Calendar.getInstance().apply {
        timeInMillis = startMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = Calendar.getInstance().apply {
        timeInMillis = endMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return TimeUnit.MILLISECONDS.toDays(end.timeInMillis - start.timeInMillis)
}

private fun elapsedDaysSince(timeMillis: Long): Long {
    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timeMillis)
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DeepZenTheme {
        HomeScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
    DeepZenTheme {
        CalendarScreen(
            records = listOf(
                Calendar.getInstance().apply {
                    set(2026, Calendar.JULY, 1)
                }.timeInMillis,
                Calendar.getInstance().apply {
                    set(2026, Calendar.JULY, 6)
                }.timeInMillis
            )
        )
    }
}
