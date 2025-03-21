package com.example.email2whatsapp

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object GmailApiHelper {
    
    private const val TAG = "GmailApiHelper"
    
    suspend fun fetchEmails(context: Context, query: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            val service = GmailCredentialManager.buildGmailService(context)
                ?: throw Exception("Gmail service not initialized")
                
            return@withContext fetchEmailsWithBackoff(service, query)
        } catch (e: Exception) {
            LogManager.logAction(context, "Error fetching emails: ${e.message}")
            Log.e(TAG, "Error fetching emails", e)
            return@withContext emptyList()
        }
    }
    
    private suspend fun fetchEmailsWithBackoff(service: Gmail, query: String): List<Message> {
        repeat(5) { attempt ->
            try {
                val listResponse = service.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(10L)
                    .execute()
                    
                return listResponse.messages ?: emptyList()
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 429 && attempt < 4) {
                    // Exponential backoff for rate limiting
                    val delayMs = (1 shl attempt) * 60 * 1000L // 1, 2, 4, 8 minutes
                    delay(delayMs)
                } else {
                    throw e
                }
            }
        }
        
        throw Exception("Failed to fetch emails after multiple attempts")
    }
    
    suspend fun getFullMessage(context: Context, messageId: String): Message? = withContext(Dispatchers.IO) {
        try {
            val service = GmailCredentialManager.buildGmailService(context)
                ?: throw Exception("Gmail service not initialized")
                
            return@withContext service.users().messages().get("me", messageId)
                .setFormat("full")
                .execute()
        } catch (e: Exception) {
            LogManager.logAction(context, "Error getting message details: ${e.message}")
            Log.e(TAG, "Error getting message details", e)
            return@withContext null
        }
    }
    
    suspend fun getAttachment(
        context: Context, 
        messageId: String, 
        attachmentId: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val service = GmailCredentialManager.buildGmailService(context)
                ?: throw Exception("Gmail service not initialized")
                
            val attachment = service.users().messages().attachments()
                .get("me", messageId, attachmentId)
                .execute()
                
            return@withContext if (attachment.data != null) {
                java.util.Base64.getUrlDecoder().decode(attachment.data)
            } else {
                null
            }
        } catch (e: Exception) {
            LogManager.logAction(context, "Error downloading attachment: ${e.message}")
            Log.e(TAG, "Error downloading attachment", e)
            return@withContext null
        }
    }
    
    suspend fun sendEmail(
        context: Context,
        to: String,
        subject: String,
        bodyText: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = GmailCredentialManager.buildGmailService(context)
                ?: throw Exception("Gmail service not initialized")
                
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            val email = MimeMessage(session)
            
            email.setFrom(InternetAddress("me"))
            email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
            email.subject = subject
            email.setText(bodyText)
            
            val buffer = ByteArrayOutputStream()
            email.writeTo(buffer)
            val bytes = buffer.toByteArray()
            val encodedEmail = java.util.Base64.getUrlEncoder().encode(bytes)
            
            val message = Message()
            message.raw = String(encodedEmail)
            
            service.users().messages().send("me", message).execute()
            
            LogManager.logAction(context, "Email sent to: $to")
            return@withContext true
        } catch (e: Exception) {
            LogManager.logAction(context, "Error sending email: ${e.message}")
            Log.e(TAG, "Error sending email", e)
            return@withContext false
        }
    }
    
    fun buildSearchQuery(
        senderEmail: String?,
        subjectKeywords: String?,
        bodyKeywords: String?
    ): String {
        val queryParts = mutableListOf<String>()
        
        if (!senderEmail.isNullOrBlank()) {
            queryParts.add("from:$senderEmail")
        }
        
        if (!subjectKeywords.isNullOrBlank()) {
            queryParts.add("subject:$subjectKeywords")
        }
        
        if (!bodyKeywords.isNullOrBlank()) {
            queryParts.add(bodyKeywords)
        }
        
        queryParts.add("in:inbox -in:trash")
        
        return queryParts.joinToString(" ")
    }
}
