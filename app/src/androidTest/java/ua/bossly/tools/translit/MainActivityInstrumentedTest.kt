package ua.bossly.tools.translit

import android.os.Bundle
import android.util.Log
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val localeTestRule = LocaleTestRule()

    @Before
    fun before() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        val extras: Bundle = InstrumentationRegistry.getArguments()
        // https://docs.fastlane.tools/getting-started/android/screenshots/#advanced-screengrab
        Log.d("extras", extras.toString())
    }

    @Test
    fun mainScreen() {
        // if selector presented
        onView(withId(android.R.id.text1)).check(matches(isDisplayed()))

        // enter the text
        onView(withId(R.id.inputField)).perform(
            ViewActions.replaceText("Тарас Бульба"),
            ViewActions.closeSoftKeyboard()
        )

        // check result text changed
        onView(withId(R.id.outputField)).check(matches(withText("Taras Bulba")))

        Screengrab.screenshot("home")
    }
}