package com.example.email2whatsapp

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    
    private const val LOG_PREFS = "log_prefs"
    private const val LOG_COUNT_KEY = "log_count"
    
    fun logAction(context: Context, message: String) {
        val sharedPrefs = context.getSharedPreferences(LOG_PREFS, Context.MODE_PRIVATE)
        val logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)
        
        // Create log entry with timestamp
        val timestamp = System.currentTimeMillis()
        val logEntry = "$timestamp:$message"
        
        // Store log entry
        sharedPrefs.edit().apply {
            putString("log_$logCount", logEntry)
            putInt(LOG_COUNT_KEY, logCount + 1)
            apply()
        }
        
        // Trim logs if we have too many (keep last 1000)
        if (logCount > 1000) {
            trimLogs(sharedPrefs)
        }
    }
    
    fun getLogs(context: Context, timeFilter: Long = Long.MAX_VALUE): List<LogEntry> {
        val sharedPrefs = context.getSharedPreferences(LOG_PREFS, Context.MODE_PRIVATE)
        val logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)
        val logs = mutableListOf<LogEntry>()
        
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - timeFilter
        
        // Retrieve all logs within time filter
        for (i in 0 until logCount) {
            val logString = sharedPrefs.getString("log_$i", null) ?: continue
            val parts = logString.split(":", limit = 2)
            if (parts.size != 2) continue
            
            val timestamp = parts[0].toLongOrNull() ?: continue
            if (timestamp < cutoffTime) continue
            
            val message = parts[1]
            logs.add(LogEntry(timestamp, message))
        }
        
        // Sort by timestamp (newest first)
        return logs.sortedByDescending { it.timestamp }
    }
    
    fun clearLogs(context: Context) {
        val sharedPrefs = context.getSharedPreferences(LOG_PREFS, Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
    }
    
    private fun trimLogs(sharedPrefs: SharedPreferences) {
        val logCount = sharedPrefs.getInt(LOG_COUNT_KEY, 0)
        val editor = sharedPrefs.edit()
        
        // Keep only the last 1000 logs
        val keepCount = 1000
        val startIndex = logCount - keepCount
        
        // Copy logs to new indices
        for (i in 0 until keepCount) {
            val oldIndex = startIndex + i
            val newIndex = i
            val logEntry = sharedPrefs.getString("log_$oldIndex", null)
            
            if (logEntry != null) {
                editor.putString("log_$newIndex", logEntry)
            }
            
            // Remove old entry
            editor.remove("log_$oldIndex")
        }
        
        // Update log count
        editor.putInt(LOG_COUNT_KEY, keepCount)
        editor.apply()
    }
    
    data class LogEntry(val timestamp: Long, val message: String) {
        fun getFormattedTime(): String {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }
}
