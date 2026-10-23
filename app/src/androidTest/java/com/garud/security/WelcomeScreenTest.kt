package com.garud.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garud.security.ui.screens.WelcomeScreen
import com.garud.security.ui.theme.GarudTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeSkipInvokesLocalContinueImmediately() {
        var skipped = false
        composeRule.setContent {
            GarudTheme {
                WelcomeScreen(onSkip = { skipped = true }, onAuthenticationSuccess = {})
            }
        }

        composeRule.onNodeWithText("Your digital safety companion.").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").performClick()
        assertTrue(skipped)
    }

    @Test
    fun welcomeOffersDemoPasswordAndForgotPasswordHelp() {
        composeRule.setContent {
            GarudTheme {
                WelcomeScreen(onSkip = {}, onAuthenticationSuccess = {})
            }
        }

        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Demo password: 123456").assertIsDisplayed()
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
    }
}
