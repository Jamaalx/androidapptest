package com.example.email2whatsapp

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Date

object ScheduleManager {
    
    fun scheduleMessage(context: Context, message: ScheduledMessage) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SendMessageReceiver::class.java).apply {
            putExtra("messageId", message.id)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            message.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the alarm
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            message.sendTime,
            pendingIntent
        )
        
        // Log the action
        LogManager.logAction(
            context,
            "Message scheduled: ID=${message.id}, Time=${Date(message.sendTime)}"
        )
    }
    
    fun cancelScheduledMessage(context: Context, messageId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SendMessageReceiver::class.java).apply {
            putExtra("messageId", messageId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            messageId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            // Log the action
            LogManager.logAction(context, "Scheduled message cancelled: ID=$messageId")
        }
    }
}
