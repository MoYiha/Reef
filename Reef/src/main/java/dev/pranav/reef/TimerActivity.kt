package dev.pranav.reef

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.ui.ReefTheme
import dev.pranav.reef.util.AndroidUtilities
import dev.pranav.reef.util.prefs

class TimerActivity : ComponentActivity() {

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val left = intent.getStringExtra(FocusModeService.EXTRA_TIME_LEFT) ?: "00:00"
            currentTimeLeft = left
            isPaused = FocusModeService.isPaused

            if (left == "00:00") {
                isTimerRunning = false
                isPaused = false
                val androidUtilities = AndroidUtilities()
                androidUtilities.vibrate(context, 500)
            }
        }
    }

    private var currentTimeLeft by mutableStateOf("00:00")
    private var isTimerRunning by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)
    private var isStrictMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                timerReceiver,
                IntentFilter("dev.pranav.reef.TIMER_UPDATED"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(
                timerReceiver,
                IntentFilter("dev.pranav.reef.TIMER_UPDATED")
            )
        }

        if (FocusModeService.isRunning || FocusModeService.isPaused) {
            isTimerRunning = true
            isPaused = FocusModeService.isPaused
            isStrictMode = prefs.getBoolean("strict_mode", false)
            currentTimeLeft = intent.getStringExtra(FocusModeService.EXTRA_TIME_LEFT) ?: "00:00"
        } else if (intent.hasExtra(FocusModeService.EXTRA_TIME_LEFT)) {
            currentTimeLeft = intent.getStringExtra(FocusModeService.EXTRA_TIME_LEFT) ?: "00:00"
            isTimerRunning = true
            isStrictMode = prefs.getBoolean("strict_mode", false)
        }

        setContent {
            ReefTheme {
                TimerScreen(
                    isTimerRunning = isTimerRunning,
                    isPaused = isPaused,
                    currentTimeLeft = currentTimeLeft,
                    isStrictMode = isStrictMode,
                    onStartTimer = { minutes, strictMode ->
                        startFocusMode(minutes, strictMode)
                    },
                    onPauseTimer = {
                        pauseFocusMode()
                    },
                    onResumeTimer = {
                        resumeFocusMode()
                    },
                    onBackPressed = {
                        handleBackPress()
                    }
                )
            }
        }
    }

    private fun startFocusMode(minutes: Int, strictMode: Boolean) {
        prefs.edit {
            putBoolean("focus_mode", true)
            putLong("focus_time", minutes * 60 * 1000L)
            putBoolean("strict_mode", strictMode)
        }

        val intent = Intent(this, FocusModeService::class.java)
        startForegroundService(intent)

        isTimerRunning = true
        isPaused = false
        isStrictMode = strictMode
    }

    private fun pauseFocusMode() {
        val intent = Intent(this, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_PAUSE
        }
        startService(intent)
        isPaused = true
    }

    private fun resumeFocusMode() {
        val intent = Intent(this, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_RESUME
        }
        startService(intent)
        isPaused = false
    }

    private fun handleBackPress() {
        if (FocusModeService.isRunning && !FocusModeService.isPaused) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            startActivity(intent)
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(timerReceiver)
        if (!FocusModeService.isRunning && !FocusModeService.isPaused) {
            prefs.edit {
                putBoolean("focus_mode", false)
                remove("strict_mode")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    isTimerRunning: Boolean,
    isPaused: Boolean,
    currentTimeLeft: String,
    isStrictMode: Boolean,
    onStartTimer: (Int, Boolean) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Focus Timer",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (!isStrictMode) {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isTimerRunning,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "timer_state"
            ) { running ->
                if (running) {
                    RunningTimerView(
                        timeLeft = currentTimeLeft,
                        isPaused = isPaused,
                        isStrictMode = isStrictMode,
                        onPause = onPauseTimer,
                        onResume = onResumeTimer
                    )
                } else {
                    TimerSetupView(onStart = onStartTimer)
                }
            }
        }
    }
}

@Preview
@Composable
fun TimerScreenPreview() {
    TimerScreen(
        isTimerRunning = false,
        isPaused = false,
        currentTimeLeft = "25:00",
        isStrictMode = false,
        onStartTimer = { _, _ -> },
        onPauseTimer = {},
        onResumeTimer = {},
        onBackPressed = {}
    )
}

