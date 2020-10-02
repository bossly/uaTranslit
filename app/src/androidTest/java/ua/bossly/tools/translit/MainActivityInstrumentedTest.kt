package ua.bossly.tools.translit

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    }
}