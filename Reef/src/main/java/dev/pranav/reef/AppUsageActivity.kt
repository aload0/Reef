package dev.pranav.reef

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import dev.pranav.reef.ui.ReefTheme
import dev.pranav.reef.util.applyDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nsh07.pomodoro.ui.statsScreen.TimeColumnChart
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// New enums for range/sort
enum class Range { TODAY, LAST_7_DAYS }
enum class Sort { TIME_DESC, NAME_ASC }

data class AppUsageStats(
    val applicationInfo: ApplicationInfo,
    val totalTime: Long
)

data class WeeklyUsageData(
    val dayOfWeek: String,
    val totalUsageHours: Float
)

class AppUsageViewModel : ViewModel() {
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

    var selectedRange by mutableStateOf(Range.TODAY)
        private set
    var selectedSort by mutableStateOf(Sort.TIME_DESC)
        private set

    fun setRange(range: Range) {
        selectedRange = range
        _isShowingAllApps.value = false
    }

    fun setSort(sort: Sort) {
        selectedSort = sort
        _isShowingAllApps.value = false
    }

    fun showAllApps() {
        _isShowingAllApps.value = true
    }

    fun selectDay(timestamp: Long?) {
        _selectedDayTimestamp.value = timestamp
        _isShowingAllApps.value = false
    }

    fun previousWeek() {
        _weekOffset.value -= 1
    }

    fun nextWeek() {
        if (_weekOffset.value < 0) _weekOffset.value += 1
    }

