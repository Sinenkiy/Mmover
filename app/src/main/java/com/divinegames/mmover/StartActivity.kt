package com.divinegames.mmover

import android.content.Context
import android.content.Intent

import android.os.Bundle

import androidx.activity.OnBackPressedCallback
import androidx.core.view.isInvisible

import com.divinegames.mmover.databinding.ActivityOnboardingBinding

class StartActivity : BaseActivity() {
    companion object {
        internal const val RETURN_TO_MAIN = "returnToExistingMain"
    }

    private lateinit var binding: ActivityOnboardingBinding
    private var showingOnboarding = false
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(RETURN_TO_MAIN, false)) {
            // Back from the start screen still exits the task, rather than looping to Main.
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { finishAffinity() }
            })
        }


        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // Consume the Intent flag once; retain the displayed screen in saved state.
        val force = intent.getBooleanExtra("forceOnboarding", false)
        intent.removeExtra("forceOnboarding")

        // 2) Версии
        val lastSeenVersionCode = prefs.getInt("lastSeenVersionCode", 0)
        val currentVersionCode  = BuildConfig.VERSION_CODE

        // 3) Решаем, показывать ли онбординг
        showingOnboarding = savedInstanceState?.getBoolean("showing_onboarding")
            ?: (force || currentVersionCode > lastSeenVersionCode)

        if (!showingOnboarding) {
            // Не показываем — сразу на Main
            goToMainActivity()
            return
        }

        // 4) Показываем онбординг
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOnboardingUi()
        applyHelpInsets(binding.root)

    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("showing_onboarding", showingOnboarding)
        super.onSaveInstanceState(outState)
    }

    private fun setupOnboardingUi() {
        fun updateScrollHint() {
            // Keep its space when hidden so reaching the end does not change the scroll range.
            binding.scrollHint.isInvisible = !binding.onboardingScroll.canScrollVertically(1)
        }
        binding.onboardingScroll.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> updateScrollHint() }
        )
        binding.onboardingScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScrollHint() }
        binding.onboardingScroll.getChildAt(0).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateScrollHint() }
        binding.scrollHint.setOnClickListener {
            binding.onboardingScroll.smoothScrollBy(0, binding.onboardingScroll.height * 3 / 4)
        }
        binding.faqButton.setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java)
                .putExtra("EXTRA_TITLE_RES_ID", R.string.menu_faq)
                .putExtra("EXTRA_TEXT_RES_ID", R.string.faq_text))
        }
        binding.startButton.setOnClickListener {
            getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("lastSeenVersionCode", BuildConfig.VERSION_CODE)
                .apply()
            goToMainActivity()
        }
    }
    private fun goToMainActivity() {
        if (navigated) return
        navigated = true
        if (!intent.getBooleanExtra(RETURN_TO_MAIN, false)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
