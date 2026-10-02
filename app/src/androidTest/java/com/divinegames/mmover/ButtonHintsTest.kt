package com.divinegames.mmover

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.divinegames.mmover.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonHintsTest {
    @Test fun allFourButtonsShowHintsWithoutClickingAndDismissOnPause() {
        ActivityScenario.launch(InfoActivity::class.java).use { scenario ->
            var clicks = 0
            lateinit var decor: View
            val labels = mutableMapOf<Int, String>()
            val ids = listOf(R.id.settingsButton, R.id.backButton, R.id.optionsButton, R.id.removeAdsButton)
            scenario.onActivity { activity ->
                decor = activity.window.decorView
                val binding = ActivityMainBinding.inflate(activity.layoutInflater)
                activity.setContentView(binding.root)
                activity.applyHelpInsets(binding.root)
                val hints = ButtonHints(activity)
                ids.forEach { id ->
                    val button = activity.findViewById<View>(id)
                    hints.bind(button)
                    labels[id] = button.contentDescription.toString()
                    button.setOnClickListener { clicks++ }
                }
            }
            ids.forEach { id ->
                onView(withId(id)).perform(longClick())
                onView(withText(labels.getValue(id))).inRoot(withDecorView(org.hamcrest.Matchers.not(org.hamcrest.Matchers.`is`(decor)))).check(matches(isDisplayed()))
                scenario.onActivity { assertEquals(0, clicks) }
            }
            onView(withId(R.id.optionsButton)).perform(longClick())
            onView(withText(labels.getValue(R.id.optionsButton))).inRoot(withDecorView(org.hamcrest.Matchers.not(org.hamcrest.Matchers.`is`(decor))))
                .check(matches(isDisplayed()))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "hint-checks")
                .apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(dir, "hint.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            android.os.SystemClock.sleep(3200)
            onView(withText(labels.getValue(R.id.optionsButton))).check(doesNotExist())
            onView(withId(R.id.optionsButton)).perform(longClick())
            onView(withText(labels.getValue(R.id.optionsButton)))
                .inRoot(withDecorView(org.hamcrest.Matchers.not(org.hamcrest.Matchers.`is`(decor))))
                .check(matches(isDisplayed()))
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(withText(labels.getValue(R.id.optionsButton))).check(doesNotExist())
        }
    }
}