    fun loadUsageStats(
        usageStatsManager: UsageStatsManager,
        launcherApps: LauncherApps,
        packageManager: PackageManager,
        packageName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance()

                val (startTime, endTime) = when {
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

                    selectedRange == Range.TODAY -> {
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

                val rawUsage = queryAppUsageEvents(usageStatsManager, startTime, endTime)

                val mappedStats = rawUsage
                    .filter { it.value > 5 * 1000 && it.key != packageName }
                    .mapNotNull { (pkg, totalTime) ->
                        try {
                            launcherApps.getApplicationInfo(pkg, 0, Process.myUserHandle())
                                ?.let { info -> AppUsageStats(info, totalTime) }
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }

                val sortedStats = when (selectedSort) {
                    Sort.TIME_DESC -> mappedStats.sortedByDescending { it.totalTime }
                    Sort.NAME_ASC -> mappedStats.sortedBy {
                        it.applicationInfo.loadLabel(
                            packageManager
                        ).toString()
                    }
                }

                val weeklyUsageData = generateWeeklyData(usageStatsManager, _weekOffset.value)

                withContext(Dispatchers.Main) {
                    // Use max single-app usage for relative progress instead of sum for clearer UX
                    _totalUsage.value = sortedStats.sumOf { it.totalTime }
                    _appUsageStats.value = sortedStats
                    _weeklyData.value = weeklyUsageData
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    private fun generateWeeklyData(
        usageStatsManager: UsageStatsManager,
        offset: Int
    ): List<WeeklyUsageData> {
        val calendar = Calendar.getInstance()

        // 1. Calculate the start date based on the offset.
        // (offset * 14) moves the period's end date back.
        // - 13 then finds the start date of that period.
        val daysToSubtract = (offset * 14) + 6
        calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)


        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val result = mutableListOf<WeeklyUsageData>()

        // 2. Loop for 14 days from the calculated start date.
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
                usageStatsManager,
                startOfDay.timeInMillis,
                endOfDay.timeInMillis
            ).values.sum()

            result.add(
                WeeklyUsageData(
                    dayOfWeek = dayFormat.format(startOfDay.time),
                    totalUsageHours = totalUsage / (1000f * 60f * 60f)
                )
            )

            // Move to the next day
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun queryAppUsageEvents(
        usageStatsManager: UsageStatsManager,
        start: Long,
        end: Long
    ): Map<String, Long> {
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
        return usageMap
    }
}

class AppUsageActivity : ComponentActivity() {
    private val viewModel: AppUsageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps

        setContent {
            ReefTheme {
                AppUsageScreen(
                    viewModel = viewModel,
                    usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager,
                    launcherApps = launcherApps,
                    packageManager = packageManager,
                    packageName = packageName,
                    onBackPressed = { onBackPressedDispatcher.onBackPressed() },
                    onAppClick = { appUsageStats ->
                        val intent = Intent(this, ApplicationDailyLimitActivity::class.java).apply {
                            putExtra("package_name", appUsageStats.applicationInfo.packageName)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        viewModel.loadUsageStats(usageStatsManager, launcherApps, packageManager, packageName)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppUsageScreen(
    viewModel: AppUsageViewModel,
    usageStatsManager: UsageStatsManager,
    launcherApps: LauncherApps,
    packageManager: PackageManager,
    packageName: String,
    onBackPressed: () -> Unit,
    onAppClick: (AppUsageStats) -> Unit
) {
    val appUsageStats by viewModel.appUsageStats
    val weeklyData by viewModel.weeklyData
    val maxUsage by viewModel.totalUsage
    val isLoading by viewModel.isLoading
    val range = viewModel.selectedRange
    val sort = viewModel.selectedSort
    val isShowingAllApps by viewModel.isShowingAllApps
    val selectedDayTimestamp by viewModel.selectedDayTimestamp
    val weekOffset by viewModel.weekOffset

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // local state for sort menu
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(range, sort, selectedDayTimestamp, weekOffset) {
        viewModel.loadUsageStats(usageStatsManager, launcherApps, packageManager, packageName)
    }

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
                            // plural-safe subtitle
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                // Add sort action to top app bar (compact, discoverable)
                actions = {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(imageVector = Icons.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by time") },
                            onClick = {
                                viewModel.setSort(Sort.TIME_DESC)
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort A–Z") },
                            onClick = {
                                viewModel.setSort(Sort.NAME_ASC)
                                sortMenuExpanded = false
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Crossfade(targetState = isLoading, label = "loadingCrossfade") { loading ->
            if (loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // replace ambiguous LoadingIndicator with standard CircularProgressIndicator
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Fetching usage data...")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Place lightweight range chips directly below the top app bar (no card)
                    item {
                        RangeChips(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            range = range,
                            selectedDayTimestamp = selectedDayTimestamp,
                            onRangeChange = { viewModel.setRange(it) },
                            onClearSelection = { viewModel.selectDay(null) }
                        )
                    }

                    item {
                        HeroHeader(
                            totalTime = appUsageStats.sumOf { it.totalTime },
                            range = range,
                            weeklyData = weeklyData,
                            onPrevWeek = { viewModel.previousWeek() },
                            onNextWeek = { viewModel.nextWeek() },
                            canGoNext = weekOffset < 0
                        )
                    }

                    val displayedAppStats =
                        if (isShowingAllApps) appUsageStats else appUsageStats.take(30)

                    itemsIndexed(
                        items = displayedAppStats,
                        key = { _, stats -> stats.applicationInfo.packageName }
                    ) { index, stats ->
                        AppUsageItem(
                            appUsageStats = stats,
                            maxUsage = maxUsage,
                            onClick = { onAppClick(stats) },
                            index = index,
                            listSize = displayedAppStats.size
                        )
                    }

                    if (!isShowingAllApps && appUsageStats.size > 30) {
                        item {
                            AnimatedVisibility(visible = true) {
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
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroHeader(
    totalTime: Long,
    range: Range,
    weeklyData: List<WeeklyUsageData>,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    canGoNext: Boolean
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    rememberVicoScrollState()
    rememberVicoZoomState()

    LaunchedEffect(weeklyData) {
        modelProducer.runTransaction {
            columnSeries {
                series(
                    weeklyData.map { it.totalUsageHours.toLong() },
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        Text(
            text = when (range) {
                Range.TODAY -> "Today"
                Range.LAST_7_DAYS -> "Last 7 days"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))

        // Total time with AnimatedContent
        AnimatedContent(
            targetState = totalTime,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "totalTime"
        ) { time ->
            Text(
                text = formatTime(time),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(16.dp))

        // Weekly chart or loading
        if (weeklyData.isNotEmpty()) {
            TimeColumnChart(
                modelProducer,
                modifier = Modifier.padding(horizontal = 16.dp),
                yValueFormatter = CartesianValueFormatter { _, value, _ ->
                    value.toTimeString()
                },
                xValueFormatter = CartesianValueFormatter { _, value, _ ->
                    // Format to first three letters of day
                    val index = value.toInt()
                    if (index in weeklyData.indices) weeklyData[index].dayOfWeek.take(3) else ""
                },
            )
//                WeeklyUsageChart(
//                    weeklyData = weeklyData,
//                    modifier = Modifier.fillMaxWidth(),
//                    onDaySelected = onDaySelected
//                )
        } else {
            // Loading skeleton
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .animateContentSize(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            IconButton(onClick = onPrevWeek) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Previous Week",
                    tint = if (weeklyData.sumOf { it.totalUsageHours.toLong() } > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            }

            IconButton(onClick = onNextWeek) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Next Week",
                    tint = if (canGoNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

fun Double.toTimeString(): String {
    val hours = this.toInt()
    val minutes = ((this - hours) * 60).toInt()

    return if (hours > 0) {
        "${hours}h" + if (minutes > 0) " ${minutes}m" else ""
    } else {
        if (minutes > 0) "${minutes}m" else "0"
    }
}

@Composable
private fun RangeChips(
    modifier: Modifier = Modifier,
    range: Range,
    selectedDayTimestamp: Long?,
    onRangeChange: (Range) -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        modifier = modifier, // parent decides sizing
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Range",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilterChip(
            selected = range == Range.TODAY && selectedDayTimestamp == null,
            onClick = {
                onClearSelection()
                onRangeChange(Range.TODAY)
            },
            label = { Text("Today") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        )
        FilterChip(
            selected = range == Range.LAST_7_DAYS && selectedDayTimestamp == null,
            onClick = {
                onClearSelection()
                onRangeChange(Range.LAST_7_DAYS)
            },
            label = { Text("7 days") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        )
        if (selectedDayTimestamp != null) {
            FilledTonalButton(onClick = onClearSelection) {
                Text("Clear")
            }
        }
    }
}


@Composable
private fun UsageIconRing(icon: ImageBitmap, progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f))
    val ringColor = when {
        animatedProgress > 0.7f -> MaterialTheme.colorScheme.error
        animatedProgress > 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(54.dp),
            color = ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 3.dp
        )
        Image(
            bitmap = icon,
            contentDescription = "App icon",
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
        )
    }
}


@Composable
fun AppUsageItem(
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
        Column {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = shape
            ) {
                ListItem(
                    modifier = Modifier
                        .clickable { onClick() },
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
}


private fun formatTime(timeInMillis: Long): String {
    val hours = timeInMillis / 3_600_000
    val minutes = (timeInMillis % 3_600_000) / 60_000

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
