package dev.pranav.reef

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.intro.AppIntroActivity
import dev.pranav.reef.navigation.Screen
import dev.pranav.reef.screens.*
import dev.pranav.reef.timer.TimerConfig
import dev.pranav.reef.timer.TimerContent
import dev.pranav.reef.timer.TimerStateManager
import dev.pranav.reef.ui.ReefTheme
import dev.pranav.reef.util.*

class MainActivity: ComponentActivity() {
    private var pendingFocusModeStart = false
    private var hasCheckedPermissions = false
    private var shouldNavigateToTimer = false

    private val timerReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val left = intent.getStringExtra(FocusModeService.EXTRA_TIME_LEFT) ?: "00:00"
            currentTimeLeft = left
            currentTimerState = intent.getStringExtra(FocusModeService.EXTRA_TIMER_STATE) ?: "FOCUS"

            if (left == "00:00" && !prefs.getBoolean("pomodoro_mode", false)) {
                AndroidUtilities.vibrate(context, 500)
            }
        }
    }

    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    android.net.Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let { prefs.edit { putString("pomodoro_sound", it.toString()) } }
        }
    }

    private var currentTimeLeft by mutableStateOf("00:00")
    private var currentTimerState by mutableStateOf("FOCUS")

    private val usageStatsManager by lazy { getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager }
    private val launcherApps by lazy { getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDefaults()
        addExceptions()

        shouldNavigateToTimer = intent?.getBooleanExtra("navigate_to_timer", false) == true

        val shouldNavigateToRoutines =
            intent?.getBooleanExtra("navigate_to_routines", false) == true

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

        setContent {
            val navController = rememberNavController()
            val timerState by TimerStateManager.state.collectAsState()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            var slideProgress by remember { mutableFloatStateOf(0f) }

            val whitelistedCount = remember {
                Whitelist.getWhitelistedLaunchableCount(launcherApps)
            }

            val selectedNavIndex = remember(currentDestination) {
                when {
                    currentDestination?.hasRoute<Screen.Home>() == true -> 0
                    currentDestination?.hasRoute<Screen.Usage>() == true -> 1
                    currentDestination?.hasRoute<Screen.Timer>() == true -> 2
                    currentDestination?.hasRoute<Screen.Settings>() == true -> 3
                    else -> 0
                }
            }

            val showBottomBar = remember(currentDestination) {
                currentDestination?.hasRoute<Screen.Home>() == true ||
                        currentDestination?.hasRoute<Screen.Usage>() == true ||
                        currentDestination?.hasRoute<Screen.Timer>() == true ||
                        currentDestination?.hasRoute<Screen.Settings>() == true ||
                        currentDestination?.hasRoute<Screen.Whitelist>() == true ||
                        currentDestination?.hasRoute<Screen.Routines>() == true
            }

            LaunchedEffect(Unit) {
                if (timerState.isRunning || timerState.isPaused) {
                    currentTimeLeft = "00:00"
                    currentTimerState = timerState.pomodoroPhase.name
                }
            }

            LaunchedEffect(shouldNavigateToTimer, shouldNavigateToRoutines) {
                if (shouldNavigateToTimer) {
                    navController.navigate(Screen.Timer) {
                        launchSingleTop = true
                    }
                    this@MainActivity.shouldNavigateToTimer = false
                } else if (shouldNavigateToRoutines) {
                    navController.navigate(Screen.Routines) {
                        launchSingleTop = true
                    }
                }
            }

            var dailyUsageText by remember { mutableStateOf("0m today") }

            val homeScreen = remember {
                movableContentOf {
                    HomeContent(
                        onNavigateToTimer = { navController.navigate(Screen.Timer) },
                        onNavigateToUsage = { navController.navigate(Screen.Usage) },
                        onNavigateToRoutines = { navController.navigate(Screen.Routines) },
                        onNavigateToWhitelist = { navController.navigate(Screen.Whitelist) },
                        onNavigateToIntro = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    AppIntroActivity::class.java
                                )
                            )
                        },
                        onRequestAccessibility = {
                            pendingFocusModeStart = true
                            showAccessibilityDialog()
                        },
                        onSlideProgressChange = { progress ->
                            slideProgress = progress
                        },
                        currentTimeLeft = currentTimeLeft,
                        currentTimerState = currentTimerState,
                        whitelistedAppsCount = whitelistedCount,
                        dailyUsageText = dailyUsageText
                    )
                }
            }

            val timerScreen = remember {
                movableContentOf {
                    TimerContent(
                        isTimerRunning = timerState.isRunning,
                        isPaused = timerState.isPaused,
                        currentTimeLeft = currentTimeLeft,
                        currentTimerState = currentTimerState,
                        isStrictMode = timerState.isStrictMode,
                        onStartTimer = { config -> startFocusMode(config) },
                        onPauseTimer = { pauseFocusMode() },
                        onResumeTimer = { resumeFocusMode() },
                        onCancelTimer = { cancelFocusMode() },
                        onRestartTimer = { restartFocusMode() }
                    )
                }
            }

            val usageScreen = remember {
                movableContentOf {
                    UsageScreenWrapper(
                        context = this@MainActivity,
                        usageStatsManager = usageStatsManager,
                        launcherApps = launcherApps,
                        packageManager = packageManager,
                        currentPackageName = packageName,
                        onBackPressed = { navController.popBackStack() },
                        onAppClick = { appUsageStats ->
                            navController.navigate(
                                Screen.DailyLimit(
                                    appUsageStats.applicationInfo.packageName
                                )
                            )
                        }
                    )
                }
            }

            val settingsScreen = remember {
                movableContentOf {
                    SettingsContent(
                        onSoundPicker = { launchSoundPicker() }
                    )
                }
            }

            ReefTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val todayUsage =
                                ScreenUsageHelper.fetchAppUsageTodayTillNow(usageStatsManager)
                            val totalUsageSeconds = todayUsage.values.sum()
                            val totalUsageMinutes = totalUsageSeconds / 60

                            val hours = totalUsageMinutes / 60
                            val minutes = totalUsageMinutes % 60

                            val usageText = when {
                                hours > 0 && minutes > 0 -> getString(
                                    R.string.hour_min_short_suffix,
                                    hours,
                                    minutes
                                ) + " " + getString(R.string.today)

                                hours > 0 -> getString(
                                    R.string.hours_short_format,
                                    hours
                                ) + " " + getString(R.string.today)

                                minutes > 0 -> getString(
                                    R.string.minutes_short_format,
                                    minutes
                                ) + " " + getString(R.string.today)

                                else -> getString(R.string.less_than_one_minute)
                            }

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                dailyUsageText = usageText
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showBottomBar,
                                enter = fadeIn() + slideInVertically { it },
                                exit = fadeOut() + slideOutVertically { it }
                            ) {
                                ReefBottomNavBar(
                                    selectedItem = selectedNavIndex,
                                    onItemSelected = { index ->
                                        val options = androidx.navigation.navOptions {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }

                                        when (index) {
                                            0 -> {
                                                if (selectedNavIndex != 0) {
                                                    navController.navigate(Screen.Home, options)
                                                }
                                            }

                                            1 -> {
                                                if (selectedNavIndex != 1) {
                                                    navController.navigate(Screen.Usage, options)
                                                }
                                            }

                                            2 -> {
                                                if (selectedNavIndex != 2) {
                                                    navController.navigate(Screen.Timer, options)
                                                }
                                            }

                                            3 -> {
                                                if (selectedNavIndex != 3) {
                                                    navController.navigate(Screen.Settings, options)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home,
                            modifier = Modifier.padding(paddingValues),
                            enterTransition = {
                                fadeIn(animationSpec = tween(250)) +
                                        slideInHorizontally(
                                            initialOffsetX = { it / 8 },
                                            animationSpec = tween(250)
                                        )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(250)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 8 },
                                            animationSpec = tween(250)
                                        )
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(250)) +
                                        slideInHorizontally(
                                            initialOffsetX = { -it / 8 },
                                            animationSpec = tween(250)
                                        )
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(250)) +
                                        slideOutHorizontally(
                                            targetOffsetX = { it / 8 },
                                            animationSpec = tween(250)
                                        )
                            }
                        ) {
                            composable<Screen.Home> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        TopAppBar(
                                            title = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.size(44.dp),
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxSize()
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Waves,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(26.dp),
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        stringResource(R.string.app_name),
                                                        style = MaterialTheme.typography.headlineLarge.copy(
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    )
                                                }
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent
                                            )
                                        )
                                        homeScreen()
                                    }
                                }
                            }

                            composable<Screen.Timer> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        TopAppBar(
                                            title = {
                                                Text(
                                                    stringResource(R.string.focus_mode_title),
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent
                                            ),
                                            modifier = Modifier.statusBarsPadding()
                                        )
                                        timerScreen()
                                    }
                                }
                            }

                            composable<Screen.Usage> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        MediumTopAppBar(
                                            title = {
                                                Text(
                                                    stringResource(R.string.app_usage),
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent
                                            )
                                        )
                                        usageScreen()
                                    }
                                }
                            }

                            composable<Screen.DailyLimit> { backStackEntry ->
                                val route = backStackEntry.toRoute<Screen.DailyLimit>()
                                val pkgName = route.packageName

                                val application = remember(pkgName) {
                                    packageManager.getApplicationInfo(pkgName, 0)
                                }
                                val appIcon = remember(application) {
                                    packageManager.getApplicationIcon(application)
                                }
                                val appName = remember(application) {
                                    packageManager.getApplicationLabel(application).toString()
                                }
                                val existingLimitMinutes = remember(pkgName) {
                                    (AppLimits.getLimit(pkgName) / 60000).toInt()
                                }

                                var weekOffset by remember { mutableIntStateOf(0) }

                                val dailyData by remember(pkgName, weekOffset) {
                                    derivedStateOf {
                                        getDailyUsageForLastWeek(
                                            pkgName,
                                            usageStatsManager,
                                            weekOffset
                                        )
                                    }
                                }

                                DailyLimitScreen(
                                    appName = appName,
                                    appIcon = appIcon,
                                    packageName = pkgName,
                                    existingLimitMinutes = existingLimitMinutes,
                                    dailyData = dailyData,
                                    onSave = { minutes ->
                                        AppLimits.setLimit(pkgName, minutes)
                                        AppLimits.save()
                                        navController.popBackStack()
                                    },
                                    onRemove = {
                                        AppLimits.removeLimit(pkgName)
                                        AppLimits.save()
                                        navController.popBackStack()
                                    },
                                    onBackPressed = { navController.popBackStack() },
                                    weekOffset = weekOffset,
                                    onWeekChange = { newOffset -> weekOffset = newOffset },
                                    canGoPrevious = weekOffset > -4
                                )
                            }

                            composable<Screen.Routines> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        MediumTopAppBar(
                                            title = {
                                                Text(
                                                    stringResource(R.string.routines),
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent
                                            )
                                        )
                                        RoutinesScreen(
                                            onCreateRoutine = {
                                                navController.navigate(
                                                    Screen.CreateRoutine(
                                                        null
                                                    )
                                                )
                                            },
                                            onEditRoutine = { routine ->
                                                navController.navigate(Screen.CreateRoutine(routine.id))
                                            }
                                        )
                                    }
                                }
                            }

                            composable<Screen.CreateRoutine> { backStackEntry ->
                                val route = backStackEntry.toRoute<Screen.CreateRoutine>()
                                CreateRoutineScreen(
                                    routineId = route.routineId,
                                    onBackPressed = { navController.popBackStack() },
                                    onSaveComplete = { navController.popBackStack() }
                                )
                            }

                            composable<Screen.Whitelist> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        MediumTopAppBar(
                                            title = {
                                                Text(
                                                    stringResource(R.string.whitelist_apps_title),
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent
                                            )
                                        )
                                        WhitelistScreenWrapper(
                                            launcherApps = launcherApps,
                                            packageManager = packageManager,
                                            currentPackageName = packageName
                                        )
                                    }
                                }
                            }

                            composable<Screen.Settings> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        MediumTopAppBar(
                                            title = {
                                                Text(
                                                    stringResource(R.string.settings),
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = Color.Transparent
                                            )
                                        )
                                        settingsScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchSoundPicker() {
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                android.media.RingtoneManager.TYPE_NOTIFICATION
            )
            putExtra(
                android.media.RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.select_transition_sound)
            )
            val currentSound = prefs.getString("pomodoro_sound", null)
            if (!currentSound.isNullOrEmpty()) {
                putExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    currentSound.toUri()
                )
            }
        }
        soundPickerLauncher.launch(intent)
    }

    private fun startFocusMode(config: TimerConfig) {
        when (config) {
            is TimerConfig.Simple -> {
                prefs.edit {
                    putBoolean("focus_mode", true)
                    putBoolean("pomodoro_mode", false)
                    putLong("focus_time", config.minutes * 60 * 1000L)
                    putBoolean("strict_mode", config.strictMode)
                }
            }

            is TimerConfig.Pomodoro -> {
                prefs.edit {
                    putBoolean("focus_mode", true)
                    putBoolean("pomodoro_mode", true)
                    putLong("focus_time", config.focusMinutes * 60 * 1000L)
                    putLong("pomodoro_focus_duration", config.focusMinutes * 60 * 1000L)
                    putLong("pomodoro_short_break_duration", config.shortBreakMinutes * 60 * 1000L)
                    putLong("pomodoro_long_break_duration", config.longBreakMinutes * 60 * 1000L)
                    putInt("pomodoro_cycles_before_long_break", config.cycles)
                    putInt("pomodoro_current_cycle", 1)
                    putString("pomodoro_state", "FOCUS")
                    putBoolean("strict_mode", config.strictMode)
                }
            }
        }
        startForegroundService(Intent(this, FocusModeService::class.java))
    }

    private fun pauseFocusMode() {
        startService(Intent(this, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_PAUSE
        })
    }

    private fun resumeFocusMode() {
        startService(Intent(this, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_RESUME
        })
    }

    private fun restartFocusMode() {
        startService(Intent(this, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_RESTART
        })
    }

    private fun cancelFocusMode() {
        stopService(Intent(this, FocusModeService::class.java))
        prefs.edit {
            putBoolean("focus_mode", false)
            remove("strict_mode")
        }
    }

    override fun onResume() {
        super.onResume()

        if (!hasCheckedPermissions && !prefs.getBoolean("first_run", true)) {
            hasCheckedPermissions = true
            checkAndRequestMissingPermissions()
        }

        if (pendingFocusModeStart && isAccessibilityServiceEnabledForBlocker()) {
            pendingFocusModeStart = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(timerReceiver)
        } catch (_: Exception) {
        }
        val timerState = TimerStateManager.state.value
        if (!timerState.isRunning && !timerState.isPaused) {
            prefs.edit {
                putBoolean("focus_mode", false)
                remove("strict_mode")
            }
        }
    }

    private fun addExceptions() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)

        packageManager.queryIntentActivities(intent, 0).forEach {
            val packageName = it.activityInfo.packageName
            if (!Whitelist.isWhitelisted(packageName)) {
                Whitelist.whitelist(packageName)
            }
        }
    }
}

@Composable
private fun ReefBottomNavBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Outlined.Home,
                label = stringResource(R.string.nav_home),
                selected = selectedItem == 0,
                onClick = { onItemSelected(0) }
            )
            BottomNavItem(
                icon = Icons.Rounded.BarChart,
                label = stringResource(R.string.nav_stats),
                selected = selectedItem == 1,
                onClick = { onItemSelected(1) }
            )
            BottomNavItem(
                icon = Icons.Rounded.SelfImprovement,
                label = stringResource(R.string.nav_focus),
                selected = selectedItem == 2,
                onClick = { onItemSelected(2) }
            )
            BottomNavItem(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.nav_settings),
                selected = selectedItem == 3,
                onClick = { onItemSelected(3) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                }
            )
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
