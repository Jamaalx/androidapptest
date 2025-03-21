package com.example.email2whatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SendMessageReceiver : BroadcastReceiver() {
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "scheduled_messages"
        private const val NOTIFICATION_ID = 1001
        private const val CONFIRM_ACTION = "com.example.email2whatsapp.CONFIRM_SEND"
        private const val CANCEL_ACTION = "com.example.email2whatsapp.CANCEL_SEND"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        // Create a coroutine scope
        val scope = CoroutineScope(Dispatchers.IO)
        
        when (intent.action) {
            CONFIRM_ACTION -> {
                // User confirmed sending from notification
                val messageId = intent.getIntExtra("messageId", -1)
                if (messageId != -1) {
                    scope.launch {
                        sendScheduledMessage(context, messageId)
                    }
                }
                
                // Cancel the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
            }
            
            CANCEL_ACTION -> {
                // User cancelled sending from notification
                val messageId = intent.getIntExtra("messageId", -1)
                if (messageId != -1) {
                    LogManager.logAction(context, "Scheduled message sending cancelled by user: ID=$messageId")
                }
                
                // Cancel the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
            }
            
            else -> {
                // Regular alarm trigger
                val messageId = intent.getIntExtra("messageId", -1)
                if (messageId != -1) {
                    scope.launch {
                        handleScheduledMessage(context, messageId)
                    }
                }
            }
        }
    }
    
    private suspend fun handleScheduledMessage(context: Context, messageId: Int) {
        try {
            // Get message from database
            val messageDao = AppDatabase.getInstance(context).messageDao()
            val message = messageDao.getMessageById(messageId)
            
            if (message.askBeforeSending) {
                // Show confirmation notification
                showConfirmationNotification(context, message)
                
                // Log the action
                LogManager.logAction(context, "Showing confirmation for scheduled message: ID=${message.id}")
            } else {
                // Send message directly
                sendScheduledMessage(context, messageId)
            }
        } catch (e: Exception) {
            LogManager.logAction(context, "Error handling scheduled message: ${e.message}")
        }
    }
    
    private suspend fun sendScheduledMessage(context: Context, messageId: Int) {
        try {
            // Get message from database
            val messageDao = AppDatabase.getInstance(context).messageDao()
            val message = messageDao.getMessageById(messageId)
            
            // Send via WhatsApp
            val success = WhatsAppSender.sendToWhatsApp(
                context,
                message.recipient,
                message.message,
                emptyList() // No attachments for scheduled messages
            )
            
            if (success) {
                // Log the action
                LogManager.logAction(context, "Scheduled message sent: ID=${message.id}")
                
                // Delete the message from database (optional, could keep history)
                // messageDao.delete(message)
            } else {
                // Log the failure
                LogManager.logAction(context, "Failed to send scheduled message: ID=${message.id}")
                
                // Could implement retry logic here
            }
        } catch (e: Exception) {
            LogManager.logAction(context, "Error sending scheduled message: ${e.message}")
        }
    }
    
    private fun showConfirmationNotification(context: Context, message: ScheduledMessage) {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Scheduled Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create confirm intent
        val confirmIntent = Intent(context, SendMessageReceiver::class.java).apply {
            action = CONFIRM_ACTION
            putExtra("messageId", message.id)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create cancel intent
        val cancelIntent = Intent(context, SendMessageReceiver::class.java).apply {
            action = CANCEL_ACTION
            putExtra("messageId", message.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create content intent (opens the app)
        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Scheduled WhatsApp Message")
            .setContentText("Ready to send message to ${message.recipient}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Message: ${message.message}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Send Now", confirmPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setAutoCancel(false)
            .build()
        
        // Show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
