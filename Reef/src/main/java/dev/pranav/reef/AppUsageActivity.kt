package dev.pranav.reef

import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.databinding.ActivityUsageBinding
import dev.pranav.reef.databinding.AppUsageItemBinding
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Stats(val applicationInfo: ApplicationInfo, val usageStats: AppLimits.AppUsageStats)

class AppUsageActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUsageBinding
    private val adapter by lazy { AppUsageAdapter(packageManager) { stats -> onAppItemClicked(stats) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()

        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        super.onCreate(savedInstanceState)
        binding = ActivityUsageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets(binding.rootLayout)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        loadUsageStats()
    }

    private fun setupRecyclerView() {
        binding.appUsageRecyclerView.adapter = adapter
    }

    private fun loadUsageStats() {
        // Use coroutines to fetch data off the main thread
        GlobalScope.launch(Dispatchers.IO) {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val appUsageStats = AppLimits.getUsageStats(usageStatsManager)

            val maxUsage = appUsageStats.firstOrNull()?.totalTimeInForeground ?: 1L

            val filteredAppUsageStats = appUsageStats
                .asSequence()
                .filter { it.totalTimeInForeground > 5 * 1000 }
                .mapNotNull { stats ->
                    // Use mapNotNull to safely handle cases where app info might be null
                    launcherApps.getApplicationInfo(stats.packageName, 0, Process.myUserHandle())
                        ?.let { appInfo -> Stats(appInfo, stats) }
                }
                .toList()

            withContext(Dispatchers.Main) {
                adapter.setMaxUsage(maxUsage) // Pass the max usage for the progress bar
                adapter.submitList(filteredAppUsageStats)
            }
        }
    }

    private fun onAppItemClicked(stats: Stats) {
        if (stats.applicationInfo.packageName == packageName) {
            Snackbar.make(binding.root, "Cannot set limit for Reef", Snackbar.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ApplicationDailyLimitActivity::class.java).apply {
            putExtra("package_name", stats.applicationInfo.packageName)
        }
        startActivity(intent)
    }
}

class AppUsageAdapter(
    private val packageManager: PackageManager,
    private val onClick: (Stats) -> Unit
) :
    ListAdapter<Stats, AppUsageViewHolder>(StatsDiffCallback()) {

    private var maxUsage: Long = 1L

    fun setMaxUsage(max: Long) {
        maxUsage = if (max > 0) max else 1L
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val binding =
            AppUsageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppUsageViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        holder.bind(getItem(position), packageManager, maxUsage)
    }
}

class AppUsageViewHolder(
    private val binding: AppUsageItemBinding,
    private val onClick: (Stats) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    private var currentStat: Stats? = null

    init {
        binding.root.setOnClickListener {
            currentStat?.let { onClick(it) }
        }
    }

    fun bind(stats: Stats, packageManager: PackageManager, maxUsage: Long) {
        currentStat = stats
        binding.appIcon.setImageDrawable(stats.applicationInfo.loadIcon(packageManager))
        binding.appName.text = stats.applicationInfo.loadLabel(packageManager)
        binding.appUsage.text = formatTime(stats.usageStats.totalTimeInForeground)

        // Update the progress indicator
        val progress = (stats.usageStats.totalTimeInForeground * 100 / maxUsage).toInt()
        binding.usageProgressIndicator.progress = progress
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
}

class StatsDiffCallback : DiffUtil.ItemCallback<Stats>() {
    override fun areItemsTheSame(oldItem: Stats, newItem: Stats): Boolean {
        return oldItem.applicationInfo.packageName == newItem.applicationInfo.packageName
    }

    override fun areContentsTheSame(oldItem: Stats, newItem: Stats): Boolean {
        // You might want a more thorough check here if usageStats can change meaningfully
        return oldItem == newItem
    }
}
