package com.example.email2whatsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.email2whatsapp.databinding.ActivityScheduleMessageBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class ScheduleMessageActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScheduleMessageBinding
    private var messageId: Int? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check if we're editing an existing message
        messageId = intent.getIntExtra("message_id", -1)
        if (messageId != -1) {
            loadMessage(messageId!!)
        }
        
        // Set up date/time picker
        binding.dateTimePickerButton.setOnClickListener {
            showDateTimePicker()
        }
        
        // Set up save button
        binding.saveButton.setOnClickListener {
            saveMessage()
        }
        
        // Set up cancel button
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadMessage(messageId: Int) {
        lifecycleScope.launch {
            try {
                val messageDao = AppDatabase.getInstance(this@ScheduleMessageActivity).messageDao()
                val message = messageDao.getMessageById(messageId)
                
                binding.recipientInput.setText(message.recipient)
                binding.messageInput.setText(message.message)
                binding.askBeforeSendingSwitch.isChecked = message.askBeforeSending
                
                // Format and display the date/time
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = message.sendTime
                updateDateTimeDisplay(calendar)
                
                // Update title to indicate we're editing
                title = "Edit Scheduled Message"
            } catch (e: Exception) {
                Toast.makeText(this@ScheduleMessageActivity, 
                    "Error loading message: ${e.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveMessage() {
        val recipient = binding.recipientInput.text.toString()
        val messageText = binding.messageInput.text.toString()
        val askBeforeSending = binding.askBeforeSendingSwitch.isChecked
        
        // Validate inputs
        if (recipient.isEmpty()) {
            binding.recipientInput.error = "Recipient is required"
            return
        }
        
        if (messageText.isEmpty()) {
            binding.messageInput.error = "Message is required"
            return
        }
        
        // Get the selected date/time
        val calendar = Calendar.getInstance()
        if (binding.dateTimeText.text.toString().isEmpty()) {
            binding.dateTimeText.error = "Send time is required"
            return
        }
        
        lifecycleScope.launch {
            try {
                val messageDao = AppDatabase.getInstance(this@ScheduleMessageActivity).messageDao()
                
                val message = ScheduledMessage(
                    id = messageId ?: 0,
                    recipient = recipient,
                    message = messageText,
                    sendTime = calendar.timeInMillis,
                    askBeforeSending = askBeforeSending
                )
                
                if (messageId == null || messageId == -1) {
                    // Insert new message
                    val id = messageDao.insert(message)
                    
                    // Schedule the message
                    ScheduleManager.scheduleMessage(this@ScheduleMessageActivity, message.copy(id = id.toInt()))
                    
                    // Log the action
                    LogManager.logAction(this@ScheduleMessageActivity, 
                        "New message scheduled for ${Date(message.sendTime)}")
                } else {
                    // Update existing message
                    messageDao.update(message)
                    
                    // Reschedule the message
                    ScheduleManager.cancelScheduledMessage(this@ScheduleMessageActivity, message.id)
                    ScheduleManager.scheduleMessage(this@ScheduleMessageActivity, message)
                    
                    // Log the action
                    LogManager.logAction(this@ScheduleMessageActivity, 
                        "Message updated, rescheduled for ${Date(message.sendTime)}")
                }
                
                Toast.makeText(this@ScheduleMessageActivity, 
                    "Message scheduled successfully", 
                    Toast.LENGTH_SHORT).show()
                    
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ScheduleMessageActivity, 
                    "Error saving message: ${e.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDateTimePicker() {
        // This would normally show a date/time picker dialog
        // For simplicity, we'll just set a time 1 hour from now
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR, 1)
        updateDateTimeDisplay(calendar)
    }
    
    private fun updateDateTimeDisplay(calendar: Calendar) {
        val dateFormat = android.text.format.DateFormat.getDateTimeInstance(
            android.text.format.DateFormat.MEDIUM, 
            android.text.format.DateFormat.SHORT
        )
        binding.dateTimeText.text = dateFormat.format(calendar.time)
    }
}
