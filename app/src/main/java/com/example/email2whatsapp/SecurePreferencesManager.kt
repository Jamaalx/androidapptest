package com.example.email2whatsapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential

object SecurePreferencesManager {
    
    private const val SECURE_PREFS_FILENAME = "secure_prefs"
    
    fun getEncryptedSharedPreferences(context: Context): androidx.security.crypto.EncryptedSharedPreferences {
        val masterKeyAlias = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILENAME,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as androidx.security.crypto.EncryptedSharedPreferences
    }
    
    fun storeApiCredentials(context: Context, apiToken: String, instanceId: String) {
        val securePrefs = getEncryptedSharedPreferences(context)
        securePrefs.edit().apply {
            putString("chat_api_token", apiToken)
            putString("chat_api_instance", instanceId)
            apply()
        }
        
        // Log the action (without sensitive data)
        LogManager.logAction(context, "ChatAPI credentials stored securely")
    }
    
    fun clearAllSecureData(context: Context) {
        val securePrefs = getEncryptedSharedPreferences(context)
        securePrefs.edit().clear().apply()
        
        // Log the action
        LogManager.logAction(context, "All secure data cleared")
    }
}