@Preview
@Composable
fun TimerScreenRunningPreview() {
    TimerScreen(
        isTimerRunning = true,
        isPaused = false,
        currentTimeLeft = "24:59",
        isStrictMode = false,
        onStartTimer = { _, _ -> },
        onPauseTimer = {},
        onResumeTimer = {},
        onBackPressed = {}
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerSetupView(onStart: (Int, Boolean) -> Unit) {
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(30) }
    var isStrictMode by remember { mutableStateOf(false) }

    val totalMinutes = hours * 60 + minutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (hours < 12) hours++ },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Add, "Increase Hours")
                }
                Spacer(Modifier.width(120.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (minutes < 59) minutes++ else if (hours < 12) {
                            hours++; minutes = 0
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Add, "Increase Minutes")
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
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = minutes.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
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
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Remove, "Decrease Hours")
                }
                Spacer(Modifier.width(120.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (minutes > 0) minutes-- else if (hours > 0) {
                            hours--; minutes = 59
                        }
                    },
                    modifier = Modifier.size(64.dp),
                    shapes = IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.extraLargeSquareShape,
                        pressedShape = IconButtonDefaults.largePressedShape
                    )
                ) {
                    Icon(Icons.Rounded.Remove, "Decrease Minutes")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Labels
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("Hours", style = MaterialTheme.typography.titleMedium)
                Text("Minutes", style = MaterialTheme.typography.titleMedium)
            }
        }


        Spacer(Modifier.weight(1f))

        // Strict Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isStrictMode) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column {
                    Text(
                        text = if (isStrictMode) "Strict Mode" else "Flexible Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isStrictMode) "No pausing allowed" else "Pause & resume anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isStrictMode,
                onCheckedChange = { isStrictMode = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            AssistChip(onClick = { hours = 0; minutes = 5 }, label = { Text("5m") })
            AssistChip(onClick = { hours = 0; minutes = 15 }, label = { Text("15m") })
            AssistChip(onClick = { hours = 0; minutes = 30 }, label = { Text("30m") })
            AssistChip(onClick = { hours = 1; minutes = 0 }, label = { Text("1h") })
            AssistChip(onClick = { hours = 1; minutes = 30 }, label = { Text("1h 30m") })
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onStart(totalMinutes, isStrictMode) },
            modifier = Modifier
                .fillMaxWidth(),
            enabled = totalMinutes > 0,
            shapes = ButtonDefaults.shapes(
                pressedShape = ButtonDefaults.pressedShape
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunningTimerView(
    timeLeft: String,
    isPaused: Boolean,
    isStrictMode: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = timeLeft,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 100.sp,
                textAlign = TextAlign.Center
            )

            if (isStrictMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Strict Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (isPaused) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Paused",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (!isStrictMode) {
            RunningTimerActions(
                isPaused = isPaused,
                onPause = onPause,
                onResume = onResume,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        } else {
            Text(
                text = "No interruptions allowed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun RunningTimerActions(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilledTonalIconToggleButton(
            checked = isPaused,
            onCheckedChange = { if (isPaused) onResume() else onPause() },
            shapes = IconToggleButtonShapes(
                shape = IconButtonDefaults.largeRoundShape,
                pressedShape = IconButtonDefaults.extraLargeSelectedRoundShape,
                checkedShape = IconButtonDefaults.extraLargeSelectedRoundShape
            ),
            modifier = Modifier
                .fillMaxHeight(0.15f)
                .fillMaxWidth(0.9f),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    modifier = Modifier
                        .fillMaxHeight(0.6f)
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = if (isPaused) "Resume" else "Pause",
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimerSetupViewPreview() {
    ReefTheme {
        TimerSetupView(onStart = { _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
fun RunningTimerViewPreview() {
    ReefTheme {
        RunningTimerView(
            timeLeft = "24:59",
            isPaused = false,
            isStrictMode = false,
            onPause = {},
            onResume = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RunningTimerViewPausedPreview() {
    ReefTheme {
        RunningTimerView(
            timeLeft = "24:59",
            isPaused = true,
            isStrictMode = false,
            onPause = {},
            onResume = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RunningTimerViewStrictModePreview() {
    ReefTheme {
        RunningTimerView(
            timeLeft = "24:59",
            isPaused = false,
            isStrictMode = true,
            onPause = {},
            onResume = {}
        )
    }
}
