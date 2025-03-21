package com.example.email2whatsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.email2whatsapp.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOnboardingBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up ViewPager with onboarding slides
        setupViewPager()
        
        // Set up next button
        binding.nextButton.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < ONBOARDING_PAGES - 1) {
                // Go to next page
                binding.viewPager.currentItem = currentItem + 1
            } else {
                // Last page, complete onboarding
                completeOnboarding()
            }
        }
        
        // Set up skip button
        binding.skipButton.setOnClickListener {
            completeOnboarding()
        }
    }
    
    private fun setupViewPager() {
        // Create adapter with onboarding pages
        val adapter = OnboardingAdapter(this, listOf(
            OnboardingPage(
                "Welcome to Email2WhatsApp Automator",
                "Automate the process of checking emails and sending content via WhatsApp",
                R.drawable.ic_onboarding_welcome
            ),
            OnboardingPage(
                "Grant Permissions",
                "We need access to your Gmail and WhatsApp to automate the process",
                R.drawable.ic_onboarding_permissions
            ),
            OnboardingPage(
                "Set Up Your Automation",
                "Configure which emails to check and where to send the content",
                R.drawable.ic_onboarding_setup
            )
        ))
        
        binding.viewPager.adapter = adapter
        
        // Set up page indicator
        binding.pageIndicator.setViewPager(binding.viewPager)
        
        // Update button text on page change
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.nextButton.text = if (position == ONBOARDING_PAGES - 1) {
                    "Get Started"
                } else {
                    "Next"
                }
                
                binding.skipButton.visibility = if (position == ONBOARDING_PAGES - 1) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
            }
        })
    }
    
    private fun completeOnboarding() {
        // Mark onboarding as completed
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
        
        // Start main activity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    companion object {
        private const val ONBOARDING_PAGES = 3
    }
    
    data class OnboardingPage(
        val title: String,
        val description: String,
        val imageResId: Int
    )
}
