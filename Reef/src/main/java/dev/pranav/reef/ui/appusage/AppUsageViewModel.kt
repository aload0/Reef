package dev.pranav.reef.ui.appusage

import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pranav.reef.util.ScreenUsageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Stable
data class AppUsageStats(
    val applicationInfo: ApplicationInfo,
    val label: String,
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
    private val context: android.content.Context,
    private val usageStatsManager: UsageStatsManager,
    private val launcherApps: LauncherApps,
    private val packageManager: PackageManager,
    private val packageName: String
): ViewModel() {

    private val _appUsageStats = mutableStateOf<List<AppUsageStats>>(emptyList())
    val appUsageStats: State<List<AppUsageStats>> = _appUsageStats

    private val _weeklyData = mutableStateOf<List<WeeklyUsageData>>(emptyList())
    val weeklyData: State<List<WeeklyUsageData>> = _weeklyData

    private val _totalUsage = mutableLongStateOf(1L) // Optimization: primitive state
    val totalUsage: State<Long> = _totalUsage

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _isShowingAllApps = mutableStateOf(false)
    val isShowingAllApps: State<Boolean> = _isShowingAllApps

    private val _selectedDayTimestamp = mutableStateOf<Long?>(null)

    private val _weekOffset = mutableIntStateOf(0) // Optimization: primitive state
    val weekOffset: State<Int> = _weekOffset

    private val _selectedDayIndex = mutableStateOf<Int?>(null)
    val selectedDayIndex: State<Int?> = _selectedDayIndex

    private val _canGoPrevious = mutableStateOf(true)
    val canGoPrevious: State<Boolean> = _canGoPrevious

    var selectedRange by mutableStateOf(UsageRange.TODAY)
        private set

    var selectedSort by mutableStateOf(UsageSortOrder.TIME_DESC)
        private set

    private var allAppStats: List<AppUsageStats> = emptyList()
    private var isInitialized = false

    init {
        viewModelScope.launch {
            if (!isInitialized) {
                _isLoading.value = true
                loadInitialData()
                isInitialized = true
            }
        }
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
        _weekOffset.intValue -= 1
        loadWeekData()
    }

    fun nextWeek() {
        if (_weekOffset.intValue < 0) {
            _weekOffset.intValue += 1
            loadWeekData()
        }
    }

    private suspend fun loadInitialData() {
        withContext(Dispatchers.IO) {
            try {
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startTime = cal.timeInMillis
                val endTime = System.currentTimeMillis()

                val rawMap =
                    ScreenUsageHelper.calculateUsage(context, usageStatsManager, startTime, endTime)
                allAppStats = processUsageMap(rawMap)

                loadWeekData()
                filterAndSortData()
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
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

            val stats =
                if (_selectedDayTimestamp.value != null || selectedRange == UsageRange.TODAY) {
                    processUsageMap(
                        ScreenUsageHelper.calculateUsage(
                            context, usageStatsManager,
                            startTime,
                            endTime
                        )
                    )
                } else {
                    allAppStats
                }

            val sortedStats = when (selectedSort) {
                UsageSortOrder.TIME_DESC -> stats.sortedByDescending { it.totalTime }
                UsageSortOrder.NAME_ASC -> stats.sortedBy { it.label }
            }

            withContext(Dispatchers.Main) {
                _totalUsage.longValue = sortedStats.sumOf { it.totalTime }.coerceAtLeast(1L)
                _appUsageStats.value = sortedStats
            }
        }
    }

    private fun processUsageMap(usageMap: Map<String, Long>): List<AppUsageStats> {
        return usageMap
            .filter { it.value > 5000 && it.key != packageName }
            .mapNotNull { (pkg, totalTime) ->
                try {
                    if (packageManager.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null

                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(
                            pkg,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        packageManager.getApplicationInfo(pkg, 0)
                    }
                    val label = packageManager.getApplicationLabel(info).toString()

                    launcherApps.getApplicationInfo(
                        pkg,
                        0, Process.myUserHandle()
                    )?.let { info ->
                        AppUsageStats(
                            applicationInfo = info,
                            label = label,
                            totalTime = totalTime
                        )
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                    null
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
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val result = mutableListOf<WeeklyUsageData>()

        val now = System.currentTimeMillis()

        repeat(7) { i ->
            val cal = Calendar.getInstance()
            val daysAgo = (_weekOffset.intValue - 1) * 7 + i + 1
            cal.add(Calendar.DAY_OF_YEAR, daysAgo)

            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val startMillis = cal.timeInMillis
            val dayLabel = dayFormat.format(cal.time)

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)

            val endMillis = minOf(cal.timeInMillis, now)

            val totalUsageMs = if (endMillis > startMillis) {
                ScreenUsageHelper.calculateUsage(
                    context, usageStatsManager,
                    startMillis,
                    endMillis
                ).filter { it.key != packageName }.values.sum()
            } else 0L

            result.add(
                WeeklyUsageData(
                    dayOfWeek = dayLabel,
                    totalUsageHours = totalUsageMs / 3600000f,
                    timestamp = startMillis
                )
            )
        }
        checkPreviousWeekData()
        return result
    }

    private fun checkPreviousWeekData() {
        viewModelScope.launch(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            val daysToSubtract = (_weekOffset.intValue - 1) * 7
            calendar.add(Calendar.DAY_OF_YEAR, daysToSubtract)

            val maxWeeksBack = 13
            if (_weekOffset.intValue <= -maxWeeksBack) {
                withContext(Dispatchers.Main) { _canGoPrevious.value = false }
                return@launch
            }

            var hasData = false
            for (i in 0 until 7) {
                val start = calendar.clone() as Calendar
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND,0)
                start.set(Calendar.MILLISECOND, 0)

                val end = start.clone() as Calendar
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE,59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)

                if (ScreenUsageHelper.calculateUsage(
                        context, usageStatsManager,
                        start.timeInMillis,
                        end.timeInMillis
                    ).values.any { it > 0}
                ) {
                    hasData = true
                    break
                }
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

            withContext(Dispatchers.Main) { _canGoPrevious.value = hasData }
        }
    }
}
