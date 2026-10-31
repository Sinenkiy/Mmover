package com.divinegames.mmover

import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.divinegames.mmover.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var motionController: MotionController
    private lateinit var rewardController: RewardAccessController
    private lateinit var timerRewardController: RewardAccessController
    private var timerRewardDialog: StealthRewardDialog? = null
    private var timerRewardNoticePending = false
    internal lateinit var adsController: MainAdsController
        private set
    internal lateinit var reviewController: ReviewController
        private set
    internal lateinit var sessionTimer: SessionTimerController
        private set
    private var timerDialogVisible = false
    private var stealthDialog: StealthRewardDialog? = null
    private var renderingStealthSwitch = false
    private var backgroundDirty = false
    private var settingsResultReceived = false
    private var screenLanguage = ""
    private var backgroundStyle: String? = null
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        settingsResultReceived = it.resultCode == RESULT_OK
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logMem("onCreate.start")
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        @Suppress("DEPRECATION")
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.glass_header)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = false
        binding.centerStatusTextView.visibility = View.GONE
        // A 48dp control row plus only the insets delivered to this window.
        // Older Android versions already place the content below the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.titleBar) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = safe.left, top = safe.top, right = safe.right)
            insets
        }
        ViewCompat.requestApplyInsets(binding.titleBar)

        val locale = resources.configuration.locales[0]
        Log.d("RegionDebug", "Device locale: ${locale.language}-${locale.country}")
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        reviewController = ReviewController(
            ReviewPolicy(PreferenceReviewStore(this)), MainThreadScheduler(),
            RuStoreReviewGateway(applicationContext), canShow = ::canPromptForReview
        )
        rewardController = RewardAccessController(
            RewardAccess(PreferenceRewardAccessStore(prefs)),
            MainThreadScheduler()
        ) { enabled -> renderStealthSwitch(enabled) }
        timerRewardController = RewardAccessController(
            RewardAccess(PreferenceRewardAccessStore(prefs,
                PreferenceRewardAccessStore.TIMER_DATE, PreferenceRewardAccessStore.TIMER_ENABLED)),
            MainThreadScheduler()
        ) { if (::sessionTimer.isInitialized) renderSessionTimer(sessionTimer.state) }
        adsController = MainAdsController(
            this, binding.mainContainer, binding.adViewContainerTop, binding.adViewContainerBottom,
            onReward = { rewardController.grant() },
            onStateChanged = ::updateStealthSwitchState,
            onMessage = { showSafeToast(it) },
            onFullscreenAd = { reviewController.onFullscreenAd() }
        )
        motionController = MotionController(
            MainThreadScheduler(),
            settings = {
                MotionController.Settings(
                    activeSeconds = prefs.getInt("active_duration", 10),
                    pauseSteps = prefs.getInt("pause_duration", 36),
                    stealth = rewardController.isEnabled()
                )
            },
            onStateChanged = ::renderMotion
        )
        sessionTimer = SessionTimerController(
            MainThreadScheduler(), prefs.getInt(SessionTimerController.PREFERENCE, 0),
            onStateChanged = ::renderSessionTimer,
            onFinished = {
                motionController.stop()
                reviewController.onAutomaticStop()
                showSafeToast(R.string.session_timer_finished)
            }
        )
        supportFragmentManager.setFragmentResultListener(SessionTimerDialogFragment.RESULT, this) { _, result ->
            val minutes = result.getInt(SessionTimerDialogFragment.MINUTES).coerceIn(0, SessionTimerController.MAX_MINUTES)
            if (minutes > 0 && !canUseSessionTimer()) showTimerRewardDialog(minutes)
            else applySessionTimer(minutes)
        }
        renderSessionTimer(sessionTimer.state)
        screenLanguage = LocaleHelper.getLanguage(this)
        backgroundStyle = prefs.getString("animation_style", "generated_contrast_noise")
        binding.loadingIndicator.visibility = View.VISIBLE
        setUiEnabled(false)
        binding.titleTextView.text = getString(R.string.app_name)
        updateStealthSwitchState()
        setupClickListeners()

        adsController.initialize()
        logMem("before_bg_load")
        binding.movingBackground.loadBackgroundWithCallback {
            runOnUiThread {
                binding.loadingIndicator.visibility = View.GONE
                setUiEnabled(true)
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.playPauseButton.isEnabled = enabled
        binding.modeSwitch.isEnabled = enabled
        binding.backButton.isEnabled = enabled
        binding.optionsButton.isEnabled = enabled
        binding.settingsButton.isEnabled = enabled
        binding.removeAdsButton.isEnabled = enabled
        binding.timerButton.isEnabled = enabled
    }

    private fun renderMotion(state: MotionController.State) {
        when (state) {
            MotionController.State.Moving -> {
                binding.movingBackground.stopMovement()
                stopVibration()
                binding.centerStatusTextView.visibility = View.GONE
                binding.movingBackground.setPowerSavingMode(false)
                keepScreenOn()
                restoreUserSettings()
                binding.playPauseButton.setText(R.string.motion_stop)
                binding.playPauseButton.setIconResource(R.drawable.ic_media_stop)
                binding.movingBackground.startMovement()
                startVibration()
            }
            MotionController.State.Stopped -> {
                if (::sessionTimer.isInitialized) sessionTimer.stop()
                binding.movingBackground.stopMovement()
                stopVibration()
                allowScreenToLock()
                restoreUserSettings()
                binding.playPauseButton.setText(R.string.motion_start)
                binding.playPauseButton.setIconResource(R.drawable.ic_media_play2)
                binding.movingBackground.setPowerSavingMode(false)
                binding.centerStatusTextView.visibility = View.GONE
            }
            is MotionController.State.Waiting -> {
                if (binding.centerStatusTextView.visibility != View.VISIBLE) {
                    binding.movingBackground.stopMovement()
                    stopVibration()
                    binding.movingBackground.setPowerSavingMode(true)
                    if (timerDialogVisible || timerRewardDialog?.isShowing == true) restoreUserSettings() else setScreenBrightness(0.02f)
                }
                binding.centerStatusTextView.visibility = View.VISIBLE
                val minutes = state.secondsRemaining / 60
                val seconds = state.secondsRemaining % 60
                val modeLabel = getString(
                    if (state.stealth) R.string.mode_stealth_label else R.string.mode_normal_label
                )
                binding.centerStatusTextView.text = getString(
                    R.string.power_saving_mode_text, minutes, seconds, modeLabel
                )
            }
        }
    }

    private fun sendEmail() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:divinegames42@gmail.com".toUri()
            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(R.string.email_feedback_subject)
            )
        }
        try {
            startActivity(
                Intent.createChooser(
                    emailIntent,
                    getString(R.string.email_chooser_title)
                )
            )
        } catch (_: android.content.ActivityNotFoundException) {
            showSafeToast(R.string.email_client_not_found)
        }
    }

    private fun setupClickListeners() {
        val hints = ButtonHints(this)
        listOf(binding.settingsButton, binding.backButton, binding.optionsButton, binding.removeAdsButton, binding.timerButton)
            .forEach(hints::bind)
        binding.timerButton.setOnClickListener { showSessionTimer() }
        binding.settingsButton.setOnClickListener {
            reviewController.cancelPending()
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.removeAdsButton.setOnClickListener {
            reviewController.cancelPending()
            if (canShowUi()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.remove_ads_purchase)
                    .setMessage(R.string.remove_ads_unavailable)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
        binding.backButton.setOnClickListener {
            openStartScreen()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                openStartScreen()
            }
        })

        binding.playPauseButton.setOnClickListener {
            if (motionController.state == MotionController.State.Stopped) {
                if (sessionTimer.state.minutes > 0 && !canUseSessionTimer()) {
                    showTimerRewardDialog()
                    return@setOnClickListener
                }
                reviewController.onSessionStarted()
                sessionTimer.start()
                motionController.start()
            } else {
                motionController.stop()
                reviewController.onManualStop()
            }
        }
        binding.modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (renderingStealthSwitch) return@setOnCheckedChangeListener
            reviewController.cancelPending()
            if (!rewardController.setEnabled(isChecked) && isChecked) {
                renderStealthSwitch(false)
                showStealthModeDialog()
            }
        }
        binding.optionsButton.setOnClickListener { view ->
            reviewController.cancelPending()
            val popup = createMainMenu(view) { menuItem: MenuItem ->
                when (menuItem.itemId) {
                    R.id.menu_settings -> settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                    R.id.menu_contact -> sendEmail() // <-- ДОБАВЛЕНО
                    R.id.menu_share -> shareAppLink()
                    R.id.menu_faq -> openInfoScreen(R.string.menu_faq, R.drawable.my_faq_image, R.string.faq_text)
                }
                true
            }
            popup.show()
        }
    }

    internal fun canUseSessionTimer() = timerRewardController.hasAccess()

    private fun applySessionTimer(minutes: Int) {
        prefs.edit { putInt(SessionTimerController.PREFERENCE, minutes) }
        sessionTimer.configure(minutes)
    }

    private fun showTimerRewardDialog(minutes: Int? = null) {
        if (!canShowUi() || supportFragmentManager.isStateSaved || adsController.isFullscreenShowing ||
            timerRewardDialog?.isShowing == true) return
        reviewController.cancelPending()
        restoreUserSettings()
        timerRewardDialog = StealthRewardDialog(this,
            onRetry = { adsController.retryRewarded() },
            titleRes = R.string.session_timer_title,
            messageRes = R.string.session_timer_reward_message,
            onDismiss = { onTimerDialogVisibilityChanged(timerDialogVisible) }
        ) {
            // Do not depend on the SDK using a separate Activity to pause motion.
            motionController.stop()
            reviewController.onAutomaticStop()
            adsController.showRewarded {
                timerRewardController.grant()
                if (minutes != null) applySessionTimer(minutes)
                timerRewardNoticePending = true
            }
        }.also { it.show(adsController.rewardedState) }
    }

    private fun showTimerRewardNoticeIfReady() {
        if (timerRewardNoticePending && canShowUi() &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            !adsController.isFullscreenShowing) {
            timerRewardNoticePending = false
            showSafeToast(R.string.session_timer_reward_granted)
        }
    }

    private fun showSessionTimer() {
        if (!canShowUi() || supportFragmentManager.isStateSaved ||
            supportFragmentManager.findFragmentByTag(SessionTimerDialogFragment.TAG) != null) return
        reviewController.cancelPending()
        SessionTimerDialogFragment.newInstance(sessionTimer.state.minutes)
            .showNow(supportFragmentManager, SessionTimerDialogFragment.TAG)
    }

    private fun renderSessionTimer(state: SessionTimerController.State) {
        // A countdown started with valid access may finish after midnight; new starts stay gated.
        val locked = !canUseSessionTimer() && state.phase != SessionTimerController.Phase.RUNNING
        binding.timerButton.isSelected = state.minutes > 0 && !locked
        val status = getString(when {
            locked -> R.string.session_timer_locked
            state.minutes > 0 -> R.string.session_timer_enabled
            else -> R.string.session_timer_disabled
        })
        if (ViewCompat.getStateDescription(binding.timerButton) != status)
            ViewCompat.setStateDescription(binding.timerButton, status)
        binding.timerStatus.visibility = if (state.minutes == 0) View.GONE else View.VISIBLE
        binding.timerStatus.text = if (state.minutes > 0 && locked) getString(R.string.session_timer_locked) else when (state.phase) {
            SessionTimerController.Phase.OFF -> ""
            SessionTimerController.Phase.READY -> getString(R.string.session_timer_ready, formatSessionTime(state.remainingSeconds))
            SessionTimerController.Phase.RUNNING -> getString(R.string.session_timer_running, formatSessionTime(state.remainingSeconds))
            SessionTimerController.Phase.FINISHED -> getString(R.string.session_timer_finished)
        }
        (supportFragmentManager.findFragmentByTag(SessionTimerDialogFragment.TAG) as? SessionTimerDialogFragment)?.render(state, canUseSessionTimer())
    }

    internal fun onTimerDialogVisibilityChanged(visible: Boolean) {
        timerDialogVisible = visible
        if (!canShowUi()) return
        if (visible || timerRewardDialog?.isShowing == true) {
            reviewController.cancelPending()
            restoreUserSettings()
        } else if (motionController.state is MotionController.State.Waiting) {
            setScreenBrightness(0.02f)
        }
    }

    private fun openStartScreen() {
        reviewController.cancelPending()
        startActivity(Intent(this, StartActivity::class.java)
            .putExtra("forceOnboarding", true)
            .putExtra(StartActivity.RETURN_TO_MAIN, true))
    }

    private fun showStealthModeDialog() {
        if (!canShowUi()) return

        if (stealthDialog?.isShowing == true) return
        stealthDialog = StealthRewardDialog(this, onRetry = { adsController.retryRewarded() }) {
            adsController.showRewarded()
        }.also {
            it.show(adsController.rewardedState)
        }
    }

    private fun renderStealthSwitch(enabled: Boolean) {
        renderingStealthSwitch = true
        try {
            binding.modeSwitch.isChecked = enabled
        } finally {
            renderingStealthSwitch = false
        }
        updateModeDescription(enabled)
    }

    private fun updateModeDescription(stealth: Boolean) {
        val pauseSeconds = prefs.getInt("pause_duration", 36).coerceIn(0, 180) * 5
        binding.modeDescription.text = if (stealth) {
            getString(R.string.motion_description_stealth)
        } else {
            getString(R.string.motion_description, prefs.getInt("active_duration", 10).coerceAtLeast(1),
                pauseSeconds / 60, pauseSeconds % 60)
        }
    }

    private fun updateStealthSwitchState() {
        renderStealthSwitch(rewardController.isEnabled())
        if (::adsController.isInitialized) {
            stealthDialog?.render(adsController.rewardedState)
            timerRewardDialog?.render(adsController.rewardedState)
            showTimerRewardNoticeIfReady()
        }
    }

    private fun openInfoScreen(titleResId: Int, imageResId: Int, textResId: Int) {
        val intent = Intent(this, InfoActivity::class.java).apply {
            putExtra("EXTRA_TITLE_RES_ID", titleResId)
            putExtra("EXTRA_IMAGE_RES_ID", imageResId)
            putExtra("EXTRA_TEXT_RES_ID", textResId)
        }
        startActivity(intent)
    }

    private fun shareAppLink() {
        val url = "https://apps.rustore.ru/app/com.divinegames.mmover"
        val shareText = getString(R.string.share_app_text, url)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    override fun onResume() {
        super.onResume()
        if (settingsResultReceived) {
            settingsResultReceived = false
            if (screenLanguage != LocaleHelper.getLanguage(this)) {
                recreate()
                return
            }
            val newStyle = prefs.getString("animation_style", "generated_contrast_noise")
            backgroundDirty = backgroundStyle != newStyle
            backgroundStyle = newStyle
        }
        reviewController.onResume()
        rewardController.onResume()
        timerRewardController.onResume()
        adsController.onResume()
        showTimerRewardNoticeIfReady()

        logMem("onResume")
        restoreUserSettings()
        if (backgroundDirty) {
            backgroundDirty = false
            binding.movingBackground.loadBackgroundWithCallback {
                Log.d("MainActivity", "Фон перезагружен после возврата из SettingsActivity")
            }
        }
        updateStealthSwitchState()

    }

    override fun onPause() {
        super.onPause()
        logMem("onPause")
        reviewController.onPause()
        motionController.stop()
        rewardController.onPause()
        timerRewardController.onPause()
        adsController.onPause()
    }

    override fun onDestroy() {
        reviewController.close()
        stealthDialog?.dismiss()
        stealthDialog = null
        timerRewardDialog?.dismiss()
        timerRewardDialog = null
        adsController.close()
        rewardController.close()
        timerRewardController.close()
        sessionTimer.close()
        motionController.close()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopVibration()
        super.onDestroy()
    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun allowScreenToLock() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startVibration() {
        val vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        if (!vibrationEnabled) return

        val deviceVibrator = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deviceVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            deviceVibrator.vibrate(longArrayOf(0, 100, 500), 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::reviewController.isInitialized) reviewController.cancelPending()
    }

    private fun canPromptForReview(): Boolean =
        canShowUi() && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            window.decorView.hasWindowFocus() &&
            motionController.state == MotionController.State.Stopped &&
            stealthDialog?.isShowing != true && timerRewardDialog?.isShowing != true &&
            !timerDialogVisible && !adsController.isFullscreenShowing

    private fun logMem(tag: String) {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val free = rt.freeMemory() / (1024 * 1024)
        val total = rt.totalMemory() / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)

        Log.d("MEM", "$tag used=${used}MB free=${free}MB total=${total}MB max=${max}MB")
    }

}
