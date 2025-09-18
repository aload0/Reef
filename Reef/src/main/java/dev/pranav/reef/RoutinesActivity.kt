package dev.pranav.reef

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import dev.pranav.reef.databinding.ActivityRoutinesBinding
import dev.pranav.reef.databinding.ItemRoutineBinding
import dev.pranav.reef.util.NotificationHelper
import dev.pranav.reef.util.RoutineLimits
import dev.pranav.reef.util.RoutineManager
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.applyWindowInsets
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class RoutinesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoutinesBinding
    private val adapter by lazy { RoutinesAdapter { routine -> onRoutineClicked(routine) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()

        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        super.onCreate(savedInstanceState)
        binding = ActivityRoutinesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets(binding.rootLayout)

        setupUI()
        loadRoutines()
    }

    override fun onResume() {
        super.onResume()
        loadRoutines()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.routinesRecyclerView.adapter = adapter

        binding.fabAddRoutine.setOnClickListener {
            startActivity(Intent(this, CreateRoutineActivity::class.java))
        }
    }

    private fun loadRoutines() {
        val routines = RoutineManager.getRoutines()

        if (routines.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.routinesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.routinesRecyclerView.visibility = View.VISIBLE
            adapter.submitList(routines)
        }
    }

    private fun onRoutineClicked(routine: Routine) {
        if (routine.schedule.type == RoutineSchedule.ScheduleType.MANUAL) {
            // For manual routines, show option to activate immediately
            MaterialAlertDialogBuilder(this)
                .setTitle("Activate Routine")
                .setMessage("Do you want to activate '${routine.name}' now?")
                .setPositiveButton("Activate") { _, _ ->
                    activateRoutineNow(routine)
                }
                .setNeutralButton("Edit") { _, _ ->
                    editRoutine(routine)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            editRoutine(routine)
        }
    }

    private fun editRoutine(routine: Routine) {
        val intent = Intent(this, CreateRoutineActivity::class.java).apply {
            putExtra("routine_id", routine.id)
        }
        startActivity(intent)
    }

    private fun activateRoutineNow(routine: Routine) {
        // Apply routine limits using the separate system
        val limitsMap = routine.limits.associate { it.packageName to it.limitMinutes }
        RoutineLimits.setRoutineLimits(limitsMap, routine.id)

        // Show confirmation
        val limitsText = when (routine.limits.size) {
            0 -> "No app limits were applied"
            1 -> "1 app limit has been applied"
            else -> "${routine.limits.size} app limits have been applied"
        }

        Snackbar.make(
            binding.root,
            "Routine '${routine.name}' activated! $limitsText",
            Snackbar.LENGTH_LONG
        ).show()

        // Show notification
        NotificationHelper.showRoutineActivatedNotification(this, routine)
    }
}

class RoutinesAdapter(
    private val onRoutineClick: (Routine) -> Unit
) : ListAdapter<Routine, RoutineViewHolder>(RoutineDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoutineViewHolder {
        val binding = ItemRoutineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoutineViewHolder(binding, onRoutineClick)
    }

    override fun onBindViewHolder(holder: RoutineViewHolder, position: Int) {
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

class RoutineViewHolder(
    private val binding: ItemRoutineBinding,
    private val onRoutineClick: (Routine) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    private var currentRoutine: Routine? = null

    init {
        binding.root.setOnClickListener {
            currentRoutine?.let { onRoutineClick(it) }
        }

        binding.routineToggle.setOnCheckedChangeListener { _, _ ->
            currentRoutine?.let { routine ->
                RoutineManager.toggleRoutine(routine.id, binding.root.context)
            }
        }
    }

    fun bind(routine: Routine) {
        currentRoutine = routine

        binding.routineName.text = routine.name
        binding.routineSchedule.text = formatSchedule(routine.schedule)
        binding.routineAppsCount.text = when (routine.limits.size) {
            0 -> "No app limits set"
            1 -> "1 app with limit"
            else -> "${routine.limits.size} apps with limits"
        }

        // Temporarily remove the listener to prevent it from firing during data binding
        binding.routineToggle.setOnCheckedChangeListener(null)
        binding.routineToggle.isChecked = routine.isEnabled
        // Restore the listener after setting the value
        binding.routineToggle.setOnCheckedChangeListener { _, _ ->
            currentRoutine?.let { routine ->
                RoutineManager.toggleRoutine(routine.id, binding.root.context)
            }
        }

        if (routine.isEnabled) {
            binding.routineStatus.visibility = View.VISIBLE
            binding.routineStatus.setText(R.string.active_status)
        } else {
            binding.routineStatus.visibility = View.GONE
        }
    }

    private fun formatSchedule(schedule: RoutineSchedule): String {
        Log.d("RoutineViewHolder", "Formatting schedule: $schedule")
        return when (schedule.type) {
            RoutineSchedule.ScheduleType.DAILY -> {
                val timeRange = if (schedule.time != null && schedule.endTime != null) {
                    " from ${formatTime(schedule.time!!)} to ${formatTime(schedule.endTime!!)}"
                } else if (schedule.time != null) {
                    " at ${formatTime(schedule.time!!)}"
                } else ""
                "Daily$timeRange"
            }

            RoutineSchedule.ScheduleType.WEEKLY -> {
                val days = if (schedule.daysOfWeek.size == 7) {
                    "Every day"
                } else if (schedule.daysOfWeek.containsAll(
                        listOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                        )
                    )
                ) {
                    "Weekdays"
                } else if (schedule.daysOfWeek.containsAll(
                        listOf(
                            DayOfWeek.SATURDAY,
                            DayOfWeek.SUNDAY
                        )
                    )
                ) {
                    "Weekends"
                } else {
                    schedule.daysOfWeek.sortedBy { it.value }
                        .joinToString(", ") {
                            it.getDisplayName(
                                TextStyle.SHORT,
                                Locale.getDefault()
                            )
                        }
                }
                val timeRange = if (schedule.time != null && schedule.endTime != null) {
                    " from ${formatTime(schedule.time!!)} to ${formatTime(schedule.endTime!!)}"
                } else if (schedule.time != null) {
                    " at ${formatTime(schedule.time!!)}"
                } else ""
                "$days$timeRange"
            }

            RoutineSchedule.ScheduleType.MANUAL -> "Manual activation"
        }
    }

    private fun formatTime(time: java.time.LocalTime): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        return time.format(formatter)
    }
}

class RoutineDiffCallback : DiffUtil.ItemCallback<Routine>() {
    override fun areItemsTheSame(oldItem: Routine, newItem: Routine): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Routine, newItem: Routine): Boolean {
        return oldItem == newItem
    }
}
