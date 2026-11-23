package com.zwiedzak.data.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class PlaceImageTarget(val url: String, val requiresAuth: Boolean)

internal fun resolvePlaceImage(baseUrl: String, imageUrl: String): PlaceImageTarget? {
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    val resolved = base.resolve(imageUrl.trim()) ?: return null
    val prefix = base.encodedPath.trimEnd('/') + "/api/v1/images/"
    val id = resolved.encodedPath.removePrefix(prefix)
    val isApiImage = resolved.scheme == base.scheme &&
        resolved.host == base.host &&
        resolved.port == base.port &&
        resolved.encodedPath.startsWith(prefix) &&
        id.matches(Regex("[0-9a-fA-F-]{36}"))
    return PlaceImageTarget(resolved.toString(), isApiImage)
}
