package com.zwiedzak.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.PlaceCategory
import com.zwiedzak.ui.CategoriesState
import com.zwiedzak.ui.PlaceActionState
import com.zwiedzak.ui.VisitHistoryState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastVisitInEditTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lastVisitAppearsInLocalTimeEvenWhileHistoryLoads() {
        val timestamp = "2026-11-16T14:30:25.123456Z"
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(timestamp.take(19))!!
        val expected = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(utc)
        showEdit(Place(id = "1", name = "Miejsce", lastVisitedAt = timestamp), true)
        compose.onNodeWithText("Ostatnia wizyta: $expected").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missingVisitShowsNoRecordedVisit() {
        showEdit(Place(id = "1", name = "Miejsce"), true)
        compose.onNodeWithText("Ostatnia wizyta: Brak zapisanej wizyty")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun lastVisitIsHiddenWithoutAuthentication() {
        showEdit(Place(id = "1", name = "Miejsce", lastVisitedAt = "2026-11-16T14:30:25Z"), false)
        compose.onNodeWithText("Ostatnia wizyta:", substring = true).assertDoesNotExist()
    }

    @Test
    fun recordingAnotherVisitRequiresConfirmation() {
        var records = 0
        val place = Place(id = "1", name = "Miejsce", visitStatus = "avoid")
        compose.setContent {
            AddPlaceDialog(
                initialLatitude = 0.0,
                initialLongitude = 0.0,
                actionState = PlaceActionState.Idle,
                categoriesState = CategoriesState.Success(listOf(PlaceCategory("inne", "Inne"))),
                editPlace = place,
                onDismiss = {},
                onRetryCategories = {},
                onSubmit = { _, _, _ -> },
                onRecordVisit = { records++ }
            )
        }
        compose.onNodeWithText("Zapisz kolejną wizytę").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, records) }
        compose.onNode(hasText("Kontynuuj") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, records) }
    }

    private fun showEdit(place: Place, authenticated: Boolean) {
        compose.setContent {
            AddPlaceDialog(
                initialLatitude = 0.0,
                initialLongitude = 0.0,
                actionState = PlaceActionState.Idle,
                categoriesState = CategoriesState.Success(listOf(PlaceCategory("inne", "Inne"))),
                editPlace = place,
                isAuthenticated = authenticated,
                historyState = VisitHistoryState.Loading,
                onDismiss = {},
                onRetryCategories = {},
                onSubmit = { _, _, _ -> }
            )
        }
    }
}
