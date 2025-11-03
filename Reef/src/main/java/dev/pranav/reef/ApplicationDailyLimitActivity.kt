package dev.pranav.reef

import android.app.usage.UsageStatsManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import dev.pranav.reef.ui.ReefTheme
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.applyDefaults
import org.nsh07.pomodoro.ui.statsScreen.TimeColumnChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ApplicationDailyLimitActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageName = intent.getStringExtra("package_name") ?: run {
            finish()
            return
        }

        val application = packageManager.getApplicationInfo(packageName, 0)
        val appIcon = packageManager.getApplicationIcon(application)
        val appName = packageManager.getApplicationLabel(application).toString()

        val existingLimitMinutes = AppLimits.getLimit(packageName) / 60000

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        setContent {
            ReefTheme {
                DailyLimitScreen(
                    appName = appName,
                    appIcon = appIcon,
                    packageName = packageName,
                    existingLimitMinutes = existingLimitMinutes.toInt(),
                    usageStatsManager = usageStatsManager,
                    onSave = { minutes ->
                        AppLimits.setLimit(packageName, minutes)
                        AppLimits.saveLimits()
                        finish()
                    },
                    onRemove = {
                        AppLimits.removeLimit(packageName)
                        AppLimits.saveLimits()
                        finish()
                    },
                    onBackPressed = {
                        onBackPressedDispatcher.onBackPressed()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLimitScreen(
    appName: String,
    appIcon: Drawable,
    packageName: String,
    existingLimitMinutes: Int,
    usageStatsManager: UsageStatsManager,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit,
    onBackPressed: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Daily Usage Limit",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        DailyLimitContent(
            appName = appName,
            appIcon = appIcon,
            packageName = packageName,
            existingLimitMinutes = existingLimitMinutes,
            usageStatsManager = usageStatsManager,
            onSave = onSave,
            onRemove = onRemove,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

data class HourlyUsageData(
    val day: String,
    val usageMinutes: Double,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyLimitContent(
    appName: String,
    appIcon: Drawable,
    packageName: String,
    existingLimitMinutes: Int,
    usageStatsManager: UsageStatsManager,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hours by remember { mutableIntStateOf(existingLimitMinutes / 60) }
    var minutes by remember { mutableIntStateOf(existingLimitMinutes % 60) }

    val totalMinutes = hours * 60 + minutes

    val dailyData = remember(packageName) {
        getDailyUsageForLastWeek(packageName, usageStatsManager)
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(dailyData) {
        modelProducer.runTransaction {
            columnSeries { series(dailyData.map { it.usageMinutes.toLong() }) }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // App info section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                appIcon.toBitmap().asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Set daily usage limit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        // Usage graph
        if (dailyData.isNotEmpty()) {
            UsageGraphCard(
                dailyData = dailyData,
                modelProducer = modelProducer
            )
            Spacer(Modifier.height(32.dp))
        }

        // Time picker section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (hours < 12) hours++ },
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Add, "Increase Hours", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(100.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (minutes < 59) minutes++ else if (hours < 12) {
                            hours++; minutes = 0
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Add, "Increase Minutes", modifier = Modifier.size(20.dp))
                }
            }

            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = hours.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = minutes.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Decrement buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (hours > 0) hours-- },
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Remove, "Decrease Hours", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(100.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (minutes > 0) minutes-- else if (hours > 0) {
                            hours--; minutes = 59
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Remove, "Decrease Minutes", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // Labels
            Row(
                modifier = Modifier.fillMaxWidth(0.75f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("Hours", style = MaterialTheme.typography.titleMedium)
                Text("Minutes", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            AssistChip(onClick = { hours = 0; minutes = 15 }, label = { Text("15m") })
            AssistChip(onClick = { hours = 0; minutes = 30 }, label = { Text("30m") })
            AssistChip(onClick = { hours = 1; minutes = 0 }, label = { Text("1h") })
            AssistChip(onClick = { hours = 2; minutes = 0 }, label = { Text("2h") })
            AssistChip(onClick = { hours = 3; minutes = 0 }, label = { Text("3h") })
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSave(totalMinutes) },
                modifier = Modifier.fillMaxWidth(),
                enabled = totalMinutes > 0,
                shapes = ButtonDefaults.shapes(
                    pressedShape = ButtonDefaults.pressedShape
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Set Limit",
                    style = MaterialTheme.typography.labelLargeEmphasized
                )
            }

            if (existingLimitMinutes > 0) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Remove Limit",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun UsageGraphCard(
    dailyData: List<HourlyUsageData>,
    modelProducer: CartesianChartModelProducer
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Usage History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            val totalUsage = dailyData.sumOf { it.usageMinutes }
            val avgUsage = if (dailyData.isNotEmpty()) totalUsage / dailyData.size else 0.0
            Text(
                text = "${avgUsage.toInt()} min/day average",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            TimeColumnChart(
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth(),
                yValueFormatter = CartesianValueFormatter { _, value, _ ->
                    val totalMinutes = value.toInt()
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    when {
                        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                        hours > 0 -> "${hours}h"
                        else -> "${minutes}m"
                    }
                },
                xValueFormatter = CartesianValueFormatter { _, value, _ ->
                    val index = value.toInt()
                    if (index in dailyData.indices) {
                        dailyData[index].day
                    } else ""
                },
                dataValues = dailyData.map { it.usageMinutes.toFloat() }
            )
        }
    }
}

private fun getDailyUsageForLastWeek(
    packageName: String,
    usageStatsManager: UsageStatsManager
): List<HourlyUsageData> {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val dailyMap = mutableMapOf<String, Pair<Long, Long>>()

    for (dayOffset in 0 until 7) {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -dayOffset)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val dayEnd = calendar.timeInMillis

        val usageTime = queryAppUsageEventsForDay(packageName, usageStatsManager, dayStart, dayEnd)

        if (usageTime > 0) {
            val instant = Instant.ofEpochMilli(dayStart)
            val zonedDateTime = instant.atZone(ZoneId.systemDefault())
            val day = zonedDateTime.format(dateFormatter)
            dailyMap[day] = Pair(dayStart, usageTime)
        }
    }

    return dailyMap.map { (day, data) ->
        HourlyUsageData(
            day = day,
            usageMinutes = data.second / 60000.0,
            timestamp = data.first
        )
    }.sortedBy { it.timestamp }
}

private fun queryAppUsageEventsForDay(
    packageName: String,
    usageStatsManager: UsageStatsManager,
    start: Long,
    end: Long
): Long {
    val events = usageStatsManager.queryEvents(start, end)
    var totalUsage = 0L
    var lastResumeTime: Long? = null
    val event = android.app.usage.UsageEvents.Event()

    while (events.hasNextEvent()) {
        events.getNextEvent(event)

        if (event.packageName == packageName) {
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTime = event.timeStamp
                }

                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val startTime = lastResumeTime
                    if (startTime != null) {
                        val duration = event.timeStamp - startTime
                        totalUsage += duration
                        lastResumeTime = null
                    }
                }
            }
        }
    }

    lastResumeTime?.let {
        totalUsage += (end - it)
    }

    return totalUsage
}
