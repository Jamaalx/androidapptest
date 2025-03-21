package com.example.email2whatsapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.email2whatsapp.databinding.ItemScheduledMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScheduledMessageAdapter(
    private val messages: List<ScheduledMessage>,
    private val onMessageClick: (ScheduledMessage) -> Unit
) : RecyclerView.Adapter<ScheduledMessageAdapter.MessageViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemScheduledMessageBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return MessageViewHolder(binding, onMessageClick)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount(): Int = messages.size
    
    class MessageViewHolder(
        private val binding: ItemScheduledMessageBinding,
        private val onMessageClick: (ScheduledMessage) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ScheduledMessage) {
            binding.recipientText.text = "To: ${message.recipient}"
            
            // Truncate message if too long
            val messagePreview = if (message.message.length > 50) {
                message.message.substring(0, 47) + "..."
            } else {
                message.message
            }
            binding.messagePreviewText.text = messagePreview
            
            // Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.sendTimeText.text = "Time: ${dateFormat.format(Date(message.sendTime))}"
            
            // Show ask before sending indicator if enabled
            binding.askBeforeSendingIndicator.visibility = 
                if (message.askBeforeSending) View.VISIBLE else View.GONE
            
            // Set click listener
            binding.root.setOnClickListener {
                onMessageClick(message)
            }
            
            // Set edit button click listener
            binding.editButton.setOnClickListener {
                onMessageClick(message)
            }
            
            // Set delete button click listener
            binding.deleteButton.setOnClickListener {
                // This would normally show a confirmation dialog
                // For simplicity, we'll just call a method to delete the message
                val context = binding.root.context
                deleteMessage(context, message)
            }
        }
        
        private fun deleteMessage(context: Context, message: ScheduledMessage) {
            // Delete from database and cancel scheduled alarm
            Thread {
                try {
                    // Delete from database
                    val messageDao = AppDatabase.getInstance(context).messageDao()
                    messageDao.delete(message)
                    
                    // Cancel scheduled alarm
                    ScheduleManager.cancelScheduledMessage(context, message.id)
                    
                    // Log the action
                    LogManager.logAction(context, "Scheduled message deleted: ${message.id}")
                    
                    // Refresh the list (this would normally be done via LiveData)
                    // For simplicity, we'll just show a toast
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            context,
                            "Message deleted",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            context,
                            "Error deleting message: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.start()
        }
    }
}
