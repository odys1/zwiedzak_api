package com.zwiedzak.ui.screens

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.PlaceImage
import com.zwiedzak.ui.PlaceActionState
import com.zwiedzak.ui.PlaceDetailsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceDetailContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val place = Place(
        id = "1",
        name = "Miejsce",
        description = "Opis miejsca",
        latitude = 52.0,
        longitude = 21.0,
        hasAccommodation = 1,
        hasFood = 0,
        visitStatus = "avoid",
        lastVisitedAt = "2026-11-16T14:30:25Z",
        images = listOf(PlaceImage("invalid://image"))
    )

    @Test
    fun detailsAreReadOnlyAndEditRequiresExplicitClick() {
        var editClicks = 0
        compose.setContent {
            PlaceDetailContent(place, true, true, PlaceDetailsState.Success(place), PlaceActionState.Idle,
                onEdit = { editClicks++ })
        }

        compose.onNodeWithText("Edytuj miejsce").assertIsDisplayed()
        compose.onNodeWithText("Edycja miejsca").assertDoesNotExist()
        compose.onNodeWithText("Odwiedzone · nie wracam").performScrollTo().assertHasNoClickAction()
        compose.onNodeWithText("Nocleg: Tak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Jedzenie: Nie").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ostatnia wizyta: ${formatVisitTime(place.lastVisitedAt!!)}")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Opis miejsca").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Współrzędne geograficzne").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Oznacz jako odwiedzone").assertDoesNotExist()
        compose.onNodeWithText("Zapisz ocenę").assertDoesNotExist()
        compose.onNodeWithText("Edytuj miejsce").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, editClicks) }
    }

    @Test
    fun anonymousDetailsDoNotExposePersonalDataOrEdit() {
        compose.setContent {
            PlaceDetailContent(place, false, true, PlaceDetailsState.Success(place), PlaceActionState.Idle, {})
        }

        compose.onNodeWithText("Edytuj miejsce").assertDoesNotExist()
        compose.onNodeWithContentDescription("Otwórz zdjęcie 1").assertDoesNotExist()
        compose.onNodeWithText("Odwiedzone · nie wracam").assertDoesNotExist()
        compose.onNodeWithText("Ostatnia wizyta:", substring = true).assertDoesNotExist()
    }
}
