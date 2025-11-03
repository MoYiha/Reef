package dev.pranav.reef

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import dev.pranav.reef.ui.ReefTheme
import dev.pranav.reef.util.RoutineManager
import dev.pranav.reef.util.applyDefaults
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class RoutinesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()

        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        super.onCreate(savedInstanceState)

        setContent {
            ReefTheme {
                RoutinesScreen(
                    onBackPressed = { onBackPressedDispatcher.onBackPressed() },
                    onCreateRoutine = {
                        startActivity(Intent(this, CreateRoutineActivity::class.java))
                    },
                    onEditRoutine = { routine ->
                        val intent = Intent(this, CreateRoutineActivity::class.java).apply {
                            putExtra("routine_id", routine.id)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onBackPressed: () -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Routine) -> Unit
) {
    val context = LocalContext.current
    var routines by remember { mutableStateOf(RoutineManager.getRoutines()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showActivateDialog by remember { mutableStateOf<Routine?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                routines = RoutineManager.getRoutines()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        routines = RoutineManager.getRoutines()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.routines)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRoutine,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.create_routine)) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                InfoCard()
            }

            if (routines.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(routines, key = { it.id }) { routine ->
                    RoutineItem(
                        routine = routine,
                        onClick = {
                            if (routine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
                                showActivateDialog = routine
                            } else {
                                onEditRoutine(routine)
                            }
                        },
                        onToggle = { _ ->
                            RoutineManager.toggleRoutine(routine.id, context)
                            routines = RoutineManager.getRoutines()
                        }
                    )
                }
            }
        }
    }

    showActivateDialog?.let { routine ->
        AlertDialog(
            onDismissRequest = { showActivateDialog = null },
            title = { Text("Activate Routine") },
            text = { Text("Do you want to activate '${routine.name}' now?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        activateRoutineNow(routine, context)
                        showActivateDialog = null

                        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
                        val limitsText = when (routine.limits.size) {
                            0 -> "No app limits were applied"
                            1 -> "1 app limit has been applied"
                            else -> "${routine.limits.size} app limits have been applied"
                        }

                        kotlinx.coroutines.MainScope().launch {
                            snackbarHostState.showSnackbar(
                                "Routine '${routine.name}' activated! $limitsText",
                                duration = SnackbarDuration.Long
                            )
                        }
                    }
                ) {
                    Text("Activate")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showActivateDialog = null }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            showActivateDialog = null
                            onEditRoutine(routine)
                        }
                    ) {
                        Text("Edit")
                    }
                }
            }
        )
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.routine_card_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.routine_card_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = stringResource(R.string.no_routines),
                modifier = Modifier.padding(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_routines_yet),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.create_routine_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RoutineItem(
    routine: Routine,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.routine_icon),
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatSchedule(routine.schedule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = when (routine.limits.size) {
                                0 -> "No app limits set"
                                1 -> "1 app with limit"
                                else -> "${routine.limits.size} apps with limits"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    AnimatedVisibility(visible = routine.isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.active_status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = routine.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

private fun formatSchedule(schedule: RoutineSchedule?): String {
    if (schedule == null) return "Unknown schedule"

    return when (schedule.type) {
        RoutineSchedule.ScheduleType.DAILY -> {
            val timeRange = if (schedule.time != null && schedule.endTime != null) {
                " from ${formatTime(schedule.time!!)} to ${formatTime(schedule.endTime!!)}"
            } else if (schedule.time != null) {
                " at ${formatTime(schedule.time!!)}"
            } else ""
            "Daily$timeRange"
        }

        RoutineSchedule.ScheduleType.WEEKLY -> {
            val days = if (schedule.daysOfWeek.size == 7) {
                "Every day"
            } else if (schedule.daysOfWeek.containsAll(
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    )
                )
            ) {
                "Weekdays"
            } else if (schedule.daysOfWeek.containsAll(
                    listOf(
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY
                    )
                )
            ) {
                "Weekends"
            } else {
                schedule.daysOfWeek.sortedBy { it.value }
                    .joinToString(", ") {
                        it.getDisplayName(
                            TextStyle.SHORT,
                            Locale.getDefault()
                        )
                    }
            }
            val timeRange = if (schedule.time != null && schedule.endTime != null) {
                " from ${formatTime(schedule.time!!)} to ${formatTime(schedule.endTime!!)}"
            } else if (schedule.time != null) {
                " at ${formatTime(schedule.time!!)}"
            } else ""
            "$days$timeRange"
        }

        RoutineSchedule.ScheduleType.MANUAL -> "Manual activation"
    }
}

private fun formatTime(time: LocalTime): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return time.format(formatter)
}

private fun activateRoutineNow(routine: Routine, context: android.content.Context) {
    dev.pranav.reef.routine.RoutineExecutor.activateRoutine(context, routine)
}
