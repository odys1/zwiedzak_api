package com.zwiedzak.data.network

import com.zwiedzak.data.model.PlaceVisit
import java.io.IOException

internal suspend fun loadVisitPages(api: ApiService, placeId: String): List<PlaceVisit> {
    val visits = mutableListOf<PlaceVisit>()
    var offset = 0
    do {
        val page = api.getPlaceVisits(placeId, 50, offset)
        visits.addAll(page.items)
        if (page.hasMore && (page.items.isEmpty() || offset >= 1_000_000)) {
            throw IOException("Nieprawidłowa odpowiedź stronicowania historii.")
        }
        offset += 50
    } while (page.hasMore)
    return visits
}
