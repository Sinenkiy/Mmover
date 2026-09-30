//MainActivity.kt — обновлённый код с колбэком после загрузки
package com.divinegames.mmover

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.divinegames.mmover.databinding.ActivityMainBinding
//import com.divinegames.mousemover.databinding.ActivityMainBinding
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdLoader
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.common.AdRequestConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.get

// Версии Android
private const val API_ANDROID_15 = 35  // Android 15
private const val API_ANDROID_16 = 36  // Android 16 (на будущее)

class MainActivity : BaseActivity() {

    // Объект привязки к layout. Через него мы получаем доступ ко всем View.
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var yandexTopBanner: BannerAdView? = null
    private var yandexBottomBanner: BannerAdView? = null

    private var rewardedAd: RewardedAd? = null
    private var rewardedAdLoader: RewardedAdLoader? = null

    // --- Переменные для рекламы и ее обновления ---
    private val uiHandler = Handler(Looper.getMainLooper())
    private val BANNER_FALLBACK_TIMEOUT_MS = 5000L   // <-- твой N мс, при желании поменяй

    private val NATIVE_REFRESH_MS = 60_000L          // 60 сек

    // Переменные состояния
    private var isPlaying = false
    private var activeTimerJob: Job? = null
    private var pauseTimerJob: Job? = null

    private var pauseCountdownTimer: CountDownTimer? = null
    //private var bannerShown = false
    private val TAG = "MouseMoverMain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Инициализируем настройки ДО ВСЕГО ОСТАЛЬНОГО.
        // Это гарантирует, что они будут доступны.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        //prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // 2. Инициализируем View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.centerStatusTextView.visibility = View.GONE

        // ✅ ВАЖНО: инициализируем ДО любых обращений к prefs
        //prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // 🔁 сбросить stealth, если день новый
        val lastDate = prefs.getString("last_stealth_activation_date", "")
        if (lastDate != getTodayDateString()) {
            prefs.edit().putBoolean("stealth_mode_enabled", false).apply()
        }

        setupYandexTopBanner()
        setupYandexBottomBanner()
        setupYandexRewarded()

