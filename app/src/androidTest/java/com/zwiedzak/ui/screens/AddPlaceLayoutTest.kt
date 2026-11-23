package com.zwiedzak.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.DuplicatePlace
import com.zwiedzak.data.model.PlaceCategory
import com.zwiedzak.ui.CategoriesState
import com.zwiedzak.ui.PlaceActionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddPlaceLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun checkboxesAreVerticallyStackedAndDistinctCheckboxIsRemoved() {
        compose.setContent {
            AddPlaceDialog(
                initialLatitude = 52.0,
                initialLongitude = 21.0,
                actionState = PlaceActionState.Idle,
                categoriesState = CategoriesState.Success(emptyList()),
                onDismiss = {},
                onRetryCategories = {},
                onSubmit = { _, _, _ -> }
            )
        }

        compose.onNodeWithText("Nie wracam").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Oznacz jako odwiedzone").performScrollTo().assertIsOn()
        compose.onNodeWithText("Jest hotel / nocleg").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Jest restauracja / jedzenie").performScrollTo().performClick().assertIsOn()
        val visited = compose.onNodeWithText("Oznacz jako odwiedzone").getUnclippedBoundsInRoot()
        val avoid = compose.onNodeWithText("Nie wracam").getUnclippedBoundsInRoot()
        val lodging = compose.onNodeWithText("Jest hotel / nocleg").getUnclippedBoundsInRoot()
        val food = compose.onNodeWithText("Jest restauracja / jedzenie").getUnclippedBoundsInRoot()
        assertTrue(visited.bottom <= avoid.top)
        assertTrue(avoid.bottom <= lodging.top)
        assertTrue(lodging.bottom <= food.top)
        assertEquals(visited.left, avoid.left)
        assertEquals(visited.left, lodging.left)
        assertEquals(lodging.left, food.left)
        compose.onNodeWithText("Oznacz jako odwiedzone").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Nie wracam").assertIsOff()
        compose.onNodeWithText("Potwierdzam, że to odrębne miejsce").assertDoesNotExist()
    }

    @Test
    fun creatingWithAvoidSubmitsVisitedAndAvoidStatus() {
        var submitted: CreatePlaceRequest? = null
        compose.setContent {
            AddPlaceDialog(
                initialLatitude = 52.0,
                initialLongitude = 21.0,
                actionState = PlaceActionState.Idle,
                categoriesState = CategoriesState.Success(listOf(PlaceCategory("inne", "Inne"))),
                onDismiss = {},
                onRetryCategories = {},
                onSubmit = { request, _, _ -> submitted = request }
            )
        }
        compose.onNodeWithText("Nazwa (2–150 znaków)").performTextInput("Miejsce")
        compose.onNodeWithText("Kategoria").performClick()
        compose.onNodeWithText("Inne").performClick()
        compose.onNodeWithText("Nie wracam").performScrollTo().performClick()
        compose.onNodeWithText("Dodaj").performClick()
        compose.runOnIdle {
            assertEquals(true, submitted?.visited)
            assertEquals("avoid", submitted?.createVisitStatus)
        }
    }

    @Test
    fun duplicateRequiresExplicitConfirmationForCurrentForm() {
        var state by mutableStateOf<PlaceActionState>(PlaceActionState.Idle)
        val requests = mutableListOf<CreatePlaceRequest>()
        compose.setContent {
            AddPlaceDialog(
                initialLatitude = 52.0,
                initialLongitude = 21.0,
                actionState = state,
                categoriesState = CategoriesState.Success(listOf(PlaceCategory("inne", "Inne"))),
                onDismiss = {},
                onRetryCategories = {},
                onSubmit = { request, _, _ ->
                    requests.add(request)
                    state = PlaceActionState.CreateConflict(
                        request.copy(confirmDistinct = false),
                        listOf(DuplicatePlace("Zamek", "Rynek", 27))
                    )
                }
            )
        }

        compose.onNodeWithText("Nazwa (2–150 znaków)").performTextInput("Miejsce")
        compose.onNodeWithText("Kategoria").performClick()
        compose.onNodeWithText("Inne").performClick()
        compose.onNodeWithText("Dodaj").performClick()
        compose.runOnIdle { assertFalse(requests.last().confirmDistinct) }
        compose.onNodeWithText("Sprawdź podobne").performClick()
        compose.onNodeWithText("Zamek (27 m)\nRynek").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests.size) }
        compose.onNodeWithText("To inne miejsce").performClick()
        compose.runOnIdle { assertTrue(requests.last().confirmDistinct) }
    }

    @Test
    fun changedFormDoesNotReuseDuplicateConfirmation() {
        var state by mutableStateOf<PlaceActionState>(PlaceActionState.Idle)
        val requests = mutableListOf<CreatePlaceRequest>()
        compose.setContent {
            AddPlaceDialog(
                initialLatitude = 52.0,
                initialLongitude = 21.0,
                actionState = state,
                categoriesState = CategoriesState.Success(listOf(PlaceCategory("inne", "Inne"))),
                onDismiss = {},
                onRetryCategories = {},
                onSubmit = { request, _, _ ->
                    requests.add(request)
                    state = PlaceActionState.CreateConflict(
                        request.copy(confirmDistinct = false),
                        listOf(DuplicatePlace("Zamek", null, 27))
                    )
                }
            )
        }

        compose.onNodeWithText("Nazwa (2–150 znaków)").performTextInput("Miejsce")
        compose.onNodeWithText("Kategoria").performClick()
        compose.onNodeWithText("Inne").performClick()
        compose.onNodeWithText("Dodaj").performClick()
        compose.onNodeWithText("Nazwa (2–150 znaków)").performTextInput(" inne")
        compose.onNodeWithText("Dodaj").performClick()
        compose.runOnIdle {
            assertEquals(2, requests.size)
            assertFalse(requests.last().confirmDistinct)
        }
    }
}
