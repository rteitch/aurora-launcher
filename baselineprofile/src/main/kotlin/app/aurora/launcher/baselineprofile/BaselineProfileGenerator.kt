package app.aurora.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateLauncherBaselineProfile() = baselineProfileRule.collect(
        packageName = "app.aurora.launcher",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe((w * 0.8).toInt(), h / 2, (w * 0.2).toInt(), h / 2, 16)
        device.waitForIdle()
    }
}
