package dev.pranav.reef.ui.appusage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AppUsageStats(
    val applicationInfo: ApplicationInfo,
    val totalTime: Long
)

data class WeeklyUsageData(
    val dayOfWeek: String,
    val totalUsageHours: Float,
    val timestamp: Long = 0L
)

enum class UsageRange { TODAY, LAST_7_DAYS }
enum class UsageSortOrder { TIME_DESC, NAME_ASC }

class AppUsageViewModel(
    private val usageStatsManager: UsageStatsManager,
    private val launcherApps: LauncherApps,
    private val packageManager: PackageManager,
    private val packageName: String
) : ViewModel() {

    private val _appUsageStats = mutableStateOf<List<AppUsageStats>>(emptyList())
    val appUsageStats: State<List<AppUsageStats>> = _appUsageStats

    private val _weeklyData = mutableStateOf<List<WeeklyUsageData>>(emptyList())
    val weeklyData: State<List<WeeklyUsageData>> = _weeklyData

    private val _totalUsage = mutableStateOf(1L)
    val totalUsage: State<Long> = _totalUsage

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _isShowingAllApps = mutableStateOf(false)
    val isShowingAllApps: State<Boolean> = _isShowingAllApps

    private val _selectedDayTimestamp = mutableStateOf<Long?>(null)
    val selectedDayTimestamp: State<Long?> = _selectedDayTimestamp

    private val _weekOffset = mutableStateOf(0)
    val weekOffset: State<Int> = _weekOffset

    private val _selectedDayIndex = mutableStateOf<Int?>(null)
    val selectedDayIndex: State<Int?> = _selectedDayIndex

    private val _canGoPrevious = mutableStateOf(true)
    val canGoPrevious: State<Boolean> = _canGoPrevious

    var selectedRange by mutableStateOf(UsageRange.TODAY)
        private set

    var selectedSort by mutableStateOf(UsageSortOrder.TIME_DESC)
        private set

    // Cache raw data to avoid reloading
    private var rawAppUsageMap: Map<String, Long> = emptyMap()
    private var allAppStats: List<AppUsageStats> = emptyList()

    init {
        loadInitialData()
    }

    fun setRange(range: UsageRange) {
        selectedRange = range
        _isShowingAllApps.value = false
        _selectedDayIndex.value = null
        filterAndSortData()
    }

    fun setSort(sort: UsageSortOrder) {
        selectedSort = sort
        _isShowingAllApps.value = false
        filterAndSortData()
    }

    fun showAllApps() {
        _isShowingAllApps.value = true
    }

    fun selectDay(timestamp: Long?) {
        _selectedDayTimestamp.value = timestamp
        _isShowingAllApps.value = false
        filterAndSortData()
    }

    fun selectDayByIndex(index: Int, weeklyData: List<WeeklyUsageData>) {
        if (index in weeklyData.indices) {
            _selectedDayIndex.value = index
            _selectedDayTimestamp.value = weeklyData[index].timestamp
            _isShowingAllApps.value = false
            filterAndSortData()
        }
    }

    fun clearDaySelection() {
        _selectedDayIndex.value = null
        _selectedDayTimestamp.value = null
        filterAndSortData()
    }

    fun previousWeek() {
        _weekOffset.value -= 1
        loadWeekData()
    }

    fun nextWeek() {
        if (_weekOffset.value < 0) {
            _weekOffset.value += 1
            loadWeekData()
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true

                // Load last 7 days of data initially
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startTime = cal.timeInMillis
                val endTime = System.currentTimeMillis()

                rawAppUsageMap = queryAppUsageEvents(startTime, endTime)
                allAppStats = rawAppUsageMap
                    .filter { it.value > 5 * 1000 && it.key != packageName }
                    .mapNotNull { (pkg, totalTime) ->
                        try {
                            launcherApps.getApplicationInfo(pkg, 0, Process.myUserHandle())
                                ?.let { info -> AppUsageStats(info, totalTime) }
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }

                loadWeekData()
                filterAndSortData()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    private fun loadWeekData() {
        viewModelScope.launch(Dispatchers.IO) {
            val weeklyData = generateWeeklyData()
            withContext(Dispatchers.Main) {
                _weeklyData.value = weeklyData
            }
        }
    }

    private fun filterAndSortData() {
        viewModelScope.launch(Dispatchers.IO) {
            val (startTime, endTime) = calculateTimeRange()

            // Filter data based on time range
            val filteredStats = if (_selectedDayTimestamp.value != null) {
                // For specific day, re-query to get accurate data
                val dayUsage = queryAppUsageEvents(startTime, endTime)
                dayUsage
                    .filter { it.value > 5 * 1000 && it.key != packageName }
                    .mapNotNull { (pkg, totalTime) ->
                        try {
                            launcherApps.getApplicationInfo(pkg, 0, Process.myUserHandle())
                                ?.let { info -> AppUsageStats(info, totalTime) }
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
            } else if (selectedRange == UsageRange.TODAY) {
                // Filter from cached data for today
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val todayUsage = queryAppUsageEvents(todayStart, System.currentTimeMillis())
                todayUsage
                    .filter { it.value > 5 * 1000 && it.key != packageName }
                    .mapNotNull { (pkg, totalTime) ->
                        try {
                            launcherApps.getApplicationInfo(pkg, 0, Process.myUserHandle())
                                ?.let { info -> AppUsageStats(info, totalTime) }
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
            } else {
                // Use cached data for last 7 days
                allAppStats
            }

            // Sort data
            val sortedStats = when (selectedSort) {
                UsageSortOrder.TIME_DESC -> filteredStats.sortedByDescending { it.totalTime }
                UsageSortOrder.NAME_ASC -> filteredStats.sortedBy {
                    it.applicationInfo.loadLabel(packageManager).toString()
                }
            }

            withContext(Dispatchers.Main) {
                _totalUsage.value = sortedStats.sumOf { it.totalTime }.coerceAtLeast(1L)
                _appUsageStats.value = sortedStats
            }
        }
    }

    private fun calculateTimeRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        return when {
            _selectedDayTimestamp.value != null -> {
                cal.timeInMillis = _selectedDayTimestamp.value!!
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                start to cal.timeInMillis
            }

            selectedRange == UsageRange.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }

            else -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
        }
    }

    private fun generateWeeklyData(): List<WeeklyUsageData> {
        val calendar = Calendar.getInstance()
        val daysToSubtract = (_weekOffset.value * 7) + 6
        calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)

        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val result = mutableListOf<WeeklyUsageData>()
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (i in 0 until 7) {
            val startOfDay = calendar.clone() as Calendar
            startOfDay.set(Calendar.HOUR_OF_DAY, 0)
            startOfDay.set(Calendar.MINUTE, 0)
            startOfDay.set(Calendar.SECOND, 0)
            startOfDay.set(Calendar.MILLISECOND, 0)
            val dayStartMillis = startOfDay.timeInMillis

            // For today, use current time as end; for past days, use end of day
            val endOfDay = if (dayStartMillis >= todayStart) {
                // This is today - use current time
                now
            } else {
                // This is a past day - use end of day
                val end = startOfDay.clone() as Calendar
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
                end.timeInMillis
            }

            val totalUsage = queryAppUsageEvents(
                dayStartMillis,
                endOfDay
            ).values.sum()

            result.add(
                WeeklyUsageData(
                    dayOfWeek = dayFormat.format(startOfDay.time),
                    totalUsageHours = totalUsage / (1000f * 60f * 60f),
                    timestamp = dayStartMillis
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Check if the NEXT previous week (the one we'd navigate to) has any data
        checkPreviousWeekData()

        return result
    }

    private fun checkPreviousWeekData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Calculate the start of the previous week (one week before current displayed week)
            val calendar = Calendar.getInstance()
            val daysToSubtract = ((_weekOffset.value - 1) * 7) + 6
            calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)

            // Check if we're going too far back (more than 90 days)
            val maxWeeksBack = 13 // ~3 months
            if (_weekOffset.value <= -maxWeeksBack) {
                withContext(Dispatchers.Main) {
                    _canGoPrevious.value = false
                }
                return@launch
            }

            // Check if any day in the previous week has data
            var hasData = false
            for (i in 0 until 7) {
                val startOfDay = calendar.clone() as Calendar
                startOfDay.set(Calendar.HOUR_OF_DAY, 0)
                startOfDay.set(Calendar.MINUTE, 0)
                startOfDay.set(Calendar.SECOND, 0)
                startOfDay.set(Calendar.MILLISECOND, 0)

                val endOfDay = startOfDay.clone() as Calendar
                endOfDay.set(Calendar.HOUR_OF_DAY, 23)
                endOfDay.set(Calendar.MINUTE, 59)
                endOfDay.set(Calendar.SECOND, 59)
                endOfDay.set(Calendar.MILLISECOND, 999)

                val totalUsage = queryAppUsageEvents(
                    startOfDay.timeInMillis,
                    endOfDay.timeInMillis
                ).values.sum()

                if (totalUsage > 0) {
                    hasData = true
                    break
                }

                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            withContext(Dispatchers.Main) {
                _canGoPrevious.value = hasData
            }
        }
    }

    private fun queryAppUsageEvents(start: Long, end: Long): Map<String, Long> {
        val events = usageStatsManager.queryEvents(start, end)
        val usageMap = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()
        val lastResumeTimes = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTimes[event.packageName] = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val startTime = lastResumeTimes.remove(event.packageName)
                    if (startTime != null) {
                        val duration = event.timeStamp - startTime
                        usageMap[event.packageName] =
                            (usageMap[event.packageName] ?: 0L) + duration
                    }
                }
            }
        }

        lastResumeTimes.forEach { (pkg, startTime) ->
            val duration = end - startTime
            usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
        }

        return usageMap
    }
}
