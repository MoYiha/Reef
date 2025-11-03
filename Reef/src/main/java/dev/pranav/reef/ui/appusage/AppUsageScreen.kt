package dev.pranav.reef.ui.appusage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import org.nsh07.pomodoro.ui.statsScreen.TimeColumnChart


private fun formatTime(timeInMillis: Long): String {
    val hours = timeInMillis / 3_600_000
    val minutes = (timeInMillis % 3_600_000) / 60_000
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

private fun Double.toTimeString(): String {
    val hours = this.toInt()
    val minutes = ((this - hours) * 60).toInt()
    return if (hours > 0) {
        "${hours}h" + if (minutes > 0) " ${minutes}m" else ""
    } else {
        if (minutes > 0) "${minutes}m" else "0"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppUsageScreen(
    viewModel: AppUsageViewModel,
    onBackPressed: () -> Unit,
    onAppClick: (AppUsageStats) -> Unit
) {
    val appUsageStats by viewModel.appUsageStats
    val weeklyData by viewModel.weeklyData
    val maxUsage by viewModel.totalUsage
    val isLoading by viewModel.isLoading
    val range = viewModel.selectedRange
    val isShowingAllApps by viewModel.isShowingAllApps
    val selectedDayTimestamp by viewModel.selectedDayTimestamp
    val selectedDayIndex by viewModel.selectedDayIndex
    val weekOffset by viewModel.weekOffset
    val canGoPrevious by viewModel.canGoPrevious

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "App Usage",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        AnimatedVisibility(!isLoading) {
                            val count = appUsageStats.size
                            Text(
                                "${count} app${if (count == 1) "" else "s"} tracked",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by time") },
                            onClick = {
                                viewModel.setSort(UsageSortOrder.TIME_DESC)
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort A–Z") },
                            onClick = {
                                viewModel.setSort(UsageSortOrder.NAME_ASC)
                                sortMenuExpanded = false
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Crossfade(targetState = isLoading, label = "loading") { loading ->
            if (loading) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContainedLoadingIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Fetching usage data...")
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        RangeChips(
                            range, selectedDayTimestamp,
                            onRangeChange = { viewModel.setRange(it) },
                            onClearSelection = { viewModel.clearDaySelection() }
                        )
                    }

                    item {
                        HeroHeader(
                            totalTime = appUsageStats.sumOf { it.totalTime },
                            range = range,
                            weeklyData = weeklyData,
                            selectedDayIndex = selectedDayIndex,
                            onPrevWeek = { viewModel.previousWeek() },
                            onNextWeek = { viewModel.nextWeek() },
                            canGoNext = weekOffset < 0,
                            canGoPrevious = canGoPrevious,
                            onDaySelected = { index ->
                                viewModel.selectDayByIndex(
                                    index,
                                    weeklyData
                                )
                            },
                            onClearSelection = { viewModel.clearDaySelection() }
                        )
                    }

                    val displayedAppStats =
                        if (isShowingAllApps) appUsageStats else appUsageStats.take(30)

                    itemsIndexed(
                        items = displayedAppStats,
                        key = { _, stats -> stats.applicationInfo.packageName }
                    ) { index, stats ->
                        AppUsageItem(
                            stats,
                            maxUsage,
                            { onAppClick(stats) },
                            index,
                            displayedAppStats.size
                        )
                    }

                    if (!isShowingAllApps && appUsageStats.size > 30) {
                        item {
                            FilledTonalButton(
                                onClick = { viewModel.showAllApps() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                Text("Show all ${appUsageStats.size} apps")
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RangeChips(
    range: UsageRange,
    selectedDayTimestamp: Long?,
    onRangeChange: (UsageRange) -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Range",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilterChip(
            selected = range == UsageRange.TODAY && selectedDayTimestamp == null,
            onClick = { onClearSelection(); onRangeChange(UsageRange.TODAY) },
            label = { Text("Today") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer)
        )
        FilterChip(
            selected = range == UsageRange.LAST_7_DAYS && selectedDayTimestamp == null,
            onClick = { onClearSelection(); onRangeChange(UsageRange.LAST_7_DAYS) },
            label = { Text("7 days") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer)
        )
        if (selectedDayTimestamp != null) {
            FilledTonalButton(onClick = onClearSelection) { Text("Clear") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroHeader(
    totalTime: Long,
    range: UsageRange,
    weeklyData: List<WeeklyUsageData>,
    selectedDayIndex: Int?,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    canGoNext: Boolean,
    canGoPrevious: Boolean,
    onDaySelected: (Int) -> Unit,
    onClearSelection: () -> Unit
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(weeklyData) {
        modelProducer.runTransaction {
            // Convert hours to minutes for better precision in the chart
            columnSeries { series(weeklyData.map { (it.totalUsageHours * 60).toLong() }) }
        }
    }

    Column(
        Modifier.animateContentSize(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
        Text(
            text = when {
                selectedDayIndex != null && selectedDayIndex in weeklyData.indices ->
                    weeklyData[selectedDayIndex].dayOfWeek

                range == UsageRange.TODAY -> "Today"
                range == UsageRange.LAST_7_DAYS -> "Last 7 days"
                else -> "Today"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = totalTime,
            transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
            label = "totalTime"
        ) { time ->
            Text(
                formatTime(time),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(16.dp))

        if (weeklyData.isNotEmpty()) {
            TimeColumnChart(
                modelProducer,
                Modifier.padding(horizontal = 16.dp),
                yValueFormatter = CartesianValueFormatter { _, value, _ ->
                    // Value is now in minutes, convert to time string
                    val totalMinutes = value.toInt()
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    when {
                        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                        hours > 0 -> "${hours}h"
                        minutes > 0 -> "${minutes}m"
                        else -> "0"
                    }
                },
                xValueFormatter = CartesianValueFormatter { _, value, _ ->
                    val index = value.toInt()
                    if (index in weeklyData.indices) weeklyData[index].dayOfWeek.take(3) else ""
                },
                onColumnClick = { index ->
                    if (selectedDayIndex == index) {
                        onClearSelection()
                    } else {
                        onDaySelected(index)
                    }
                },
                dataValues = weeklyData.map { it.totalUsageHours * 60 }, // Convert to minutes
                selectedColumnIndex = selectedDayIndex
            )
        } else {
            ElevatedCard(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { LoadingIndicator() }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(
                onClick = onPrevWeek,
                enabled = canGoPrevious
            ) {
                Icon(
                    Icons.Filled.ChevronLeft, "Previous Week",
                    tint = if (canGoPrevious)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            }
            IconButton(
                onClick = onNextWeek,
                enabled = canGoNext
            ) {
                Icon(
                    Icons.Filled.ChevronRight, "Next Week",
                    tint = if (canGoNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AppUsageItem(
    appUsageStats: AppUsageStats,
    maxUsage: Long,
    onClick: () -> Unit,
    index: Int,
    listSize: Int
) {
    val rawProgress =
        if (maxUsage <= 0L) 0f else (appUsageStats.totalTime / maxUsage.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = rawProgress)
    val context = LocalContext.current
    val pm = context.packageManager
    val icon = remember(appUsageStats.applicationInfo.packageName) {
        try {
            appUsageStats.applicationInfo.loadIcon(pm).toBitmap().asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    val label = remember(appUsageStats.applicationInfo.packageName) {
        appUsageStats.applicationInfo.loadLabel(pm).toString()
    }

    val shape = when {
        listSize == 1 -> RoundedCornerShape(20.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )

        index == listSize - 1 -> RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )

        else -> RoundedCornerShape(6.dp)
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = shape
        ) {
            ListItem(
                modifier = Modifier.clickable { onClick() },
                headlineContent = {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                supportingContent = {
                    Text(
                        formatTime(appUsageStats.totalTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                leadingContent = { icon?.let { UsageIconRing(it, animatedProgress) } },
                trailingContent = {
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun UsageIconRing(icon: androidx.compose.ui.graphics.ImageBitmap, progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f))
    val ringColor = when {
        animatedProgress > 0.7f -> MaterialTheme.colorScheme.error
        animatedProgress > 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(54.dp),
            color = ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 3.dp
        )
        Image(icon, "App icon", Modifier
            .size(46.dp)
            .clip(CircleShape))
    }
}
