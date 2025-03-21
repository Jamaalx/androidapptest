package com.example.email2whatsapp

import android.content.Context
import android.util.Log
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.MessagePartBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

class EmailProcessor(private val context: Context) {
    
    private val TAG = "EmailProcessor"
    
    suspend fun processMatchingEmails(query: String, whatsappRecipient: String): Int = withContext(Dispatchers.IO) {
        try {
            // Log the action
            LogManager.logAction(context, "Starting email processing with query: $query")
            
            // Fetch matching emails
            val messages = GmailApiHelper.fetchEmails(context, query)
            
            if (messages.isEmpty()) {
                LogManager.logAction(context, "No matching emails found")
                return@withContext 0
            }
            
            LogManager.logAction(context, "Found ${messages.size} matching emails")
            
            // Process each email
            var successCount = 0
            for (message in messages) {
                val success = processEmail(message.id, whatsappRecipient)
                if (success) successCount++
            }
            
            return@withContext successCount
        } catch (e: Exception) {
            LogManager.logAction(context, "Error processing emails: ${e.message}")
            Log.e(TAG, "Error processing emails", e)
            return@withContext 0
        }
    }
    
    private suspend fun processEmail(messageId: String, whatsappRecipient: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get full message
            val fullMessage = GmailApiHelper.getFullMessage(context, messageId) ?: return@withContext false
            
            // Extract headers
            val headers = fullMessage.payload.headers
            val from = headers.find { it.name == "From" }?.value ?: "Unknown Sender"
            val subject = headers.find { it.name == "Subject" }?.value ?: "No Subject"
            
            // Extract text content and attachments
            val extractionResult = extractContentAndAttachments(fullMessage.payload)
            
            // Prepare message text
            val messageText = """
                From: $from
                Subject: $subject
                
                ${extractionResult.textContent}
            """.trimIndent()
            
            // Send to WhatsApp
            val success = WhatsAppSender.sendToWhatsApp(
                context,
                whatsappRecipient,
                messageText,
                extractionResult.attachments
            )
            
            if (success) {
                LogManager.logAction(context, "Email processed and sent to WhatsApp: $subject")
            } else {
                LogManager.logAction(context, "Failed to send email content to WhatsApp: $subject")
            }
            
            // Clean up attachments
            extractionResult.attachments.forEach { it.delete() }
            
            return@withContext success
        } catch (e: Exception) {
            LogManager.logAction(context, "Error processing email $messageId: ${e.message}")
            Log.e(TAG, "Error processing email", e)
            return@withContext false
        }
    }
    
    private suspend fun extractContentAndAttachments(
        messagePart: MessagePart
    ): ExtractionResult = withContext(Dispatchers.IO) {
        var textContent = ""
        val attachments = mutableListOf<File>()
        
        // Check if this part is plain text
        if (messagePart.mimeType == "text/plain" && messagePart.body?.data != null) {
            val decodedBytes = Base64.getUrlDecoder().decode(messagePart.body.data)
            textContent = String(decodedBytes)
        }
        
        // Check if this part has attachments
        if (messagePart.filename?.isNotEmpty() == true && messagePart.body?.attachmentId != null) {
            val attachmentData = GmailApiHelper.getAttachment(
                context,
                messagePart.partId,
                messagePart.body.attachmentId
            )
            
            if (attachmentData != null) {
                val file = File(context.filesDir, messagePart.filename)
                FileOutputStream(file).use { it.write(attachmentData) }
                attachments.add(file)
                
                // Check file size against WhatsApp limits
                if (file.length() > 100 * 1024 * 1024) { // 100 MB
                    LogManager.logAction(
                        context,
                        "Attachment ${messagePart.filename} exceeds WhatsApp size limit"
                    )
                }
            }
        }
        
        // Recursively process child parts
        messagePart.parts?.forEach { part ->
            val result = extractContentAndAttachments(part)
            if (textContent.isEmpty()) {
                textContent = result.textContent
            } else if (result.textContent.isNotEmpty()) {
                textContent += "\n\n" + result.textContent
            }
            attachments.addAll(result.attachments)
        }
        
        return@withContext ExtractionResult(textContent, attachments)
    }
    
    data class ExtractionResult(
        val textContent: String,
        val attachments: List<File>
    )
}
