package com.zwiedzak.data.model

import com.google.gson.annotations.SerializedName

data class NearbyPlacesResponse(
    @SerializedName("items")
    val items: List<Place>,
    @SerializedName("count")
    val count: Int
)
