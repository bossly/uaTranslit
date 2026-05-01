package ua.bossly.tools.translit

import android.os.Bundle
import android.util.Log
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
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
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
}
