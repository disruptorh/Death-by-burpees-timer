package com.timer.minimal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date

/** Read-only log of completed sessions with a running total and current streak. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var summary: TextView
    private lateinit var empty: TextView
    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        summary = findViewById(R.id.historySummary)
        empty = findViewById(R.id.historyEmpty)
        list = findViewById(R.id.historyList)

        val historyManager = HistoryManager(this)
        val records = historyManager.load().sortedByDescending { it.startedAtEpochMs }

        if (records.isEmpty()) {
            summary.text = getString(R.string.history_streak, historyManager.streakDays())
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        val totalSec = records.sumOf { it.durationSec }
        summary.text = buildString {
            append(getString(R.string.history_sessions, records.size, formatHoursMinutes(totalSec)))
            if (historyManager.streakDays() > 0) {
                append("\n")
                append(getString(R.string.history_streak, historyManager.streakDays()))
            }
        }
        empty.visibility = View.GONE
        list.visibility = View.VISIBLE
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = HistoryAdapter(records)
    }

    private fun formatHoursMinutes(totalSec: Int): String {
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        return when {
            hours > 0 -> String.format(java.util.Locale.getDefault(), "%dh %02dm", hours, minutes)
            else -> String.format(java.util.Locale.getDefault(), "%d min", minutes)
        }
    }

    private class HistoryAdapter(
        private val records: List<SessionRecord>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.historyIcon)
            val title: TextView = view.findViewById(R.id.historyEntryTitle)
            val date: TextView = view.findViewById(R.id.historyEntryDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_history, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = records[position]
            val context = holder.itemView.context
            holder.title.text = when (record.mode) {
                TimerMode.ROUTINE -> context.getString(R.string.history_entry_routine, record.durationSec)
                TimerMode.DEATH_BURPEES ->
                    context.getString(R.string.history_entry_burpees, (record.durationSec / 60).coerceAtLeast(1))
            }
            holder.date.text = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT
            ).format(Date(record.startedAtEpochMs))
            holder.icon.setImageResource(
                when (record.mode) {
                    TimerMode.ROUTINE -> R.drawable.ic_dumbbell
                    TimerMode.DEATH_BURPEES -> R.drawable.ic_skull
                }
            )
        }

        override fun getItemCount(): Int = records.size
    }
}
