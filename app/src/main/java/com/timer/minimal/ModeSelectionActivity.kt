package com.timer.minimal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Entry point: pick a saved routine, or one of the two manual modes.
 */
class ModeSelectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRESET_ID = "preset_id"
        const val EXTRA_MODE = "timer_mode"
        const val MODE_ROUTINE = "routine"
        const val MODE_DEATH_BURPEES = "death_burpees"
    }

    private lateinit var presetManager: PresetManager
    private lateinit var presetAdapter: PresetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selector)

        presetManager = PresetManager(this)

        findViewById<MaterialCardView>(R.id.cardRoutine).setOnClickListener {
            launchTimer(MainActivity::class.java, MODE_ROUTINE)
        }
        findViewById<MaterialCardView>(R.id.cardDeathBurpees).setOnClickListener {
            launchTimer(DeathBurpeesActivity::class.java, MODE_DEATH_BURPEES)
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        presetAdapter = PresetAdapter(
            onPresetClick = { preset -> launchPreset(preset) },
            onDeleteClick = { preset ->
                presetManager.delete(preset.id)
                presetAdapter.submit(presetManager.listAll())
            }
        )
        findViewById<RecyclerView>(R.id.presetList).apply {
            layoutManager = LinearLayoutManager(this@ModeSelectionActivity)
            adapter = presetAdapter
            isNestedScrollingEnabled = false
        }
        presetAdapter.submit(presetManager.listAll())

        // Launched from a notification or a preset shortcut.
        intent?.getStringExtra(EXTRA_PRESET_ID)?.let { presetId ->
            presetManager.findById(presetId)?.let { launchPreset(it) }
        }
    }

    private fun launchPreset(preset: Preset) {
        val activity = when (preset.mode) {
            TimerMode.ROUTINE -> MainActivity::class.java
            TimerMode.DEATH_BURPEES -> DeathBurpeesActivity::class.java
        }
        startActivity(
            Intent(this, activity).apply {
                putExtra(EXTRA_PRESET_ID, preset.id)
                putExtra(EXTRA_MODE, preset.mode.name)
            }
        )
    }

    private fun launchTimer(target: Class<*>, mode: String) {
        startActivity(
            Intent(this, target).apply { putExtra(EXTRA_MODE, mode) }
        )
    }

    private class PresetAdapter(
        private val onPresetClick: (Preset) -> Unit,
        private val onDeleteClick: (Preset) -> Unit
    ) : RecyclerView.Adapter<PresetAdapter.ViewHolder>() {

        private var items: List<Preset> = emptyList()

        fun submit(presets: List<Preset>) {
            items = presets
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.presetIcon)
            val name: TextView = view.findViewById(R.id.presetName)
            val summary: TextView = view.findViewById(R.id.presetSummary)
            val delete: ImageButton = view.findViewById(R.id.presetDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_preset, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val preset = items[position]
            val context = holder.itemView.context
            val isBuiltIn = preset.id.startsWith("builtin_")

            holder.name.text = preset.name
            holder.summary.text = when (preset.mode) {
                TimerMode.ROUTINE -> context.getString(
                    R.string.preset_routine_summary,
                    preset.workDurationSec,
                    preset.restDurationSec,
                    preset.totalSets
                )
                TimerMode.DEATH_BURPEES ->
                    context.getString(R.string.preset_burpees_summary, preset.inputMinutes)
            }
            holder.icon.setImageResource(
                when (preset.mode) {
                    TimerMode.ROUTINE -> R.drawable.ic_dumbbell
                    TimerMode.DEATH_BURPEES -> R.drawable.ic_skull
                }
            )
            holder.itemView.contentDescription =
                context.getString(R.string.cd_mode_card, preset.name)

            holder.delete.visibility = if (isBuiltIn) View.GONE else View.VISIBLE
            holder.delete.setOnClickListener { onDeleteClick(preset) }
            holder.itemView.setOnClickListener { onPresetClick(preset) }
        }

        override fun getItemCount(): Int = items.size
    }
}
