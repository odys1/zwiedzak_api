package com.zwiedzak.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.PlaceImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlacePhotoGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun photoStripScrollsToLaterImages() {
        val images = (1..10).map { PlaceImage("invalid://$it", "Zdjęcie $it") }
        compose.setContent {
            PlacePhotoGallery(Place(id = "1", name = "Miejsce", images = images))
        }

        compose.onNodeWithTag("photo-strip").performScrollToIndex(9)
        compose.onNodeWithContentDescription("Otwórz zdjęcie 10").performClick()
        compose.onNodeWithText("10 / 10").assertIsDisplayed()
    }

    @Test
    fun selectedPhotoOpensAndGalleryCanBeBrowsedAndClosed() {
        val place = Place(
            id = "1",
            name = "Miejsce",
            images = listOf(
                PlaceImage("invalid://one", "Pierwsze"),
                PlaceImage("invalid://two", "Drugie"),
                PlaceImage("invalid://three", "Trzecie")
            )
        )
        compose.setContent { Box(Modifier.fillMaxSize()) { PlacePhotoGallery(place) } }

        compose.onNodeWithContentDescription("Otwórz zdjęcie 2").performClick()
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithText("Drugie").assertIsDisplayed()
        compose.onNodeWithTag("photo-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithText("Trzecie").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zamknij galerię").performClick()
        compose.onNodeWithText("3 / 3").assertDoesNotExist()
    }

    @Test
    fun confirmationAddsPhotoOnlyWhenAccepted() {
        var showing by mutableStateOf(true)
        var confirmed = 0
        compose.setContent {
            if (showing) {
                ConfirmAddPhotoDialog(
                    onConfirm = { confirmed++; showing = false },
                    onDismiss = { showing = false }
                )
            }
        }

        compose.onNodeWithText("Anuluj").performClick()
        assertEquals(0, confirmed)
        compose.runOnIdle { showing = true }
        compose.onNode(hasText("Dodaj zdjęcie") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }
}
