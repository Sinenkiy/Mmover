package com.divinegames.mmover

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.preference.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HelpScreensTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun scrollHintPagesInstructionAndHidesAtEnd() {
        val intent = Intent(context, StartActivity::class.java).putExtra("forceOnboarding", true)
        ActivityScenario.launch<StartActivity>(intent).use { scenario ->
            awaitLayout(scenario, R.id.startButton)
            var canScroll = false
            scenario.onActivity {
                val scroll = it.findViewById<androidx.core.widget.NestedScrollView>(R.id.onboardingScroll)
                canScroll = scroll.canScrollVertically(1)
                assertEquals(if (canScroll) View.VISIBLE else View.INVISIBLE,
                    it.findViewById<View>(R.id.scrollHint).visibility)
            }
            if (canScroll) {
                onView(withId(R.id.scrollHint)).perform(click())
                onView(withId(R.id.faqButton)).perform(scrollTo())
                scenario.onActivity {
                    val scroll = it.findViewById<androidx.core.widget.NestedScrollView>(R.id.onboardingScroll)
                    scroll.scrollTo(0, scroll.getChildAt(0).height)
                    assertTrue(scroll.scrollY > 0)
                    assertEquals(View.INVISIBLE, it.findViewById<View>(R.id.scrollHint).visibility)
                    scroll.scrollTo(0, 0)
                    assertEquals(View.VISIBLE, it.findViewById<View>(R.id.scrollHint).visibility)
                }
            }
        }
    }

    @Test fun faqExpansionSurvivesRecreationAndCanBeCollapsed() {
        ActivityScenario.launch<InfoActivity>(faqIntent()).use { scenario ->
            awaitLayout(scenario, R.id.faqList)
            screenshot("faq-collapsed")
            scenario.onActivity {
                val list = it.findViewById<LinearLayout>(R.id.faqList)
                assertEquals(5, list.childCount)
                assertEquals(View.GONE, list.getChildAt(0).findViewById<View>(R.id.answerText).visibility)
                list.getChildAt(0).findViewById<View>(R.id.questionButton).performClick()
                list.getChildAt(2).findViewById<View>(R.id.questionButton).performClick()
            }
            scenario.recreate()
            awaitLayout(scenario, R.id.faqList)
            scenario.onActivity {
                val list = it.findViewById<LinearLayout>(R.id.faqList)
                assertEquals(View.VISIBLE, list.getChildAt(0).findViewById<View>(R.id.answerText).visibility)
                assertEquals(View.GONE, list.getChildAt(1).findViewById<View>(R.id.answerText).visibility)
                assertEquals(View.VISIBLE, list.getChildAt(2).findViewById<View>(R.id.answerText).visibility)
                list.getChildAt(2).findViewById<View>(R.id.questionButton).performClick()
                assertEquals(View.GONE, list.getChildAt(2).findViewById<View>(R.id.answerText).visibility)
            }
            instrumentation.waitForIdleSync()
            screenshot("faq-expanded")
        }
    }

    @Test fun faqFromStartReturnsWithoutCompletingOnboardingAndStartRemainsReachable() {
        val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val previous = prefs.all["lastSeenVersionCode"]
        val intent = Intent(context, StartActivity::class.java).putExtra("forceOnboarding", true)
        ActivityScenario.launch<StartActivity>(intent).use { scenario ->
            awaitLayout(scenario, R.id.startButton)
            screenshot("start")
            scenario.onActivity {
                val button = it.findViewById<View>(R.id.startButton)
                val scroll = it.findViewById<View>(R.id.onboardingScroll)
                assertTrue("Scrollable text must stay above the fixed button", scroll.bottom <= button.top)
                assertTrue("Start must fit the screen", button.bottom <= (button.parent as View).height)
            }
            onView(withId(R.id.faqButton)).perform(scrollTo(), click())
            onView(withId(R.id.faqList)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.startButton)).check(matches(isDisplayed()))
            assertEquals(previous, prefs.all["lastSeenVersionCode"])
            scenario.recreate()
            onView(withId(R.id.startButton)).check(matches(isDisplayed()))
        }
    }

    @Test fun genericInformationStillDisplaysRequestedText() {
        val intent = Intent(context, InfoActivity::class.java)
            .putExtra("EXTRA_TEXT_RES_ID", R.string.help_compatibility)
            .putExtra("EXTRA_TITLE_RES_ID", R.string.app_name)
        ActivityScenario.launch<InfoActivity>(intent).use { scenario ->
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.faqList).visibility)
                assertEquals(it.getString(R.string.help_compatibility),
                    it.findViewById<TextView>(R.id.infoTextView).text.toString())
            }
        }
    }

    @Test fun everyLanguageHasMatchingNonEmptyQuestionsAndAnswers() {
        for (language in listOf("en", "ru", "de", "fr")) {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language))
            val resources = context.createConfigurationContext(configuration).resources
            val questions = resources.getStringArray(R.array.faq_questions)
            val answers = resources.getStringArray(R.array.faq_answers)
            assertEquals(language, 5, questions.size)
            assertEquals(language, questions.size, answers.size)
            assertTrue(language, questions.all { it.isNotBlank() } && answers.all { it.isNotBlank() })
        }
    }

    @Test fun translatedScreensFitAndRemainScrollable() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "Locale.Helper.Selected.Language"
        val previous = prefs.getString(key, null)
        val previousLocale = Locale.getDefault()
        try {
            for (language in listOf("en", "ru", "de", "fr")) {
                LocaleHelper.setNewLocale(context, language)
                val intent = Intent(context, StartActivity::class.java).putExtra("forceOnboarding", true)
                ActivityScenario.launch<StartActivity>(intent).use { scenario ->
                    awaitLayout(scenario, R.id.startButton)
                    screenshot("start-$language")
                    onView(withId(R.id.faqButton)).perform(scrollTo())
                    onView(withId(R.id.startButton)).check(matches(isDisplayed()))
                    screenshot("steps-$language")
                }
                ActivityScenario.launch<InfoActivity>(faqIntent()).use { scenario ->
                    awaitLayout(scenario, R.id.faqList)
                    scenario.onActivity {
                        val list = it.findViewById<LinearLayout>(R.id.faqList)
                        assertTrue(list.width <= (560 * it.resources.displayMetrics.density).toInt())
                        repeat(list.childCount) { index ->
                            val text = list.getChildAt(index).findViewById<TextView>(R.id.questionText)
                            assertTrue(text.width > 0)
                            repeat(text.lineCount) { line -> assertEquals(0, text.layout.getEllipsisCount(line)) }
                        }
                    }
                    screenshot("faq-$language")
                }
            }
        } finally {
            prefs.edit().apply { if (previous == null) remove(key) else putString(key, previous) }.commit()
            Locale.setDefault(previousLocale)
        }
    }

    private fun faqIntent() = Intent(context, InfoActivity::class.java)
        .putExtra("EXTRA_TITLE_RES_ID", R.string.menu_faq)
        .putExtra("EXTRA_TEXT_RES_ID", R.string.faq_text)

    private fun <T : android.app.Activity> awaitLayout(scenario: ActivityScenario<T>, id: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<View>(id).height > 0 && it.hasWindowFocus() }
            if (ready) {
                instrumentation.waitForIdleSync()
                return
            }
            SystemClock.sleep(50)
        }
        fail("Help screen was not laid out")
    }

    private fun screenshot(name: String) {
        // Let the system window transition finish before capturing the display.
        // Assertions above use layout/idle synchronization, not this visual-only delay.
        SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(context.getExternalFilesDir(null), "help-screens").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
