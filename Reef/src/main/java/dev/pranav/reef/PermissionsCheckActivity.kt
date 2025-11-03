package dev.pranav.reef

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import dev.pranav.reef.util.PermissionStatus
import dev.pranav.reef.util.PermissionType
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.checkAllPermissions

class PermissionsCheckActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PermissionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions_check)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.permissions_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadPermissions()
    }

    private fun loadPermissions() {
        val permissions = checkAllPermissions()
        adapter = PermissionAdapter(permissions) { permission ->
            // Open settings directly without showing dialog
            when (permission.type) {
                PermissionType.ACCESSIBILITY -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }

                PermissionType.USAGE_STATS -> {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }

                PermissionType.NOTIFICATION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            100
                        )
                    }
                }

                PermissionType.BATTERY_OPTIMIZATION -> {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
        }
        recyclerView.adapter = adapter
    }

    private class PermissionAdapter(
        private val permissions: List<PermissionStatus>,
        private val onGrantClick: (PermissionStatus) -> Unit
    ) : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.permission_title)
            val description: TextView = view.findViewById(R.id.permission_description)
            val status: TextView = view.findViewById(R.id.permission_status)
            val grantButton: Button = view.findViewById(R.id.grant_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_permission, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val permission = permissions[position]
            holder.title.text = permission.title
            holder.description.text = permission.description

            if (permission.isGranted) {
                holder.status.text = "Granted"
                holder.status.setTextColor(0xFF4CAF50.toInt())
                holder.grantButton.visibility = View.GONE
            } else {
                holder.status.text = "Not Granted"
                holder.status.setTextColor(0xFFF44336.toInt())
                holder.grantButton.visibility = View.VISIBLE
                holder.grantButton.setOnClickListener {
                    onGrantClick(permission)
                }
            }
        }

        override fun getItemCount() = permissions.size
    }
}
