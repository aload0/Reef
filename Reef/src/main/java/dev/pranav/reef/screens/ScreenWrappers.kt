package dev.pranav.reef.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pranav.reef.ui.appusage.AppUsageScreen
import dev.pranav.reef.ui.appusage.AppUsageStats
import dev.pranav.reef.ui.appusage.AppUsageViewModel
import dev.pranav.reef.ui.whitelist.AllowedAppsState
import dev.pranav.reef.ui.whitelist.WhitelistScreen
import dev.pranav.reef.ui.whitelist.WhitelistedApp
import dev.pranav.reef.ui.whitelist.toBitmap
import dev.pranav.reef.util.Whitelist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UsageScreenWrapper(
    context: Context,
    usageStatsManager: UsageStatsManager,
    launcherApps: LauncherApps,
    packageManager: PackageManager,
    currentPackageName: String,
    onBackPressed: () -> Unit,
    onAppClick: (AppUsageStats) -> Unit
) {
    val viewModel: AppUsageViewModel = viewModel(
        factory = object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>): T {
                return AppUsageViewModel(
                    context, usageStatsManager, launcherApps, packageManager, currentPackageName
                ) as T
            }
        }
    )

    AppUsageScreen(
        viewModel = viewModel,
        onBackPressed = onBackPressed,
        onAppClick = onAppClick
    )
}

@Composable
fun WhitelistScreenWrapper(
    launcherApps: LauncherApps,
    packageManager: PackageManager,
    currentPackageName: String
) {
    var uiState by remember { mutableStateOf<AllowedAppsState>(AllowedAppsState.Loading) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = launcherApps.getActivityList(null, Process.myUserHandle())
                .asSequence()
                .distinctBy { it.applicationInfo.packageName }
                .map { it.applicationInfo }
                .filter { it.packageName != currentPackageName }
                .map { appInfo ->
                    WhitelistedApp(
                        packageName = appInfo.packageName,
                        label = appInfo.loadLabel(packageManager).toString(),
                        icon = appInfo.loadIcon(packageManager).toBitmap().asImageBitmap(),
                        isWhitelisted = Whitelist.isWhitelisted(appInfo.packageName)
                    )
                }
                .sortedBy { it.label }
                .toList()
            uiState = AllowedAppsState.Success(apps)
        }
    }

    fun toggleWhitelist(app: WhitelistedApp) {
        if (app.isWhitelisted) {
            Whitelist.unwhitelist(app.packageName)
        } else {
            Whitelist.whitelist(app.packageName)
        }

        val currentState = uiState
        if (currentState is AllowedAppsState.Success) {
            val updatedList = currentState.apps.map {
                if (it.packageName == app.packageName) {
                    it.copy(isWhitelisted = !it.isWhitelisted)
                } else {
                    it
                }
            }
            uiState = AllowedAppsState.Success(updatedList)
        }
    }

    WhitelistScreen(
        uiState = uiState,
        onToggle = ::toggleWhitelist
    )
}
