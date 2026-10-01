package com.divinegames.mmover

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    internal lateinit var adsController: MainAdsController
        private set
    private var stealthDialog: StealthRewardDialog? = null
    private var renderingStealthSwitch = false
    private var backgroundDirty = false
    private var settingsResultReceived = false
    private var screenLanguage = ""
    private var backgroundStyle: String? = null
    private val TAG = "MouseMoverMain"
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

        val locale = if (Build.VERSION.SDK_INT >= 24) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
        Log.d("RegionDebug", "Device locale: ${locale.language}-${locale.country}")
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        rewardController = RewardAccessController(
            RewardAccess(PreferenceRewardAccessStore(prefs)),
            MainThreadScheduler()
        ) { enabled -> renderStealthSwitch(enabled) }
        adsController = MainAdsController(
            this, binding.mainContainer, binding.adViewContainerTop, binding.adViewContainerBottom,
            onReward = { rewardController.grant() },
            onStateChanged = ::updateStealthSwitchState,
            onMessage = { showSafeToast(it) }
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
                    setScreenBrightness(0.02f)
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
            data = Uri.parse("mailto:divinegames42@gmail.com")
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
        } catch (ex: android.content.ActivityNotFoundException) {
            showSafeToast(R.string.email_client_not_found)
        }
    }

    private fun setupClickListeners() {
        val hints = ButtonHints(this)
        listOf(binding.settingsButton, binding.backButton, binding.optionsButton, binding.removeAdsButton)
            .forEach(hints::bind)
        binding.settingsButton.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        binding.removeAdsButton.setOnClickListener {
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
                motionController.start()
            } else {
                motionController.stop()
                checkRatingOnStop()
            }
        }
        binding.modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (renderingStealthSwitch) return@setOnCheckedChangeListener
            if (!rewardController.setEnabled(isChecked) && isChecked) {
                renderStealthSwitch(false)
                showStealthModeDialog()
            }
        }
        binding.optionsButton.setOnClickListener { view ->
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

    private fun openStartScreen() {
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
        if (::adsController.isInitialized) stealthDialog?.render(adsController.rewardedState)
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
        rewardController.onResume()
        adsController.onResume()

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
        motionController.stop()
        rewardController.onPause()
        adsController.onPause()
    }

    override fun onDestroy() {
        stealthDialog?.dismiss()
        stealthDialog = null
        adsController.close()
        rewardController.close()
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

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 500), 0)
        }
    }

    private fun stopVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
    }

    private fun checkRatingOnStop() {
        val neverShow = prefs.getBoolean("never_show_rating", false)
        if (neverShow) return
        val currentStopClicks = prefs.getInt("stop_click_count", 0) + 1
        prefs.edit().putInt("stop_click_count", currentStopClicks).apply()
        if (currentStopClicks % 10 == 0) {
            showRateAppDialog()
        }
    }

    private fun showRateAppDialog() {
        if (!canShowUi()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.rate_dialog_title)
            .setMessage(R.string.rate_dialog_message)
            .setPositiveButton(R.string.rate_dialog_rate_now) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.rustore.ru/app/com.divinegames.mmover")))
                } catch (_: android.content.ActivityNotFoundException) {
                    showSafeToast(R.string.store_client_not_found)
                } catch (e: android.content.ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
                prefs.edit().putBoolean("never_show_rating", true).apply()
            }
            .setNegativeButton(R.string.rate_dialog_no_thanks) { _, _ ->
                prefs.edit().putBoolean("never_show_rating", true).apply()
            }
            .setNeutralButton(R.string.rate_dialog_later) { _, _ ->
            }
            .show()
    }

    private fun logMem(tag: String) {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val free = rt.freeMemory() / (1024 * 1024)
        val total = rt.totalMemory() / (1024 * 1024)
        val max = rt.maxMemory() / (1024 * 1024)

        Log.d("MEM", "$tag used=${used}MB free=${free}MB total=${total}MB max=${max}MB")
    }

}
