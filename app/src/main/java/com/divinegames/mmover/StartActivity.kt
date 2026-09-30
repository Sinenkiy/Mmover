// StartActivity.kt — стабильная версия
package com.divinegames.mmover

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import androidx.core.view.updateLayoutParams
import com.divinegames.mmover.databinding.ActivityOnboardingBinding

class StartActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // 1) Сначала читаем и сразу «сжигаем» флаг force, чтобы он не переиспользовался при поворотах экрана
        val force = intent.getBooleanExtra("forceOnboarding", false)
        intent.removeExtra("forceOnboarding")

        // 2) Версии
        val lastSeenVersionCode = prefs.getInt("lastSeenVersionCode", 0)
        val currentVersionCode  = BuildConfig.VERSION_CODE

        // 3) Решаем, показывать ли онбординг
        val shouldShowOnboarding = force || (currentVersionCode > lastSeenVersionCode)

        if (!shouldShowOnboarding) {
            // Не показываем — сразу на Main
            goToMainActivity()
            return
        }

        // 4) Показываем онбординг
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOnboardingUi()
        applyOnboardingLayoutTweaks()   // твои правки отступов/размеров и т.д.

        // 5) Версию запоминаем только при НЕ форс-показе
        if (!force) {
            prefs.edit().putInt("lastSeenVersionCode", currentVersionCode).apply()
        }
    }

    private fun applyOnboardingLayoutTweaks() {
        if (!this::binding.isInitialized) return

        // 3) ТЕПЕРЬ применяем отступы/размеры (они сохранятся, т.к. layout уже финальный)
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        val dm = resources.displayMetrics
        val screenWidthPx = dm.widthPixels
        val screenHeightDp = (dm.heightPixels / dm.density).toInt()

        // ГРУППЫ ВЫСОТ (подгони пороги под дизайн)
        val isSmall  = screenHeightDp <650
        val isMedium = screenHeightDp in 650..780
        val isLarge  = screenHeightDp >= 780

        // Одинаковый паддинг со всех сторон
        val paddingAllDp = when {
            isSmall  -> 5
            isMedium -> 10
            else     -> 20  // как в XML
        }
        binding.onboardingContainer.setPadding(
            dp(paddingAllDp), dp(paddingAllDp), dp(paddingAllDp), dp(paddingAllDp)
        )

        // 1) Верхний margin для "compatibility note" (releaseNotesTextView)
        binding.titleTextView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = when {
                isSmall  -> dp(5)
                isMedium -> dp(15)
                else     -> dp(30)
            }
        }

        // 2) Нижний margin для кнопки Start
        binding.startButton.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomMargin = when {
                isSmall  -> dp(20)
                isMedium -> dp(40)
                else     -> dp(60)
            }
        }

        // Настройка размеров descriptionImageView в зависимости от версии Android
        val image = binding.descriptionImageView

        val (targetWidthDp, targetHeightDp) = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> 512 to 336
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> 384 to 252
            else -> 256 to 168
        }

        if (isSmall) {
            // Очень низкие экраны — задаём ЖЁСТКИЙ размер картинки
            image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width  = dp(256)          // подгони под реальный макет
                height = dp(168)
            }
        } else {
            val desiredWidthPx = dp(targetWidthDp).coerceAtMost(screenWidthPx - dp(48))
            image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width  = desiredWidthPx
                height = 0 // ВАЖНО: для работы app:layout_constraintDimensionRatio
                // dimensionRatio задан в XML (512:336) — высота посчитается автоматически
            }
        }

        image.requestLayout()
    }

    private fun setupOnboardingUi() {
        //MobileAds.initialize(this) {}
        //findViewById<AdView>(R.id.adViewTop).loadAd(AdRequest.Builder().build())
        //findViewById<AdView>(R.id.adViewBottom).loadAd(AdRequest.Builder().build())

        // 1. Находим наш TextView
        val descriptionTextView = findViewById<TextView>(R.id.descriptionTextView)

        // 2. Получаем строку с HTML-тегами
        val formattedText = getString(R.string.start_screen_description)

        // 3. Превращаем HTML в форматированный текст и устанавливаем его
        descriptionTextView.text = HtmlCompat.fromHtml(formattedText, HtmlCompat.FROM_HTML_MODE_LEGACY)


        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener {
            // Когда пользователь нажимает "Старт", мы помечаем, что первый запуск прошел
            getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("lastSeenVersionCode", BuildConfig.VERSION_CODE)
                .apply()
            goToMainActivity()
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    override fun onStart() {
        super.onStart()
        if (this::binding.isInitialized) applyOnboardingLayoutTweaks()
    }

    override fun onResume() {
        super.onResume()
        if (this::binding.isInitialized) applyOnboardingLayoutTweaks()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // чтобы читать новые extras
        if (this::binding.isInitialized) applyOnboardingLayoutTweaks()
    }
}