package com.zwiedzak.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zwiedzak.data.auth.AuthTokenManager
import com.zwiedzak.data.network.RetrofitInstance
import com.zwiedzak.data.network.resolvePlaceImage

@Composable
internal fun PlaceRemoteImage(
    url: String,
    description: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val target = resolvePlaceImage(RetrofitInstance.getBaseUrl(), url)
    val token = AuthTokenManager.getInstance(context).getToken()
    var failed by remember(url, token) { mutableStateOf(false) }
    val request = remember(target, token) {
        target?.let {
            ImageRequest.Builder(context)
                .data(it.url)
                .apply {
                    if (it.requiresAuth) {
                        token?.let { accessToken -> setHeader("Authorization", "Bearer $accessToken") }
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
                .build()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onLoading = { failed = false },
                onError = { failed = true }
            )
        }
        if (request == null || failed) {
            Icon(Icons.Default.BrokenImage, contentDescription = "Nie udało się wczytać zdjęcia",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
