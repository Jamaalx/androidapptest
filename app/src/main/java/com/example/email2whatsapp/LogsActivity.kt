package com.example.email2whatsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.email2whatsapp.databinding.ActivityLogsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLogsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up RecyclerView
        binding.logsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Load logs
        loadLogs()
        
        // Set up filter dropdown
        setupFilterDropdown()
        
        // Set up clear logs button
        binding.clearLogsButton.setOnClickListener {
            clearLogs()
        }
    }
    
    private fun loadLogs() {
        // Get selected filter
        val filterPosition = binding.filterSpinner.selectedItemPosition
        val timeFilter = when (filterPosition) {
            0 -> 24 * 60 * 60 * 1000L // Last 24 hours
            1 -> 7 * 24 * 60 * 60 * 1000L // Last 7 days
            else -> Long.MAX_VALUE // All logs
        }
        
        // Get logs from shared preferences
        val logs = LogManager.getLogs(this, timeFilter)
        
        // Set up adapter
        val adapter = LogAdapter(logs)
        binding.logsRecyclerView.adapter = adapter
        
        // Show empty view if needed
        if (logs.isEmpty()) {
            binding.emptyLogsText.visibility = android.view.View.VISIBLE
            binding.logsRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyLogsText.visibility = android.view.View.GONE
            binding.logsRecyclerView.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun setupFilterDropdown() {
        // This would normally set up a spinner adapter
        // For simplicity, we'll just set a listener
        binding.filterSpinner.setOnItemSelectedListener { _, _, _, _ ->
            loadLogs()
        }
    }
    
    private fun clearLogs() {
        // Clear logs
        LogManager.clearLogs(this)
        
        // Reload logs (which will now be empty)
        loadLogs()
        
        // Show toast
        android.widget.Toast.makeText(this, "Logs cleared", android.widget.Toast.LENGTH_SHORT).show()
    }
}
