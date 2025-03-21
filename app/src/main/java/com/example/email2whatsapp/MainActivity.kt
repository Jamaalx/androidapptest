package com.example.email2whatsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.email2whatsapp.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val RC_SIGN_IN = 9001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check if user has completed onboarding
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val onboardingCompleted = sharedPrefs.getBoolean("onboarding_completed", false)
        
        if (!onboardingCompleted) {
            // Start onboarding
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        // Set up email automation toggle
        binding.emailAutomationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Start email checking service
                startEmailChecking()
            } else {
                // Stop email checking service
                stopEmailChecking()
            }
        }
        
        // Set up email settings button
        binding.editEmailSettingsButton.setOnClickListener {
            startActivity(Intent(this, EmailSettingsActivity::class.java))
        }
        
        // Set up schedule new message button
        binding.scheduleNewMessageButton.setOnClickListener {
            startActivity(Intent(this, ScheduleMessageActivity::class.java))
        }
        
        // Set up logs button
        binding.viewLogsButton.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        
        // Load scheduled messages
        loadScheduledMessages()
    }
    
    private fun startEmailChecking() {
        // Check if user is signed in with Google
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            // Sign in with Google
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        } else {
            // Start email checking worker
            EmailCheckWorker.scheduleEmailChecking(this)
            Toast.makeText(this, "Email checking started", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopEmailChecking() {
        // Stop email checking worker
        EmailCheckWorker.cancelEmailChecking(this)
        Toast.makeText(this, "Email checking stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun loadScheduledMessages() {
        lifecycleScope.launch {
            try {
                val messageDao = AppDatabase.getInstance(this@MainActivity).messageDao()
                val messages = messageDao.getAllMessages()
                
                // Update UI with scheduled messages
                if (messages.isEmpty()) {
                    binding.noScheduledMessagesText.visibility = android.view.View.VISIBLE
                    binding.scheduledMessagesList.visibility = android.view.View.GONE
                } else {
                    binding.noScheduledMessagesText.visibility = android.view.View.GONE
                    binding.scheduledMessagesList.visibility = android.view.View.VISIBLE
                    
                    // Set up adapter for scheduled messages
                    val adapter = ScheduledMessageAdapter(messages) { message ->
                        // Handle message click - show edit dialog
                        val intent = Intent(this@MainActivity, ScheduleMessageActivity::class.java)
                        intent.putExtra("message_id", message.id)
                        startActivity(intent)
                    }
                    binding.scheduledMessagesList.adapter = adapter
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading scheduled messages: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // Start email checking worker
                EmailCheckWorker.scheduleEmailChecking(this)
                Toast.makeText(this, "Email checking started", Toast.LENGTH_SHORT).show()
            } else {
                // Sign in failed
                binding.emailAutomationSwitch.isChecked = false
                Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh scheduled messages
        loadScheduledMessages()
        
        // Update email automation switch state
        binding.emailAutomationSwitch.isChecked = EmailCheckWorker.isEmailCheckingActive(this)
    }
}
