package ua.bossly.tools.translit

import android.graphics.Bitmap
import androidx.test.runner.screenshot.Screenshot
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

/**
 * Using the watcher, add following:
@get:Rule
val mScreenshotWatcher = ScreenshotWatcher()
// be sure to add these permission ot AndroidManifest.xml
@get:Rule
val mGrantPermissionRule = GrantPermissionRule.grant(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
@get:Rule
val mRuleChain = RuleChain.outerRule(mGrantPermissionRule)
.around(mScreenshotWatcher)
.around(activityRule)
 *
 * Created on 02.10.2020.
 * Copyright by oleg
 */
class ScreenshotWatcher : TestWatcher() {
    override fun succeeded(description: Description) {
        captureScreenshot(description.methodName.toString() + "_ok")
    }

    private fun captureScreenshot(name: String) {
        val capture = Screenshot.capture()
        capture.format = Bitmap.CompressFormat.PNG
        capture.name = name
        try {
            capture.process()
        } catch (ex: IOException) {
            throw IllegalStateException(ex)
        }
    }

    override fun failed(e: Throwable?, description: Description) {
        captureScreenshot(description.methodName.toString() + "_fail")
    }
}