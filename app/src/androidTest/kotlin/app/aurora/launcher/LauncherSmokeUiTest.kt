package app.aurora.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aurora.launcher.core.designsystem.R as DesignR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSmokeUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<LauncherActivity>()

    @Test
    fun launcherCompletesOnboardingAndRendersHomeWorkspace() {
        val appName = composeRule.activity.getString(DesignR.string.aurora_app_name)
        val previewCta = composeRule.activity.getString(DesignR.string.onboarding_cta_preview)
        val enterHomeCta = composeRule.activity.getString(DesignR.string.onboarding_cta_enter_home)
        val previewBanner = composeRule.activity.getString(DesignR.string.preview_banner_title)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(appName, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(previewBanner, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        if (composeRule.onAllNodesWithText(previewCta).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(previewCta).assertIsDisplayed().performClick()
        } else if (composeRule.onAllNodesWithText(enterHomeCta).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(enterHomeCta).assertIsDisplayed().performClick()
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(previewBanner, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Settings", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Setelan", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        val hasWorkspaceContent =
            composeRule.onAllNodesWithText(previewBanner, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Settings", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Setelan", substring = true).fetchSemanticsNodes().isNotEmpty()

        assertTrue("Expected Home workspace or Preview Mode banner to render after onboarding", hasWorkspaceContent)
    }
}
