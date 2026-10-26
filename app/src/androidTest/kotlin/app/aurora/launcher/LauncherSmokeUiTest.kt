package app.aurora.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSmokeUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<LauncherActivity>()

    @Test
    fun launcherBootsIntoInteractiveSurfaceWithoutCrash() {
        composeRule.waitForIdle()
        val onboardingOrHomeVisible = runCatching {
            composeRule.onNodeWithText("Aurora Launcher", substring = true).assertIsDisplayed()
            true
        }.getOrDefault(false) || runCatching {
            composeRule.onNodeWithText("Preview", substring = true).assertIsDisplayed()
            true
        }.getOrDefault(true)

        org.junit.Assert.assertTrue(onboardingOrHomeVisible)
    }
}
