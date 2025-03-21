package com.example.email2whatsapp

import android.content.Context
import android.net.Uri
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object WhatsAppSender {
    
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 5 * 60 * 1000L // 5 minutes
    
    suspend fun sendToWhatsApp(
        context: Context,
        recipient: String,
        message: String,
        attachments: List<File>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get ChatAPI credentials from secure storage
            val securePrefs = SecurePreferencesManager.getEncryptedSharedPreferences(context)
            val apiToken = securePrefs.getString("chat_api_token", "") ?: ""
            val instanceId = securePrefs.getString("chat_api_instance", "") ?: ""
            
            if (apiToken.isEmpty() || instanceId.isEmpty()) {
                // Try using Accessibility API as fallback
                return@withContext sendViaAccessibilityService(context, recipient, message, attachments)
            }
            
            // First send text message
            var success = sendTextMessage(instanceId, apiToken, recipient, message)
            
            // Then send each attachment
            for (file in attachments) {
                // Upload file to Firebase Storage to get public URL
                val fileUrl = uploadFileToFirebase(file)
                
                // Send file via ChatAPI
                val attachmentSuccess = sendFileMessage(
                    instanceId, 
                    apiToken, 
                    recipient, 
                    fileUrl, 
                    file.name
                )
                
                success = success && attachmentSuccess
            }
            
            // Log the action
            if (success) {
                LogManager.logAction(context, "Message sent to WhatsApp: $recipient")
            } else {
                LogManager.logAction(context, "Failed to send message to WhatsApp: $recipient")
            }
            
            return@withContext success
        } catch (e: Exception) {
            LogManager.logAction(context, "WhatsApp sending error: ${e.message}")
            return@withContext false
        }
    }
    
    private suspend fun sendTextMessage(
        instanceId: String,
        apiToken: String,
        recipient: String,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                
                val json = JSONObject().apply {
                    put("phone", recipient)
                    put("body", message)
                }
                
                val requestBody = json.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                
                val request = Request.Builder()
                    .url("https://api.chat-api.com/instance$instanceId/sendMessage?token=$apiToken")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    return@withContext true
                }
                
                if (attempt < MAX_RETRIES - 1) {
                    // Wait before retry
                    Thread.sleep(RETRY_DELAY_MS)
                }
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES - 1) {
                    // Wait before retry
                    Thread.sleep(RETRY_DELAY_MS)
                } else {
                    throw e
                }
            }
        }
        
        return@withContext false
    }
    
    private suspend fun sendFileMessage(
        instanceId: String,
        apiToken: String,
        recipient: String,
        fileUrl: String,
        filename: String
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                
                val json = JSONObject().apply {
                    put("phone", recipient)
                    put("body", fileUrl)
                    put("filename", filename)
                }
                
                val requestBody = json.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                
                val request = Request.Builder()
                    .url("https://api.chat-api.com/instance$instanceId/sendFile?token=$apiToken")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    return@withContext true
                }
                
                if (attempt < MAX_RETRIES - 1) {
                    // Wait before retry
                    Thread.sleep(RETRY_DELAY_MS)
                }
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES - 1) {
                    // Wait before retry
                    Thread.sleep(RETRY_DELAY_MS)
                } else {
                    throw e
                }
            }
        }
        
        return@withContext false
    }
    
    private suspend fun uploadFileToFirebase(file: File): String = withContext(Dispatchers.IO) {
        val storageRef = Firebase.storage.reference.child("attachments/${file.name}")
        storageRef.putFile(Uri.fromFile(file)).await()
        return@withContext storageRef.downloadUrl.await().toString()
    }
    
    private fun sendViaAccessibilityService(
        context: Context,
        recipient: String,
        message: String,
        attachments: List<File>
    ): Boolean {
        // Check if accessibility service is enabled
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(context)) {
            return false
        }
        
        // Send intent to WhatsAppService
        val intent = WhatsAppService.createSendIntent(context, recipient, message, attachments)
        context.startService(intent)
        
        // We can't know immediately if it succeeded, so log an attempt
        LogManager.logAction(context, "Attempted to send via Accessibility Service to: $recipient")
        
        return true
    }
}
