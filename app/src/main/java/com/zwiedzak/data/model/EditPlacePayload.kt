package com.zwiedzak.data.model

import com.google.gson.JsonObject
import com.google.gson.JsonNull

internal fun editPlacePayload(
    original: Place,
    request: CreatePlaceRequest
): JsonObject =
    JsonObject().apply {
        if (request.name != original.name) addProperty("name", request.name)
        if (request.category != original.category) addProperty("category", request.category)
        if (request.latitude != original.latitude) addProperty("latitude", request.latitude)
        if (request.longitude != original.longitude) addProperty("longitude", request.longitude)
        if (request.address != original.address) {
            if (request.address == null) add("address", JsonNull.INSTANCE) else addProperty("address", request.address)
        }
        if (request.description != original.description) {
            if (request.description == null) add("description", JsonNull.INSTANCE) else addProperty("description", request.description)
        }
        if (request.hasAccommodation != (original.hasAccommodation == 1)) addProperty("hasAccommodation", request.hasAccommodation)
        if (request.hasFood != (original.hasFood == 1)) addProperty("hasFood", request.hasFood)
        val editStatus = request.editVisitStatus
        if (editStatus != null) {
            if (editStatus != original.visitState) {
                if (editStatus == VisitStatus.AVOID || original.visitState == VisitStatus.AVOID) {
                    addProperty("visitStatus", editStatus.apiValue)
                } else {
                    addProperty("visited", editStatus.isVisited)
                }
            }
        } else if (request.visited != original.visitState.isVisited) {
            addProperty("visited", request.visited)
        }
        if (size() == 0) addProperty("name", request.name)
    }
