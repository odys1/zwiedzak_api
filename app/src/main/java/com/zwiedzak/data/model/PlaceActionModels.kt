package com.zwiedzak.data.model

import com.google.gson.annotations.SerializedName

data class PlaceCategory(
    @SerializedName("code")
    val code: String,
    @SerializedName("name")
    val name: String
)

data class CategoriesResponse(
    @SerializedName("items")
    val items: List<PlaceCategory>
)

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
    val confirmDistinct: Boolean,
    @SerializedName("visited")
    val visited: Boolean
)

data class UpdateVisitStatusRequest(
    @SerializedName("status")
    val status: String
)

data class PlaceVisit(
    @SerializedName("id")
    val id: String,
    @SerializedName("visitedAt")
    val visitedAt: String
)

data class PlaceVisitsResponse(
    @SerializedName("items")
    val items: List<PlaceVisit>,
    @SerializedName("limit")
    val limit: Int,
    @SerializedName("offset")
    val offset: Int,
    @SerializedName("hasMore")
    val hasMore: Boolean
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
