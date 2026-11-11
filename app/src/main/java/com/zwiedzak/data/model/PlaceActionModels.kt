package com.zwiedzak.data.model

import com.google.gson.annotations.SerializedName

data class CreatePlaceRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("category")
    val category: String,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("address")
    val address: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("hasAccommodation")
    val hasAccommodation: Boolean,
    @SerializedName("hasFood")
    val hasFood: Boolean,
    @SerializedName("visibility")
    val visibility: String,
    @SerializedName("confirmDistinct")
    val confirmDistinct: Boolean
)

data class UpdateVisitStatusRequest(
    @SerializedName("status")
    val status: String
)

data class AddPlaceImageRequest(
    @SerializedName("imageUrl")
    val imageUrl: String,
    @SerializedName("caption")
    val caption: String?
)

data class SubmitRatingRequest(
    @SerializedName("score")
    val score: Int
)
