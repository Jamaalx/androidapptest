package com.example.email2whatsapp

import android.content.Context
import androidx.work.*
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit

class EmailCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "email_check_work"
        
        fun scheduleEmailChecking(context: Context) {
            // Get polling interval from preferences (default 15 minutes)
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val intervalMinutes = sharedPrefs.getInt("polling_interval", 15)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val workRequest = PeriodicWorkRequestBuilder<EmailCheckWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            // Log the action
            LogManager.logAction(context, "Email checking scheduled every $intervalMinutes minutes")
        }
        
        fun cancelEmailChecking(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            
            // Log the action
            LogManager.logAction(context, "Email checking cancelled")
        }
        
        fun isEmailCheckingActive(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
                
            return workInfos.any { !it.state.isFinished }
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Log the action
            LogManager.logAction(applicationContext, "Starting email check")
            
            // Get email settings from preferences
            val sharedPrefs = applicationContext.getSharedPreferences("email_settings", Context.MODE_PRIVATE)
            val senderEmail = sharedPrefs.getString("sender_email", "") ?: ""
            val subjectKeywords = sharedPrefs.getString("subject_keywords", "") ?: ""
            val bodyKeywords = sharedPrefs.getString("body_keywords", "") ?: ""
            val whatsappRecipient = sharedPrefs.getString("whatsapp_recipient", "") ?: ""
            
            if (senderEmail.isEmpty() || whatsappRecipient.isEmpty()) {
                LogManager.logAction(applicationContext, "Email check skipped: Missing settings")
                return@withContext Result.failure()
            }
            
            // Get Gmail credential
            val credential = GmailCredentialManager.getCredential(applicationContext)
                ?: return@withContext Result.retry()
            
            // Build Gmail service
            val service = Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Email2WhatsApp").build()
            
            // Build query
            val queryParts = mutableListOf<String>()
            if (senderEmail.isNotEmpty()) queryParts.add("from:$senderEmail")
            if (subjectKeywords.isNotEmpty()) queryParts.add("subject:$subjectKeywords")
            if (bodyKeywords.isNotEmpty()) queryParts.add(bodyKeywords)
            queryParts.add("in:inbox -in:trash")
            
            val query = queryParts.joinToString(" ")
            
            // Fetch emails with backoff
            val messages = fetchEmailsWithBackoff(service, query)
            
            if (messages.isEmpty()) {
                LogManager.logAction(applicationContext, "Email check completed: No matching emails found")
                return@withContext Result.success()
            }
            
            // Process each matching email
            var successCount = 0
            for (message in messages) {
                val result = processMessage(service, message.id, whatsappRecipient)
                if (result) successCount++
            }
            
            LogManager.logAction(applicationContext, 
                "Email check completed: Found ${messages.size} emails, processed $successCount successfully")
            
            return@withContext Result.success()
        } catch (e: Exception) {
            LogManager.logAction(applicationContext, "Email check failed: ${e.message}")
            return@withContext Result.failure()
        }
    }
    
    private suspend fun fetchEmailsWithBackoff(service: Gmail, query: String): List<Message> {
        repeat(5) { attempt ->
            try {
                return service.users().messages().list("me")
                    .setQ(query)
                    .execute()
                    .messages ?: emptyList()
            } catch (e: Exception) {
                if (attempt < 4) {
                    // Exponential backoff
                    val delayMs = (1 shl attempt) * 60 * 1000L // 1, 2, 4, 8 minutes
                    withContext(Dispatchers.IO) {
                        Thread.sleep(delayMs)
                    }
                } else {
                    throw e
                }
            }
        }
        return emptyList()
    }
    
    private suspend fun processMessage(service: Gmail, messageId: String, whatsappRecipient: String): Boolean {
        try {
            // Get full message
            val message = service.users().messages().get("me", messageId).execute()
            val payload = message.payload
            
            // Extract text content
            var textContent = ""
            payload.parts?.forEach { part ->
                if (part.mimeType == "text/plain") {
                    val body = part.body
                    if (body.data != null) {
                        textContent = String(Base64.getUrlDecoder().decode(body.data))
                    }
                }
            }
            
            // Extract attachments
            val attachmentFiles = mutableListOf<File>()
            payload.parts?.forEach { part ->
                if (part.filename?.isNotEmpty() == true) {
                    val attachmentId = part.body.attachmentId
                    if (attachmentId != null) {
                        val attachment = service.users().messages().attachments()
                            .get("me", messageId, attachmentId).execute()
                        
                        val fileData = Base64.getUrlDecoder().decode(attachment.data)
                        val file = File(applicationContext.filesDir, part.filename)
                        FileOutputStream(file).use { it.write(fileData) }
                        attachmentFiles.add(file)
                        
                        // Check file size against WhatsApp limits
                        if (file.length() > 100 * 1024 * 1024) { // 100 MB
                            LogManager.logAction(applicationContext, 
                                "Attachment ${part.filename} exceeds WhatsApp size limit")
                            // Could implement compression here
                        }
                    }
                }
            }
            
            // Send to WhatsApp
            val success = WhatsAppSender.sendToWhatsApp(
                applicationContext, 
                whatsappRecipient,
                "Email from: ${message.payload.headers.find { it.name == "From" }?.value}\n" +
                "Subject: ${message.payload.headers.find { it.name == "Subject" }?.value}\n\n" +
                textContent,
                attachmentFiles
            )
            
            // Clean up attachments after sending
            attachmentFiles.forEach { it.delete() }
            
            return success
        } catch (e: Exception) {
            LogManager.logAction(applicationContext, "Failed to process message: ${e.message}")
            return false
        }
    }
}
