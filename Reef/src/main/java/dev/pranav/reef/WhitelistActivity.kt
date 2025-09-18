package dev.pranav.reef

import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.databinding.ActivityWhitelistBinding
import dev.pranav.reef.databinding.AppItemBinding
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WhitelistedApp(
    val appInfo: ApplicationInfo,
    val isWhitelisted: Boolean
)

class WhitelistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWhitelistBinding
    private val adapter = WhitelistAdapter { app -> onAppItemToggled(app) }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()

        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        super.onCreate(savedInstanceState)
        binding = ActivityWhitelistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()
        loadInstalledApps()
    }

    private fun setupRecyclerView() {
        binding.appsRecyclerView.adapter = adapter
    }

    private fun loadInstalledApps() {
        binding.progressIndicator.isVisible = true
        lifecycleScope.launch(Dispatchers.IO) {
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val apps = launcherApps.getActivityList(null, android.os.Process.myUserHandle())
                .asSequence()
                .map { it.applicationInfo }
                .filter { it.packageName != packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                .map { appInfo ->
                    WhitelistedApp(
                        appInfo = appInfo,
                        isWhitelisted = Whitelist.isWhitelisted(appInfo.packageName)
                    )
                }
                .toList()

            withContext(Dispatchers.Main) {
                binding.progressIndicator.isVisible = false
                adapter.submitList(apps)
            }
        }
    }

    private fun onAppItemToggled(toggledApp: WhitelistedApp) {
        if (toggledApp.isWhitelisted) {
            Whitelist.unwhitelist(toggledApp.appInfo.packageName)
        } else {
            Whitelist.whitelist(toggledApp.appInfo.packageName)
        }

        val newList = adapter.currentList.map { app ->
            if (app.appInfo.packageName == toggledApp.appInfo.packageName) {
                app.copy(isWhitelisted = !app.isWhitelisted)
            } else {
                app
            }
        }
        adapter.submitList(newList)
    }
}


class WhitelistAdapter(private val onToggle: (WhitelistedApp) -> Unit) :
    ListAdapter<WhitelistedApp, WhitelistViewHolder>(WhitelistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WhitelistViewHolder {
        val binding = AppItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WhitelistViewHolder(binding, onToggle)
    }

    override fun onBindViewHolder(holder: WhitelistViewHolder, position: Int) {
        holder.bind(getItem(position))

        val context = holder.itemView.context

        val background = when {
            itemCount == 1 -> ContextCompat.getDrawable(context, R.drawable.list_item_single)

            position == 0 -> ContextCompat.getDrawable(context, R.drawable.list_item_top)

            position == itemCount - 1 -> ContextCompat.getDrawable(
                context,
                R.drawable.list_item_bottom
            )

            else -> ContextCompat.getDrawable(context, R.drawable.list_item_middle)
        }
        holder.itemView.background = background
    }
}

class WhitelistViewHolder(
    private val binding: AppItemBinding,
    private val onToggle: (WhitelistedApp) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(app: WhitelistedApp) {
        binding.appIcon.setImageDrawable(itemView.context.packageManager.getApplicationIcon(app.appInfo))
        binding.appName.text = itemView.context.packageManager.getApplicationLabel(app.appInfo)
        binding.checkbox.isChecked = app.isWhitelisted

        binding.root.setOnClickListener {
            onToggle(app)
        }
    }
}

class WhitelistDiffCallback : DiffUtil.ItemCallback<WhitelistedApp>() {
    override fun areItemsTheSame(oldItem: WhitelistedApp, newItem: WhitelistedApp): Boolean {
        return oldItem.appInfo.packageName == newItem.appInfo.packageName
    }

    override fun areContentsTheSame(oldItem: WhitelistedApp, newItem: WhitelistedApp): Boolean {
        return oldItem.isWhitelisted == newItem.isWhitelisted
    }
}
