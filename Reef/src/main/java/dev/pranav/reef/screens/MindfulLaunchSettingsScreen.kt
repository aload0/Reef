package dev.pranav.reef.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pranav.reef.R
import dev.pranav.reef.util.MindfulLaunchManager
import dev.pranav.reef.util.append
import dev.pranav.reef.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulLaunchScreen(
    onBackPressed: () -> Unit,
    onNavigateToApps: () -> Unit
) {
    BackHandler { onBackPressed() }

    var enabled by remember { mutableStateOf(MindfulLaunchManager.isEnabled()) }
    var duration by remember { mutableStateOf(MindfulLaunchManager.getDurationSeconds()) }
    var warningMessage by remember { mutableStateOf(MindfulLaunchManager.getWarningMessage()) }
    var limitEnabled by remember { mutableStateOf(MindfulLaunchManager.isLimitEnabled()) }
    var limitCount by remember { mutableIntStateOf(MindfulLaunchManager.getLimitCount()) }
    var phoneUnlockEnabled by remember { mutableStateOf(MindfulLaunchManager.isPhoneUnlockEnabled()) }
    val selectedAppsCount = remember { MindfulLaunchManager.getMindfulApps().size }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.mindful_launch)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.append(16.dp)
        ) {
            // Section 1: Main Toggle
            item {
                Text(
                    text = stringResource(R.string.active_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsCard(index = 0, listSize = 1) {
                    ListItem(
                        modifier = Modifier
                            .clickable {
                                enabled = !enabled
                                prefs.edit { putBoolean("mindful_launch_enabled", enabled) }
                            }
                            .padding(4.dp),
                        headlineContent = {
                            Text(
                                if (enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.mindful_launch_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    prefs.edit { putBoolean("mindful_launch_enabled", it) }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Section 2: Customizations (Only shown if enabled)
            if (enabled) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Configurations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Selected Apps Row
                item {
                    SettingsCard(index = 0, listSize = 3) {
                        ListItem(
                            modifier = Modifier
                                .clickable { onNavigateToApps() }
                                .padding(4.dp),
                            headlineContent = {
                                Text(
                                    stringResource(R.string.mindful_launch_apps),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.mindful_launch_apps_description),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (selectedAppsCount == 1) "1 app" else "$selectedAppsCount apps",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                item {
                    SettingsCard(index = 1, listSize = 3) {
                        ListItem(
                            modifier = Modifier
                                .clickable {
                                    phoneUnlockEnabled = !phoneUnlockEnabled
                                    MindfulLaunchManager.setPhoneUnlockEnabled(phoneUnlockEnabled)
                                }
                                .padding(4.dp),
                            headlineContent = {
                                Text(
                                    stringResource(R.string.mindful_launch_phone_unlock),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.mindful_launch_phone_unlock_description),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = phoneUnlockEnabled,
                                    onCheckedChange = {
                                        phoneUnlockEnabled = it
                                        MindfulLaunchManager.setPhoneUnlockEnabled(it)
                                    }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                // Countdown Duration Picker
                item {
                    SettingsCard(index = 2, listSize = 3) {
                        NumberSettingItem(
                            label = stringResource(R.string.mindful_launch_duration),
                            value = duration,
                            range = 3..60,
                            suffix = "s",
                            onValueChange = {
                                duration = it
                                prefs.edit { putInt("mindful_launch_duration", it) }
                            }
                        )
                    }
                }

                // Warning Message Box
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.mindful_launch_warning),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    SettingsCard(index = 0, listSize = 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = warningMessage,
                                onValueChange = {
                                    warningMessage = it
                                    prefs.edit { putString("mindful_launch_warning", it) }
                                },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.mindful_launch_warning_hint),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                        }
                    }
                }

                // Daily Launch Limits
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.mindful_launch_limit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    SettingsCard(index = 0, listSize = if (limitEnabled) 2 else 1) {
                        ListItem(
                            modifier = Modifier
                                .clickable {
                                    limitEnabled = !limitEnabled
                                    prefs.edit {
                                        putBoolean(
                                            "mindful_launch_limit_enabled",
                                            limitEnabled
                                        )
                                    }
                                }
                                .padding(4.dp),
                            headlineContent = {
                                Text(
                                    stringResource(R.string.enable_launch_limit),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = limitEnabled,
                                    onCheckedChange = {
                                        limitEnabled = it
                                        prefs.edit {
                                            putBoolean(
                                                "mindful_launch_limit_enabled",
                                                it
                                            )
                                        }
                                    }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                if (limitEnabled) {
                    item {
                        SettingsCard(index = 1, listSize = 2) {
                            NumberSettingItem(
                                label = stringResource(R.string.launch_limit_count),
                                value = limitCount,
                                range = 1..100,
                                suffix = stringResource(R.string.launches_suffix),
                                onValueChange = {
                                    limitCount = it
                                    prefs.edit { putInt("mindful_launch_limit_count", it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

class MindfulLaunchAppsViewModel(
    private val launcherApps: LauncherApps,
    private val packageManager: PackageManager,
    private val currentPackageName: String
) : ViewModel() {

    private val _uiState = mutableStateOf<AllowedAppsState>(AllowedAppsState.Loading)
    val uiState: State<AllowedAppsState> = _uiState

    private var allApps = listOf<WhitelistedApp>()

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _showSystemApps = mutableStateOf(false)
    val showSystemApps: State<Boolean> = _showSystemApps

    private val _onlyLaunchable = mutableStateOf(true)
    val onlyLaunchable: State<Boolean> = _onlyLaunchable

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val profiles = launcherApps.profiles
                val allAppsList = mutableListOf<WhitelistedApp>()

                profiles.forEach { userHandle ->
                    val launcherActivities = launcherApps.getActivityList(null, userHandle)
                    val launchablePackages =
                        launcherActivities.map { it.applicationInfo.packageName }.toSet()

                    val allInstalledApps =
                        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

                    val combined = allInstalledApps
                        .filter { it.packageName != currentPackageName }
                        .map { appInfo ->
                            val isLaunchable = launchablePackages.contains(appInfo.packageName)
                            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                            WhitelistedApp(
                                packageName = appInfo.packageName,
                                label = appInfo.loadLabel(packageManager).toString(),
                                isWhitelisted = MindfulLaunchManager.isMindfulApp(appInfo.packageName),
                                user = userHandle,
                                isSystemApp = isSystem,
                                isLaunchable = isLaunchable
                            )
                        }
                    allAppsList.addAll(combined)
                }
                allAppsList.distinctBy { it.packageName + it.user.hashCode() }.sortedBy { it.label }
            }
            allApps = apps
            updateFilteredList()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        updateFilteredList()
    }

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
        updateFilteredList()
    }

    fun toggleOnlyLaunchable() {
        _onlyLaunchable.value = !_onlyLaunchable.value
        updateFilteredList()
    }

    private fun updateFilteredList() {
        val query = _searchQuery.value
        val showSystem = _showSystemApps.value
        val onlyLaunch = _onlyLaunchable.value

        val filtered = allApps.filter { app ->
            val matchesQuery = query.isEmpty() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesSystem = showSystem || !app.isSystemApp
            val matchesLaunchable = !onlyLaunch || app.isLaunchable

            matchesQuery && matchesSystem && matchesLaunchable
        }
        _uiState.value = AllowedAppsState.Success(filtered)
    }

    fun toggleApp(app: WhitelistedApp) {
        val currentSet = MindfulLaunchManager.getMindfulApps().toMutableSet()
        if (app.isWhitelisted) {
            currentSet.remove(app.packageName)
        } else {
            currentSet.add(app.packageName)
        }
        MindfulLaunchManager.setMindfulApps(currentSet)

        allApps = allApps.map {
            if (it.packageName == app.packageName && it.user == app.user) {
                it.copy(isWhitelisted = !it.isWhitelisted)
            } else {
                it
            }
        }
        updateFilteredList()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MindfulLaunchAppsScreen(
    onBackPressed: () -> Unit
) {
    BackHandler { onBackPressed() }

    val context = LocalContext.current
    val launcherApps =
        remember { context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps }
    val packageManager = remember { context.packageManager }
    val currentPackageName = remember { context.packageName }
    var appToConfigure by remember { mutableStateOf<WhitelistedApp?>(null) }

    val viewModel: MindfulLaunchAppsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MindfulLaunchAppsViewModel(launcherApps, packageManager, currentPackageName) as T
        }
    )

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.mindful_launch_apps)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Rounded.FilterList, contentDescription = "Filters")
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Show System Apps") },
                                onClick = {
                                    viewModel.toggleSystemApps()
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    Checkbox(
                                        checked = viewModel.showSystemApps.value,
                                        onCheckedChange = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Only Launchable Apps") },
                                onClick = {
                                    viewModel.toggleOnlyLaunchable()
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    Checkbox(
                                        checked = viewModel.onlyLaunchable.value,
                                        onCheckedChange = null
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val uiState = viewModel.uiState.value) {
                is AllowedAppsState.Loading -> {
                    ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is AllowedAppsState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = viewModel.searchQuery.value,
                                onValueChange = viewModel::onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_apps),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (viewModel.searchQuery.value.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(28.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                        }

                        if (uiState.apps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_apps_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = uiState.apps,
                                key = { _, app -> app.packageName + app.user.hashCode() }
                            ) { index, app ->
                                MindfulLaunchProtectedAppItem(
                                    app = app,
                                    index = index,
                                    listSize = uiState.apps.size,
                                    onToggle = { viewModel.toggleApp(app) },
                                    onConfigure = { appToConfigure = app }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    appToConfigure?.let { app ->
        MindfulLaunchAppOverrideDialog(
            app = app,
            onDismiss = { appToConfigure = null }
        )
    }
}

@Composable
private fun MindfulLaunchProtectedAppItem(
    app: WhitelistedApp,
    index: Int,
    listSize: Int,
    onToggle: () -> Unit,
    onConfigure: () -> Unit
) {
    val context = LocalContext.current
    var icon by remember(app.packageName, app.user) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.packageName, app.user) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(app.packageName, 0)
                val originalIcon = appInfo.loadIcon(pm)
                icon = pm.getUserBadgedIcon(originalIcon, app.user)
            } catch (_: Exception) {
            }
        }
    }

    val summary = when {
        !app.isWhitelisted -> null
        !MindfulLaunchManager.hasAppOverrides(app.packageName) ->
            stringResource(R.string.mindful_launch_uses_defaults)

        else -> {
            val limitSummary =
                if (MindfulLaunchManager.isLimitEnabled(app.packageName)) {
                    stringResource(
                        R.string.mindful_launch_limit_summary,
                        MindfulLaunchManager.getLimitCount(app.packageName)
                    )
                } else {
                    stringResource(R.string.mindful_launch_no_limit)
                }
            stringResource(
                R.string.mindful_launch_custom_summary,
                MindfulLaunchManager.getDurationSeconds(app.packageName),
                limitSummary
            )
        }
    }

    SettingsCard(index = index, listSize = listSize) {
        ListItem(
            modifier = Modifier
                .clickable {
                    if (app.isWhitelisted) onConfigure() else onToggle()
                }
                .padding(4.dp),
            headlineContent = {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            },
            supportingContent = summary?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    icon?.let {
                        Image(
                            painter = BitmapPainter(it.toBitmap().asImageBitmap()),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app.isWhitelisted) {
                        IconButton(onClick = onConfigure) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = stringResource(
                                    R.string.mindful_launch_customize_app
                                )
                            )
                        }
                    }
                    Checkbox(
                        checked = app.isWhitelisted,
                        onCheckedChange = { onToggle() }
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun MindfulLaunchAppOverrideDialog(
    app: WhitelistedApp,
    onDismiss: () -> Unit
) {
    val packageName = app.packageName
    var useGlobalSettings by remember(packageName) {
        mutableStateOf(!MindfulLaunchManager.hasAppOverrides(packageName))
    }
    var durationSeconds by remember(packageName) {
        mutableIntStateOf(
            MindfulLaunchManager.getDurationSeconds(packageName).coerceIn(5, 300)
        )
    }
    var warningMessage by remember(packageName) {
        mutableStateOf(MindfulLaunchManager.getWarningMessage(packageName))
    }
    var limitEnabled by remember(packageName) {
        mutableStateOf(MindfulLaunchManager.isLimitEnabled(packageName))
    }
    var limitCount by remember(packageName) {
        mutableIntStateOf(MindfulLaunchManager.getLimitCount(packageName))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.8f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(stringResource(R.string.mindful_launch_app_settings, app.label))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure behavior for " + app.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.use_global_settings))
                    },
                    trailingContent = {
                        Switch(
                            checked = useGlobalSettings,
                            onCheckedChange = { useGlobalSettings = it }
                        )
                    },
                    modifier = Modifier.clickable {
                        useGlobalSettings = !useGlobalSettings
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (!useGlobalSettings) {
                    Text(
                        text = stringResource(
                            R.string.mindful_launch_delay_seconds,
                            durationSeconds
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = durationSeconds.toFloat(),
                        onValueChange = {
                            durationSeconds = (it / 5f).roundToInt() * 5
                        },
                        valueRange = 5f..300f,
                        steps = 58
                    )
                    OutlinedTextField(
                        value = warningMessage,
                        onValueChange = { warningMessage = it },
                        label = {
                            Text(stringResource(R.string.mindful_launch_warning))
                        },
                        placeholder = {
                            Text(stringResource(R.string.mindful_launch_warning_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.enable_launch_limit))
                        },
                        trailingContent = {
                            Switch(
                                checked = limitEnabled,
                                onCheckedChange = { limitEnabled = it }
                            )
                        },
                        modifier = Modifier.clickable {
                            limitEnabled = !limitEnabled
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (limitEnabled) {
                        Text(
                            text = stringResource(
                                R.string.mindful_launch_limit_summary,
                                limitCount
                            ),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = limitCount.toFloat(),
                            onValueChange = { limitCount = it.roundToInt() },
                            valueRange = 1f..100f,
                            steps = 98
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (useGlobalSettings) {
                        MindfulLaunchManager.clearAppOverrides(packageName)
                    } else {
                        MindfulLaunchManager.setAppOverrides(
                            pkg = packageName,
                            durationSeconds = durationSeconds,
                            warningMessage = warningMessage,
                            limitEnabled = limitEnabled,
                            limitCount = limitCount
                        )
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
