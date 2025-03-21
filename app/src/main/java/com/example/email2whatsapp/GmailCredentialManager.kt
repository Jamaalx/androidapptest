package com.example.email2whatsapp

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes

object GmailCredentialManager {
    
    fun getCredential(context: Context): GoogleCredential? {
        val securePrefs = SecurePreferencesManager.getEncryptedSharedPreferences(context)
        val accessToken = securePrefs.getString("gmail_access_token", null) ?: return null
        val refreshToken = securePrefs.getString("gmail_refresh_token", null) ?: return null
        
        return GoogleCredential.Builder()
            .setTransport(NetHttpTransport())
            .setJsonFactory(GsonFactory.getDefaultInstance())
            .setClientSecrets(
                securePrefs.getString("gmail_client_id", ""),
                securePrefs.getString("gmail_client_secret", "")
            )
            .build()
            .setAccessToken(accessToken)
            .setRefreshToken(refreshToken)
    }
    
    fun storeCredential(context: Context, account: GoogleSignInAccount) {
        val securePrefs = SecurePreferencesManager.getEncryptedSharedPreferences(context)
        securePrefs.edit().apply {
            putString("gmail_access_token", account.idToken)
            // Note: In a real implementation, you would need to get the refresh token
            // through the GoogleAuthorizationCodeFlow, which requires more setup
            putString("gmail_refresh_token", "placeholder_refresh_token")
            putString("gmail_account", account.email)
            apply()
        }
        
        // Log the action (without sensitive data)
        LogManager.logAction(context, "Gmail credentials stored for: ${account.email}")
    }
    
    fun buildGmailService(context: Context): Gmail? {
        val credential = getCredential(context) ?: return null
        
        return Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("Email2WhatsApp")
        .build()
    }
    
    fun clearCredentials(context: Context) {
        val securePrefs = SecurePreferencesManager.getEncryptedSharedPreferences(context)
        securePrefs.edit().apply {
            remove("gmail_access_token")
            remove("gmail_refresh_token")
            remove("gmail_account")
            apply()
        }
        
        // Log the action
        LogManager.logAction(context, "Gmail credentials cleared")
    }
}
