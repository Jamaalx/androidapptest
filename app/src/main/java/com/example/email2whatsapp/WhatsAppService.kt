package com.example.email2whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.bundleOf
import java.io.File

class WhatsAppService : AccessibilityService() {
    
    companion object {
        private const val ACTION_SEND_WHATSAPP = "com.example.email2whatsapp.ACTION_SEND_WHATSAPP"
        private const val EXTRA_RECIPIENT = "recipient"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_ATTACHMENTS = "attachments"
        
        fun createSendIntent(
            context: Context,
            recipient: String,
            message: String,
            attachments: List<File>
        ): Intent {
            return Intent(context, WhatsAppService::class.java).apply {
                action = ACTION_SEND_WHATSAPP
                putExtra(EXTRA_RECIPIENT, recipient)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_ATTACHMENTS, attachments.map { it.absolutePath }.toTypedArray())
            }
        }
    }
    
    private var pendingRecipient: String? = null
    private var pendingMessage: String? = null
    private var pendingAttachments: List<String>? = null
    private var currentStep = 0
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SEND_WHATSAPP) {
            pendingRecipient = intent.getStringExtra(EXTRA_RECIPIENT)
            pendingMessage = intent.getStringExtra(EXTRA_MESSAGE)
            pendingAttachments = intent.getStringArrayExtra(EXTRA_ATTACHMENTS)?.toList()
            
            // Start the WhatsApp sending process
            startWhatsAppSending()
        }
        
        return super.onStartCommand(intent, flags, startId)
    }
    
    private fun startWhatsAppSending() {
        // Reset step counter
        currentStep = 0
        
        // Open WhatsApp
        val whatsappIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (whatsappIntent != null) {
            whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(whatsappIntent)
        } else {
            LogManager.logAction(this, "WhatsApp not installed")
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.whatsapp") return
        
        try {
            when (currentStep) {
                0 -> openChat()
                1 -> typeMessage()
                2 -> sendMessage()
                3 -> attachFiles()
                4 -> {
                    // All done, reset
                    pendingRecipient = null
                    pendingMessage = null
                    pendingAttachments = null
                    currentStep = 0
                }
            }
        } catch (e: Exception) {
            LogManager.logAction(this, "Accessibility error: ${e.message}")
        }
    }
    
    private fun openChat() {
        // Find search button
        val searchButton = findNodeByViewId("com.whatsapp:id/menuitem_search")
        if (searchButton != null) {
            searchButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // Add a small delay to mimic human behavior
            Thread.sleep((1000..3000).random().toLong())
            
            // Find search edit text and type recipient
            val searchEditText = findNodeByViewId("com.whatsapp:id/search_input")
            if (searchEditText != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    pendingRecipient
                )
                searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                // Add a small delay to mimic human behavior
                Thread.sleep((1000..3000).random().toLong())
                
                // Find and click on the contact
                val contactList = findNodeByViewId("com.whatsapp:id/contact_list")
                if (contactList != null && contactList.childCount > 0) {
                    contactList.getChild(0)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    currentStep = 1
                }
            }
        }
    }
    
    private fun typeMessage() {
        // Find message input field
        val messageInput = findNodeByViewId("com.whatsapp:id/entry")
        if (messageInput != null) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                pendingMessage
            )
            messageInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            // Add a small delay to mimic human behavior
            Thread.sleep((1000..3000).random().toLong())
            
            currentStep = 2
        }
    }
    
    private fun sendMessage() {
        // Find send button
        val sendButton = findNodeByViewId("com.whatsapp:id/send")
        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // Add a small delay to mimic human behavior
            Thread.sleep((1000..3000).random().toLong())
            
            // If there are attachments, proceed to attach files, otherwise we're done
            if (pendingAttachments.isNullOrEmpty()) {
                currentStep = 4
                LogManager.logAction(this, "Message sent via Accessibility Service")
            } else {
                currentStep = 3
            }
        }
    }
    
    private fun attachFiles() {
        // Find attachment button
        val attachButton = findNodeByViewId("com.whatsapp:id/attach_button")
        if (attachButton != null) {
            attachButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // Add a small delay to mimic human behavior
            Thread.sleep((1000..3000).random().toLong())
            
            // Find document button
            val documentButton = findNodeByViewId("com.whatsapp:id/pickfiletype_document")
            if (documentButton != null) {
                documentButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                // Note: At this point, the system file picker opens, which is outside WhatsApp
                // We can't directly control it with this service
                // This is a limitation of the Accessibility Service approach
                
                LogManager.logAction(this, "Attachment dialog opened, but can't select files automatically")
                
                // We'll mark this as done, but in reality, the user would need to select the files manually
                currentStep = 4
            }
        }
    }
    
    private fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        return if (nodes.isNotEmpty()) nodes[0] else null
    }
    
    override fun onInterrupt() {
        // Not used
    }
}
