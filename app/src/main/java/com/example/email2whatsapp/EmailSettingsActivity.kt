package com.example.email2whatsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.email2whatsapp.databinding.ActivityEmailSettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

class EmailSettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEmailSettingsBinding
    private val RC_SIGN_IN = 9001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Load existing settings
        loadSettings()
        
        // Set up save button
        binding.saveButton.setOnClickListener {
            saveSettings()
            finish()
        }
        
        // Set up test search button
        binding.testSearchButton.setOnClickListener {
            testSearch()
        }
        
        // Set up Google account selection
        binding.gmailAccountButton.setOnClickListener {
            signInToGoogle()
        }
    }
    
    private fun loadSettings() {
        val sharedPrefs = getSharedPreferences("email_settings", Context.MODE_PRIVATE)
        binding.senderEmailInput.setText(sharedPrefs.getString("sender_email", ""))
        binding.subjectKeywordsInput.setText(sharedPrefs.getString("subject_keywords", ""))
        binding.bodyKeywordsInput.setText(sharedPrefs.getString("body_keywords", ""))
        binding.whatsappRecipientInput.setText(sharedPrefs.getString("whatsapp_recipient", ""))
        
        // Display current Gmail account
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            binding.gmailAccountText.text = account.email
        } else {
            binding.gmailAccountText.text = "Not signed in"
        }
    }
    
    private fun saveSettings() {
        val sharedPrefs = getSharedPreferences("email_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("sender_email", binding.senderEmailInput.text.toString())
            putString("subject_keywords", binding.subjectKeywordsInput.text.toString())
            putString("body_keywords", binding.bodyKeywordsInput.text.toString())
            putString("whatsapp_recipient", binding.whatsappRecipientInput.text.toString())
            apply()
        }
        
        // Log the action
        LogManager.logAction(this, "Email settings updated")
    }
    
    private fun testSearch() {
        // This would normally perform a test search using the Gmail API
        // For now, just show a toast with the search criteria
        val senderEmail = binding.senderEmailInput.text.toString()
        val subjectKeywords = binding.subjectKeywordsInput.text.toString()
        val bodyKeywords = binding.bodyKeywordsInput.text.toString()
        
        val queryParts = mutableListOf<String>()
        if (senderEmail.isNotEmpty()) queryParts.add("from:$senderEmail")
        if (subjectKeywords.isNotEmpty()) queryParts.add("subject:$subjectKeywords")
        if (bodyKeywords.isNotEmpty()) queryParts.add(bodyKeywords)
        queryParts.add("in:inbox -in:trash")
        
        val query = queryParts.joinToString(" ")
        
        android.widget.Toast.makeText(
            this,
            "Test search query: $query",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    
    private fun signInToGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                val account = GoogleSignIn.getLastSignedInAccount(this)
                if (account != null) {
                    binding.gmailAccountText.text = account.email
                    
                    // Store credential for later use
                    GmailCredentialManager.storeCredential(this, account)
                    
                    // Log the action
                    LogManager.logAction(this, "Google account connected: ${account.email}")
                }
            } else {
                binding.gmailAccountText.text = "Sign-in failed"
                
                // Log the action
                LogManager.logAction(this, "Google sign-in failed")
            }
        }
    }
}
