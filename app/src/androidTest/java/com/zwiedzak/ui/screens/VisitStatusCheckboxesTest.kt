package com.zwiedzak.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwiedzak.data.model.VisitStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisitStatusCheckboxesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun avoidChecksVisitedAndUncheckingVisitedClearsBoth() {
        var status by mutableStateOf(VisitStatus.PLANNED)
        compose.setContent {
            Column {
                VisitStatusCheckboxes(status, showAvoid = true, enabled = true, onChange = { status = it })
            }
        }

        compose.onNodeWithText("Nie wracam").performClick().assertIsOn()
        compose.onNodeWithText("Oznacz jako odwiedzone").assertIsOn()
        compose.onNodeWithText("Nie wracam").performClick().assertIsOff()
        compose.onNodeWithText("Oznacz jako odwiedzone").assertIsOn()
        compose.onNodeWithText("Nie wracam").performClick().assertIsOn()
        compose.onNodeWithText("Oznacz jako odwiedzone").performClick().assertIsOff()
        compose.onNodeWithText("Nie wracam").assertIsOff()
    }
}
