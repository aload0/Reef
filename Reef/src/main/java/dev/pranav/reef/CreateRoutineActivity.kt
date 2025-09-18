package dev.pranav.reef

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.transition.platform.MaterialSharedAxis
import dev.pranav.reef.data.Routine
import dev.pranav.reef.data.RoutineSchedule
import dev.pranav.reef.databinding.ActivityCreateRoutineBinding
import dev.pranav.reef.databinding.ItemAppLimitBinding
import dev.pranav.reef.util.RoutineManager
import dev.pranav.reef.util.applyDefaults
import dev.pranav.reef.util.applyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

class CreateRoutineActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateRoutineBinding
    private var currentRoutine: Routine? = null
    private var selectedTime = LocalTime.of(9, 0)
    private var selectedEndTime = LocalTime.of(17, 0)
    private val appLimitsAdapter = AppLimitsAdapter { appLimit -> removeAppLimit(appLimit) }
    private val currentLimits = mutableListOf<Routine.AppLimit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyDefaults()

        window.enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
        window.returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)

        super.onCreate(savedInstanceState)
        binding = ActivityCreateRoutineBinding.inflate(layoutInflater)
        applyWindowInsets(binding.root)
        setContentView(binding.root)

        setupUI()
        loadRoutineIfEditing()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    saveRoutine()
                    true
                }

                else -> false
            }
        }

        binding.timePickerButton.text = selectedTime.toString()
        binding.endTimePickerButton.text = selectedEndTime.toString()

        binding.appLimitsRecyclerView.adapter = appLimitsAdapter

        binding.scheduleTypeToggle.check(R.id.weekly_button)
        updateScheduleUI()

        binding.scheduleTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateScheduleUI()
            }
        }

        binding.timePickerButton.setOnClickListener {
            showTimePicker()
        }

        binding.endTimePickerButton.setOnClickListener {
            showEndTimePicker()
        }

        binding.addAppLimitButton.setOnClickListener {
            showAppSelectionDialog()
        }

        binding.saveRoutineButton.setOnClickListener {
            saveRoutine()
        }

        binding.deleteRoutineButton.setOnClickListener {
            deleteRoutine()
        }

        updateAppLimitsUI()
    }

    private fun loadRoutineIfEditing() {
        val routineId = intent.getStringExtra("routine_id")
        if (routineId != null) {
            currentRoutine = RoutineManager.getRoutines().find { it.id == routineId }
            currentRoutine?.let { routine ->
                binding.toolbar.title = "Edit Routine"
                binding.routineNameInput.setText(routine.name)
                binding.deleteRoutineButton.visibility = View.VISIBLE

                when (routine.schedule.type) {
                    RoutineSchedule.ScheduleType.DAILY -> binding.scheduleTypeToggle.check(R.id.daily_button)
                    RoutineSchedule.ScheduleType.WEEKLY -> binding.scheduleTypeToggle.check(R.id.weekly_button)
                    RoutineSchedule.ScheduleType.MANUAL -> binding.scheduleTypeToggle.check(R.id.manual_button)
                }

                routine.schedule.time?.let { time ->
                    selectedTime = time
                    updateTimeButton()
                }

                routine.schedule.endTime?.let { endTime ->
                    selectedEndTime = endTime
                    updateEndTimeButton()
                }

                routine.schedule.daysOfWeek.forEach { day ->
                    getDayChip(day)?.isChecked = true
                }

                currentLimits.clear()
                currentLimits.addAll(routine.limits)
                updateAppLimitsUI()
            }
        }
    }

    private fun updateScheduleUI() {
        val checkedId = binding.scheduleTypeToggle.checkedButtonId

        when (checkedId) {
            R.id.daily_button -> {
                binding.timeSelectionLayout.visibility = View.VISIBLE
                binding.daysSelectionLayout.visibility = View.GONE
            }

            R.id.weekly_button -> {
                binding.timeSelectionLayout.visibility = View.VISIBLE
                binding.daysSelectionLayout.visibility = View.VISIBLE
            }

            R.id.manual_button -> {
                binding.timeSelectionLayout.visibility = View.GONE
                binding.daysSelectionLayout.visibility = View.GONE
            }
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTitleText("Select Time")
            .setHour(selectedTime.hour)
            .setMinute(selectedTime.minute)
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedTime = LocalTime.of(picker.hour, picker.minute)
            updateTimeButton()
        }

        picker.show(supportFragmentManager, "time_picker")
    }

    private fun showEndTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTitleText("Select End Time")
            .setHour(selectedEndTime.hour)
            .setMinute(selectedEndTime.minute)
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedEndTime = LocalTime.of(picker.hour, picker.minute)
            updateEndTimeButton()
        }

        picker.show(supportFragmentManager, "end_time_picker")
    }

    private fun updateTimeButton() {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        binding.timePickerButton.text = selectedTime.format(formatter)
    }

    private fun updateEndTimeButton() {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        binding.endTimePickerButton.text = selectedEndTime.format(formatter)
    }

    private fun showAppSelectionDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val installedApps = packageManager.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filter { it.packageName != packageName }
                .sortedBy { packageManager.getApplicationLabel(it).toString() }

            val appNames = installedApps.map { packageManager.getApplicationLabel(it).toString() }
                .toTypedArray()
            val packageNames = installedApps.map { it.packageName }.toTypedArray()

            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@CreateRoutineActivity)
                    .setTitle("Select App")
                    .setItems(appNames) { _, which ->
                        showTimeLimitDialog(packageNames[which], appNames[which])
                    }
                    .show()
            }
        }
    }

    private fun showTimeLimitDialog(packageName: String, appName: String) {
        val timeOptions = arrayOf("15 minutes", "30 minutes", "1 hour", "2 hours", "3 hours")
        val timeValues = arrayOf(15, 30, 60, 120, 180)

        MaterialAlertDialogBuilder(this)
            .setTitle("Set limit for $appName")
            .setItems(timeOptions) { _, which ->
                val limit = Routine.AppLimit(packageName, timeValues[which])
                currentLimits.removeAll { it.packageName == packageName }
                currentLimits.add(limit)
                updateAppLimitsUI()
            }
            .show()
    }

    private fun removeAppLimit(appLimit: Routine.AppLimit) {
        currentLimits.removeAll { it.packageName == appLimit.packageName }
        updateAppLimitsUI()
    }

    private fun updateAppLimitsUI() {
        if (currentLimits.isEmpty()) {
            binding.noLimitsText.visibility = View.VISIBLE
            binding.appLimitsRecyclerView.visibility = View.GONE
        } else {
            binding.noLimitsText.visibility = View.GONE
            binding.appLimitsRecyclerView.visibility = View.VISIBLE
            appLimitsAdapter.submitList(currentLimits.toList())
        }
    }

    private fun getDayChip(day: DayOfWeek): Chip? {
        return when (day) {
            DayOfWeek.MONDAY -> binding.chipMonday
            DayOfWeek.TUESDAY -> binding.chipTuesday
            DayOfWeek.WEDNESDAY -> binding.chipWednesday
            DayOfWeek.THURSDAY -> binding.chipThursday
            DayOfWeek.FRIDAY -> binding.chipFriday
            DayOfWeek.SATURDAY -> binding.chipSaturday
            DayOfWeek.SUNDAY -> binding.chipSunday
        }
    }

    private fun getSelectedDays(): Set<DayOfWeek> {
        val selectedDays = mutableSetOf<DayOfWeek>()
        if (binding.chipMonday.isChecked) selectedDays.add(DayOfWeek.MONDAY)
        if (binding.chipTuesday.isChecked) selectedDays.add(DayOfWeek.TUESDAY)
        if (binding.chipWednesday.isChecked) selectedDays.add(DayOfWeek.WEDNESDAY)
        if (binding.chipThursday.isChecked) selectedDays.add(DayOfWeek.THURSDAY)
        if (binding.chipFriday.isChecked) selectedDays.add(DayOfWeek.FRIDAY)
        if (binding.chipSaturday.isChecked) selectedDays.add(DayOfWeek.SATURDAY)
        if (binding.chipSunday.isChecked) selectedDays.add(DayOfWeek.SUNDAY)
        return selectedDays
    }

    private fun saveRoutine() {
        val name = binding.routineNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Snackbar.make(binding.root, "Please enter a routine name", Snackbar.LENGTH_SHORT).show()
            return
        }

        val scheduleType = when (binding.scheduleTypeToggle.checkedButtonId) {
            R.id.daily_button -> RoutineSchedule.ScheduleType.DAILY
            R.id.weekly_button -> RoutineSchedule.ScheduleType.WEEKLY
            else -> RoutineSchedule.ScheduleType.MANUAL
        }

        val schedule = RoutineSchedule(
            type = scheduleType,
            timeHour = if (scheduleType != RoutineSchedule.ScheduleType.MANUAL) selectedTime.hour else null,
            timeMinute = if (scheduleType != RoutineSchedule.ScheduleType.MANUAL) selectedTime.minute else null,
            endTimeHour = if (scheduleType != RoutineSchedule.ScheduleType.MANUAL) selectedEndTime.hour else null,
            endTimeMinute = if (scheduleType != RoutineSchedule.ScheduleType.MANUAL) selectedEndTime.minute else null,
            daysOfWeek = if (scheduleType == RoutineSchedule.ScheduleType.WEEKLY) getSelectedDays() else emptySet()
        )

        if (scheduleType == RoutineSchedule.ScheduleType.WEEKLY && schedule.daysOfWeek.isEmpty()) {
            Snackbar.make(binding.root, "Please select at least one day", Snackbar.LENGTH_SHORT)
                .show()
            return
        }

        val routine = Routine(
            id = currentRoutine?.id ?: UUID.randomUUID().toString(),
            name = name,
            isEnabled = currentRoutine?.isEnabled ?: true,
            schedule = schedule,
            limits = currentLimits.toList()
        )

        if (currentRoutine == null) {
            RoutineManager.addRoutine(routine, this)
        } else {
            RoutineManager.updateRoutine(routine, this)
        }

        finishAfterTransition()
    }

    private fun deleteRoutine() {
        currentRoutine?.let { routine ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Routine")
                .setMessage("Are you sure you want to delete '${routine.name}'? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    RoutineManager.deleteRoutine(routine.id, this@CreateRoutineActivity)
                    finishAfterTransition()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

class AppLimitsAdapter(
    private val onRemove: (Routine.AppLimit) -> Unit
) : ListAdapter<Routine.AppLimit, AppLimitViewHolder>(AppLimitDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppLimitViewHolder {
        val binding =
            ItemAppLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppLimitViewHolder(binding, onRemove)
    }

    override fun onBindViewHolder(holder: AppLimitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class AppLimitViewHolder(
    private val binding: ItemAppLimitBinding,
    private val onRemove: (Routine.AppLimit) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(appLimit: Routine.AppLimit) {
        val context = binding.root.context
        val packageManager = context.packageManager

        try {
            val appInfo = packageManager.getApplicationInfo(appLimit.packageName, 0)
            binding.appIcon.setImageDrawable(packageManager.getApplicationIcon(appInfo))
            binding.appName.text = packageManager.getApplicationLabel(appInfo)
        } catch (_: Exception) {
            binding.appName.text = appLimit.packageName
        }

        binding.appLimit.text = formatTime(appLimit.limitMinutes)

        binding.removeButton.setOnClickListener {
            onRemove(appLimit)
        }
    }

    private fun formatTime(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0 -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}

class AppLimitDiffCallback : DiffUtil.ItemCallback<Routine.AppLimit>() {
    override fun areItemsTheSame(oldItem: Routine.AppLimit, newItem: Routine.AppLimit): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: Routine.AppLimit, newItem: Routine.AppLimit): Boolean {
        return oldItem == newItem
    }
}
