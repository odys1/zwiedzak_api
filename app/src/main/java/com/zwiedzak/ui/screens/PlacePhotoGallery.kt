package com.zwiedzak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.PlaceImage

@Composable
internal fun PlacePhotoGallery(place: Place) {
    val images = place.images.orEmpty().ifEmpty {
        place.imageUrl?.takeIf { it.isNotBlank() }?.let { listOf(PlaceImage(it)) }.orEmpty()
    }
    var selectedIndex by remember(place.id) { mutableStateOf<Int?>(null) }

    if (images.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.testTag("photo-strip"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(images) { index, image ->
                PlaceRemoteImage(
                    url = image.imageUrl,
                    description = image.caption ?: "Zdjęcie: ${place.name}",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedIndex = index }
                        .semantics { contentDescription = "Otwórz zdjęcie ${index + 1}" },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    selectedIndex?.takeIf { it in images.indices }?.let { index ->
        Dialog(
            onDismissRequest = { selectedIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val pagerState = rememberPagerState(initialPage = index, pageCount = { images.size })
            Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().testTag("photo-pager")
                ) { page ->
                    val image = images[page]
                    PlaceRemoteImage(
                        url = image.imageUrl,
                        description = image.caption ?: "Zdjęcie: ${place.name}",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(
                    onClick = { selectedIndex = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij galerię", tint = Color.White)
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${pagerState.currentPage + 1} / ${images.size}", color = Color.White)
                    images[pagerState.currentPage].caption?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfirmAddPhotoDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj zdjęcie") },
        text = { Text("Czy na pewno chcesz dodać wybrane zdjęcie? Będzie widoczne tylko dla Ciebie.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Dodaj zdjęcie") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
