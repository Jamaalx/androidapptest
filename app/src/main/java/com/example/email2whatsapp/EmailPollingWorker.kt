package com.example.email2whatsapp

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EmailPollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "email_polling_work"
        
        fun scheduleEmailPolling(context: Context) {
            // Get polling interval from preferences (default 15 minutes)
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val intervalMinutes = sharedPrefs.getInt("polling_interval", 15)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val workRequest = PeriodicWorkRequestBuilder<EmailPollingWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            // Log the action
            LogManager.logAction(context, "Email polling scheduled every $intervalMinutes minutes")
        }
        
        fun cancelEmailPolling(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            
            // Log the action
            LogManager.logAction(context, "Email polling cancelled")
        }
        
        fun isEmailPollingActive(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
                
            return workInfos.any { !it.state.isFinished }
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Log the action
            LogManager.logAction(applicationContext, "Starting email polling")
            
            // Get email settings from preferences
            val sharedPrefs = applicationContext.getSharedPreferences("email_settings", Context.MODE_PRIVATE)
            val senderEmail = sharedPrefs.getString("sender_email", "") ?: ""
            val subjectKeywords = sharedPrefs.getString("subject_keywords", "") ?: ""
            val bodyKeywords = sharedPrefs.getString("body_keywords", "") ?: ""
            val whatsappRecipient = sharedPrefs.getString("whatsapp_recipient", "") ?: ""
            
            if (senderEmail.isEmpty() || whatsappRecipient.isEmpty()) {
                LogManager.logAction(applicationContext, "Email polling skipped: Missing settings")
                return@withContext Result.failure()
            }
            
            // Build search query
            val query = GmailApiHelper.buildSearchQuery(senderEmail, subjectKeywords, bodyKeywords)
            
            // Process matching emails
            val emailProcessor = EmailProcessor(applicationContext)
            val processedCount = emailProcessor.processMatchingEmails(query, whatsappRecipient)
            
            // Log results
            if (processedCount > 0) {
                LogManager.logAction(applicationContext, 
                    "Email polling completed: Processed $processedCount emails")
            } else {
                LogManager.logAction(applicationContext, 
                    "Email polling completed: No matching emails found or processed")
            }
            
            return@withContext Result.success()
        } catch (e: Exception) {
            LogManager.logAction(applicationContext, "Email polling failed: ${e.message}")
            
            // Determine if we should retry
            val shouldRetry = e.message?.contains("network", ignoreCase = true) == true ||
                              e.message?.contains("timeout", ignoreCase = true) == true
            
            return@withContext if (shouldRetry) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
