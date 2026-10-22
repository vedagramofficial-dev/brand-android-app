package com.brandx.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brandx.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.welcomeTitle.text = getString(R.string.welcome_title)
        binding.welcomeSubtitle.text = getString(R.string.welcome_subtitle)
        binding.primaryAction.text = getString(R.string.primary_action)
        binding.secondaryAction.text = getString(R.string.secondary_action)

        binding.primaryAction.setOnClickListener {
            binding.statusBadge.text = getString(R.string.status_active)
            binding.statusBadge.setBackgroundResource(R.drawable.badge_active)
        }

        binding.secondaryAction.setOnClickListener {
            binding.statusBadge.text = getString(R.string.status_idle)
            binding.statusBadge.setBackgroundResource(R.drawable.badge_idle)
        }
    }
}