        /// Устанавливаем высоту titleBar в зависимости от API
        val titleBarHeight = when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.LOLLIPOP..Build.VERSION_CODES.N_MR1 -> 40 // для API <= 25
            in Build.VERSION_CODES.O..Build.VERSION_CODES.R -> 50 // для API 26-30
            in Build.VERSION_CODES.S..Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> 70 // для API 31-34
            else -> 100  // для API 35 и больше
        }

        // Обновляем высоту titleBar
        binding.titleBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = dpToPx(titleBarHeight)
        }

        // 2) Базовый отступ СНИЗУ для содержимого шапки
        // Можно подстроить числа под вкус.
        val extraBottomDp = when (Build.VERSION.SDK_INT) {
            in Build.VERSION_CODES.LOLLIPOP..Build.VERSION_CODES.N_MR1 -> 5 // для API <= 25
            in Build.VERSION_CODES.O..Build.VERSION_CODES.R -> 8 // для API 26-30
            in Build.VERSION_CODES.S..Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> 10 // для API 31-34
            else -> 12  // для API 35 и больше
        }

        // применяем его к titleContent (родитель — RelativeLayout!)
        binding.titleContent.updateLayoutParams<RelativeLayout.LayoutParams> {
            bottomMargin = dpToPx(extraBottomDp)
        }

        // 3) Safe-area сверху: добавляем паддинг в саму панель под статус-бар/вырез
        ViewCompat.setOnApplyWindowInsetsListener(binding.titleBar) { v, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // если хочешь чуть больше воздуха сверху — добавь + dpToPx(4) и т.п.
            v.updatePadding(top = statusTop)
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

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) { // API 26 и ниже
            binding.bottomSafeBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = 0
            }
            binding.bottomSafeBar.visibility = View.VISIBLE      // оставить в разметке для констрейнтов
        //    binding.bottomSafeBar.setBackgroundColor(Color.TRANSPARENT)
        }

        setupWindowInsets()

        // 4. Настраиваем "слушателей" для рекламы
        //setupAppodealCallbacks()

        // 5. Настраиваем начальное состояние UI и обработчики
        binding.titleTextView.text = getString(R.string.main_screen_title_off)
        updateStealthSwitchState()
        setupClickListeners()

        // 6. Показываем прогресс-бар и ждем, пока фон загрузится
        binding.loadingIndicator.visibility = View.VISIBLE
        setUiEnabled(false)

        // 7. Загрузка фона
        // restoreUserSettings()
        binding.movingBackground.loadBackgroundWithCallback {
            runOnUiThread {
                // Фон загружен — включаем UI
                binding.loadingIndicator.visibility = View.GONE
                setUiEnabled(true)
            }
        }
    }

    // Функция для конвертации dp в пиксели
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.playPauseButton.isEnabled = enabled
        binding.modeSwitch.isEnabled = enabled
        binding.backButton.isEnabled = enabled
        binding.optionsButton.isEnabled = enabled
    }

    // --- Управление состоянием (Play/Pause) ---
    private fun startPlayingState() {
        pauseTimerJob?.cancel()
        pauseCountdownTimer?.cancel() // <-- Добавлено: Убиваем таймер
        binding.centerStatusTextView.visibility = View.GONE  // <-- добавь
        binding.movingBackground.setPowerSavingMode(false)   // <-- добавили
        isPlaying = true
        keepScreenOn()
        binding.titleTextView.text = getString(R.string.main_screen_title_on)
        restoreUserSettings()
        binding.playPauseButton.setImageResource(R.drawable.ic_media_stop)
        binding.movingBackground.startMovement() // Доступ через binding
        startVibration()

        val isStealthMode = isStealthActiveToday()
        //val isStealthMode = prefs.getBoolean("stealth_mode_enabled", false)
        val maxDuration = prefs.getInt("active_duration", 5)

        // Теперь длительность активности ВСЕГДА берется из настроек, даже в Стелс-режиме.
        val activeDurationSeconds = prefs.getInt("active_duration", 5)
        //val activeDurationSeconds = if (isStealthMode && maxDuration > 1) (1..maxDuration).random() else maxDuration

        activeTimerJob = lifecycleScope.launch {
            delay(activeDurationSeconds * 1000L)
            startPausedState(isManualAction = false)
        }
    }

    private fun startPausedState(isManualAction: Boolean) {
        activeTimerJob?.cancel()
        pauseTimerJob?.cancel()
        pauseCountdownTimer?.cancel()
        pauseCountdownTimer = null
        isPlaying = false
        binding.movingBackground.stopMovement() // Доступ через binding
        stopVibration()

        if (isManualAction) {
            allowScreenToLock()
            restoreUserSettings()
            binding.titleTextView.text = getString(R.string.main_screen_title_off)
            binding.playPauseButton.setImageResource(R.drawable.ic_media_play2)
            binding.movingBackground.setPowerSavingMode(false)
            binding.centerStatusTextView.visibility = View.GONE      // <-- прячем текст в ручной паузе
            return
        } else {
            //binding.titleTextView.text = getString(R.string.main_screen_title_waiting)
            binding.movingBackground.setPowerSavingMode(true)  // <-- включаем чёрный фон
            setScreenBrightness(0.02f)
        }

        val isStealthMode = isStealthActiveToday()
        //val isStealthMode = prefs.getBoolean("stealth_mode_enabled", false)
        val maxDuration = prefs.getInt("pause_duration", 36) * 5

        // --- ИЗМЕНЕНИЕ #2: Длительность паузы ---
        // В Стелс-режиме длительность паузы случайна (от 0 до максимума),
        // в обычном - фиксирована.
        val pauseDurationSeconds = if (isStealthMode && maxDuration > 0) {
            (0..maxDuration).random()
        } else {
            maxDuration
        }
        //val pauseDurationSeconds = if (isStealthMode && maxDuration > 1) (1..maxDuration).random() else maxDuration

        if (pauseDurationSeconds > 0) {
            // показываем центральный текст
            binding.centerStatusTextView.visibility = View.VISIBLE

            // режим для подписи
            val modeLabel = if (isStealthMode) {
                getString(R.string.mode_stealth_label)
            } else {
                getString(R.string.mode_normal_label)
            }
            // --- НОВАЯ ЛОГИКА ТАЙМЕРА ---
            pauseCountdownTimer = object : CountDownTimer(pauseDurationSeconds * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    // Обновляем заголовок каждую секунду
                    val minutes = (millisUntilFinished / 1000) / 60
                    val seconds = (millisUntilFinished / 1000) % 60
                    binding.titleTextView.text = String.format(
                        Locale.getDefault(),
                        "%s %02d:%02d",
                        getString(R.string.main_screen_title_waiting),
                        minutes,
                        seconds
                    )
                    // центральный текст
                    binding.centerStatusTextView.text = getString(
                        R.string.power_saving_mode_text,
                        minutes,
                        seconds,
                        modeLabel
                    )
                }
                override fun onFinish() {
                    // Когда таймер закончен, запускаем режим активности
                    startPlayingState()
                }
            }.start()
            // --- КОНЕЦ НОВОЙ ЛОГИКИ ---
        } else {
            // пауза 0 сек — сразу в активность, без текста
            binding.centerStatusTextView.visibility = View.GONE
            startPlayingState()
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
            Toast.makeText(
                this,
                getString(R.string.email_client_not_found),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                insets.getInsets(WindowInsetsCompat.Type.systemGestures()) else Insets.NONE

            // статусбар — в паддинг заголовка
            binding.titleBar.updatePadding(top = sys.top)

            val sdk = Build.VERSION.SDK_INT
            val bottomInset = when {
                sdk <= Build.VERSION_CODES.O -> 0 // 26- : жёсткий ноль
                sdk >= API_ANDROID_15 -> {        // 35+ : оставляем текущую логику
                    maxOf(sys.bottom, gestures.bottom)
                }
                else -> {                          // 27..34 : универсально (bars + gestures)
                    maxOf(sys.bottom, gestures.bottom)
                }
            }

            // коридор для подстраховки от «улётов»
            val maxH = (resources.displayMetrics.density * 96f).toInt()
            val safeBottom = bottomInset.coerceIn(0, maxH)

            // 2) bottomSafeBar — чисто визуально подгоняем (на 26- он уже = 0)
            if (sdk > Build.VERSION_CODES.O) {
                binding.bottomSafeBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = safeBottom
                }
            }

            insets
        }

        // После первого layout посмотрим реальную высоту в пикселях
        binding.bottomSafeBar.post {
            Log.d(TAG, "bottomSafeBar.height(px) = ${binding.bottomSafeBar.height}")
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    // --- Настройка UI и обработчиков ---
    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            val intent = Intent(this, StartActivity::class.java)
                .putExtra("forceOnboarding", true)
            startActivity(intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@MainActivity, StartActivity::class.java)
                    .putExtra("forceOnboarding", true)
                startActivity(intent)
                finish()
            }
        })

        binding.playPauseButton.setOnClickListener {
            //playPauseButton.setOnClickListener {
            if (isPlaying) {
                startPausedState(isManualAction = true)
            } else {
                // Приложение НЕ АКТИВНО. Это может быть "ВЫКЛ" или "ОЖИДАНИЕ".

                // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
                // Проверяем, запущен ли у нас таймер обратного отсчета
                val isWaiting = (pauseCountdownTimer != null)

                if (isWaiting) {
                    // Сценарий 2: Приложение в режиме ОЖИДАНИЯ (тикает таймер).
                    // Нажимаем -> переходим в ручную ПАУЗУ (ВЫКЛ).
                    startPausedState(isManualAction = true)
                } else {
                    // Сценарий 3: Приложение ВЫКЛЮЧЕНО (таймеров нет).
                    // Нажимаем -> переходим в режим АКТИВНОСТИ.
                    startPlayingState()
                }
            }
        }

        binding.modeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener
            if (isChecked) {
                val today = getTodayDateString()
                val lastActivationDate = prefs.getString("last_stealth_activation_date", "")
                if (today != lastActivationDate) {
                    showStealthModeDialog()
                } else {
                    prefs.edit().putBoolean("stealth_mode_enabled", true).apply()
                }
            } else {
                prefs.edit().putBoolean("stealth_mode_enabled", false).apply()
                val modeText = getString(R.string.regular)
                val message = "${getString(R.string.mode_change_to)}: $modeText"
                //Toast.makeText(this, "Stealth mode disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.optionsButton.setOnClickListener { view ->
            val wrapper = ContextThemeWrapper(this, R.style.AppPopupMenu_ForceLight)
            val popup = PopupMenu(wrapper, view)
            popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

            // чёрный текст
            for (i in 0 until popup.menu.size()) {
                val item = popup.menu.getItem(i)
                val s = android.text.SpannableString(item.title)
                s.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.BLACK),
                    0, s.length, 0
                )
                item.title = s
            }

            //popup.show()
            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                // --- ОБНОВЛЕННАЯ ЛОГИКА МЕНЮ ---
                when (menuItem.itemId) {
                    // Убрали обработчики для disclaimer и privacy
                    R.id.menu_contact -> sendEmail() // <-- ДОБАВЛЕНО
                    R.id.menu_share -> shareAppLink()
                    R.id.menu_faq -> openInfoScreen(R.string.menu_faq, R.drawable.my_faq_image, R.string.faq_text)
                    R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                }
                true
            }
            try {
                val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
                fieldMPopup.isAccessible = true
                val mPopup = fieldMPopup.get(popup)
                mPopup.javaClass
                    .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                    .invoke(mPopup, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing menu icons.", e)
            } finally {
                popup.show()
            }
        }
    }

    private fun showStealthModeDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.stealth_dialog_title)
            .setMessage(R.string.stealth_dialog_message)
            .setPositiveButton(R.string.common_ok) { _, _ -> showRewardedAd() }
            .setNegativeButton(R.string.common_cancel) { _, _ ->
                binding.modeSwitch.isChecked = false
            }
            .setOnCancelListener { binding.modeSwitch.isChecked = false }
            .show()
    }

    private fun updateStealthSwitchState() {
        binding.modeSwitch.isChecked = isStealthActiveToday()
        //binding.modeSwitch.isChecked = prefs.getBoolean("stealth_mode_enabled", false) &&
        //        prefs.getString("last_stealth_activation_date", "") == getTodayDateString()
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
        val appPackageName = packageName
        val shareText = "Попробуй это крутое приложение: https://apps.rustore.ru/app/com.divinegames.mmover"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    // --- Жизненный цикл Activity ---
    override fun onResume() {
        super.onResume()
        restoreUserSettings()
        binding.movingBackground.loadBackgroundWithCallback {
            android.util.Log.d("MainActivity", "Фон повторно загружен при onResume")
        }
        //        binding.movingBackground.loadBackground()
        updateStealthSwitchState()
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            startPausedState(isManualAction = true)
        }
        // Останавливаем все запланированные Runnable, чтобы избежать утечек
        uiHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseCountdownTimer?.cancel()
        pauseCountdownTimer = null
        uiHandler.removeCallbacksAndMessages(null)

        // Безопасное удаление баннера
        yandexTopBanner?.let { banner ->
            (banner.parent as? android.view.ViewGroup)?.removeView(banner)
            banner.destroy()
        }
        yandexTopBanner = null

        yandexBottomBanner?.let { banner ->
            (banner.parent as? android.view.ViewGroup)?.removeView(banner)
            banner.destroy()
        }
        yandexBottomBanner = null

        rewardedAdLoader?.setAdLoadListener(null)
        rewardedAdLoader = null
        rewardedAd?.setAdEventListener(null)
        rewardedAd = null

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activeTimerJob?.cancel()
        pauseTimerJob?.cancel()
        stopVibration()
    }

    // --- Вспомогательные функции ---
    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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

    private fun showRewardedAd() {
        val ad = rewardedAd
        if (ad == null) {
            Toast.makeText(this, "Реклама не готова, попробуйте позже", Toast.LENGTH_SHORT).show()
            // Сбрасываем свитч, так как реклама не показана
            binding.modeSwitch.isChecked = false
            loadRewardedAd()
            return
        }

        ad.setAdEventListener(object : RewardedAdEventListener {
            override fun onAdShown() {
                Log.d(TAG, "Yandex rewarded shown")
            }

            override fun onAdFailedToShow(adError: AdError) {
                Log.e(TAG, "Yandex rewarded failed to show: ${adError.description}")
                rewardedAd = null
                binding.modeSwitch.isChecked = false
                loadRewardedAd()
            }

            override fun onAdDismissed() {
                // Очищаем рекламу и загружаем новую
                rewardedAd?.setAdEventListener(null)
                rewardedAd = null
                loadRewardedAd()
            }

            override fun onAdClicked() {}

            override fun onAdImpression(impressionData: ImpressionData?) {}

            // ✅ ГЛАВНОЕ ИЗМЕНЕНИЕ: Выдаем награду ТОЛЬКО здесь
            override fun onRewarded(reward: com.yandex.mobile.ads.rewarded.Reward) {
                Log.d(TAG, "User rewarded: ${reward.amount} ${reward.type}")

                // Сохраняем логику "успеха"
                prefs.edit()
                    .putBoolean("stealth_mode_enabled", true)
                    .putString("last_stealth_activation_date", getTodayDateString())
                    .apply()

                binding.modeSwitch.isChecked = true
                Toast.makeText(this@MainActivity, "Stealth mode activated!", Toast.LENGTH_SHORT).show()
            }
        })

        ad.show(this)
    }

    /*
    private fun showPermissionsDialog() {
        try {
            // Создаем интент, который ведет на экран разрешений для нашего приложения
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            intent.putExtra("extra_pkgname", packageName)

            Toast.makeText(this, "Please grant 'Display pop-up windows' permission", Toast.LENGTH_LONG).show()
            startActivity(intent)
        } catch (e: Exception) {
            // Если что-то пошло не так (например, это не Xiaomi), просто открываем стандартные настройки
            Log.e("XiaomiPermissions", "Failed to open MIUI permissions", e)
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        }
    }
     */

    private fun setupYandexTopBanner() {
        val container = binding.BannerViewTop

        if (yandexTopBanner == null) {
            yandexTopBanner = BannerAdView(this).apply {
                setAdUnitId("demo-banner-yandex")   // или твой реальный ID
                val displayMetrics = resources.displayMetrics
                val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
                val size = BannerAdSize.stickySize(this@MainActivity, adWidth)
                setAdSize(size)

                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        container.visibility = View.VISIBLE
                        Log.d(TAG, "Yandex TOP banner loaded")
                    }

                    override fun onAdFailedToLoad(adRequestError: AdRequestError) {
                        Log.e(TAG, "Yandex TOP banner failed: ${adRequestError.description}")
                        container.visibility = View.GONE
                    }

                    override fun onAdClicked() {}
                    override fun onLeftApplication() {}
                    override fun onReturnedToApplication() {}
                    override fun onImpression(impressionData: ImpressionData?) {}
                })
            }

            container.removeAllViews()
            container.addView(yandexTopBanner)
        }

        val adRequest = AdRequest.Builder().build()
        yandexTopBanner?.loadAd(adRequest)
    }

    private fun setupYandexBottomBanner() {
        val container = binding.BannerViewBottom

        if (yandexBottomBanner == null) {
            yandexBottomBanner = BannerAdView(this).apply {
                setAdUnitId("R-M-17944676-2")
                val displayMetrics = resources.displayMetrics
                val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
                val size = BannerAdSize.stickySize(this@MainActivity, adWidth)
                setAdSize(size)

                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        container.visibility = View.VISIBLE
                        Log.d(TAG, "Yandex BOTTOM banner loaded")
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        Log.e(TAG, "Bottom banner failed: ${error.description}")
                        container.visibility = View.GONE
                    }

                    override fun onAdClicked() {}
                    override fun onLeftApplication() {}
                    override fun onReturnedToApplication() {}
                    override fun onImpression(impressionData: ImpressionData?) {}
                })
            }

            container.removeAllViews()
            container.addView(yandexBottomBanner)
        }

        val adRequest = AdRequest.Builder().build()
        yandexBottomBanner?.loadAd(adRequest)
    }

    private fun setupYandexRewarded() {
        rewardedAdLoader = RewardedAdLoader(this).apply {
            setAdLoadListener(object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Yandex rewarded loaded")
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    Log.e(TAG, "Yandex rewarded failed: ${error.description}")
                    rewardedAd = null
                }
            })
        }
        loadRewardedAd()
    }

    private fun loadRewardedAd() {
        val config = AdRequestConfiguration.Builder("R-M-17944676-3").build()
        rewardedAdLoader?.loadAd(config)
    }

    private fun isStealthActiveToday(): Boolean {
        val enabled = prefs.getBoolean("stealth_mode_enabled", false)
        val lastDate = prefs.getString("last_stealth_activation_date", "")
        return enabled && lastDate == getTodayDateString()
    }
}