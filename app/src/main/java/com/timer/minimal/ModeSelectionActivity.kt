package com.timer.minimal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class ModeSelectionActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_MODE = "timer_mode"
        const val MODE_ROUTINE = "routine"
        const val MODE_DEATH_BURPEES = "death_burpees"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_selector)
        
        val cardRoutine = findViewById<MaterialCardView>(R.id.cardRoutine)
        val cardDeathBurpees = findViewById<MaterialCardView>(R.id.cardDeathBurpees)
        
        cardRoutine.setOnClickListener {
            launchTimer(MODE_ROUTINE)
        }
        
        cardDeathBurpees.setOnClickListener {
            launchTimer(MODE_DEATH_BURPEES)
        }
    }
    
    private fun launchTimer(mode: String) {
        val targetActivity = when (mode) {
            MODE_DEATH_BURPEES -> DeathBurpeesActivity::class.java
            else -> MainActivity::class.java
        }
        
        val intent = Intent(this, targetActivity).apply {
            putExtra(EXTRA_MODE, mode)
        }
        startActivity(intent)
    }
}
