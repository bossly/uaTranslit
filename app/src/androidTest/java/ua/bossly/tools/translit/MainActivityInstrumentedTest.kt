package ua.bossly.tools.translit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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

    private val uiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule(order = 0)
    val localeTestRule = LocaleTestRule()

    @get:Rule(order = 1)
    val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        AndroidComposeTestRule(
            ActivityScenarioRule(
                Intent(
                    ApplicationProvider.getApplicationContext(),
                    MainActivity::class.java
                ).apply {
                    putExtra(MainActivity.EXTRA_SUPPRESS_SAVE_FEEDBACK, true)
                }
            ),
            activityProvider = { rule ->
                var activity: MainActivity? = null
                rule.scenario.onActivity { activity = it }
                checkNotNull(activity) { "MainActivity did not launch in ActivityScenarioRule" }
            }
        )

    private fun str(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun before() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        val extras: Bundle = InstrumentationRegistry.getArguments()
        Log.d("extras", extras.toString())
    }

    @Test
    fun homeScreen() {
        uiDevice.waitForIdle()
        Screengrab.screenshot("screen1")
    }

    @Test
    fun makeTranslit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val defaultTransform = TransformTypes.types(context).first()
        val expected = WordTransformation.transform("Тарас Бульба", defaultTransform)

        uiDevice.waitForIdle()
        Screengrab.screenshot("screen1")

        val input = composeTestRule.onNodeWithTag("input", true)
        input.performTextInput("Тарас Бульба")
        Espresso.closeSoftKeyboard()

        composeTestRule.onNodeWithTag("output", true)
            .assertTextEquals(expected)

        uiDevice.waitForIdle()
        Screengrab.screenshot("screen2")
    }

    @Test
    fun showSelector() {
        composeTestRule.onNodeWithTag("selector", true)
            .performClick()

        composeTestRule.waitForIdle()

        uiDevice.waitForIdle(15_000)
        Screengrab.screenshot("screen3")
    }

    @Test
    fun historyScreenWithItems() {
        val sampleInput = "Привіт"

        uiDevice.waitForIdle()
        composeTestRule.onNodeWithTag("input", true).performTextInput(sampleInput)
        Espresso.closeSoftKeyboard()
        composeTestRule.onNodeWithContentDescription(str(R.string.save_to_history)).performClick()
        composeTestRule.waitForIdle()

        waitUntilHistoryShowsInput(sampleInput)

        composeTestRule.onNodeWithTag("history_list", true).assertIsDisplayed()
        composeTestRule.onNodeWithText(sampleInput, substring = true).assertIsDisplayed()

        uiDevice.waitForIdle()
        Screengrab.screenshot("screen_4")

        pressBackFromHistory()
    }

    private fun openHistory() {
        composeTestRule.onNodeWithContentDescription(str(R.string.cd_open_history)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun pressBackFromHistory() {
        composeTestRule.onNodeWithContentDescription(str(R.string.cd_navigate_up)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun waitUntilHistoryShowsInput(inputPhrase: String) {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            openHistory()
            val found =
                composeTestRule.onAllNodesWithText(inputPhrase, substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            if (!found) {
                pressBackFromHistory()
            }
            found
        }
    }
}
