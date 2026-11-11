package com.zwiedzak.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import java.text.Normalizer
import java.util.Locale

data class Place(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @JsonAdapter(BooleanAsIntDeserializer::class)
    @SerializedName("hasAccommodation")
    val hasAccommodation: Int? = 0,
    @JsonAdapter(BooleanAsIntDeserializer::class)
    @SerializedName("hasFood")
    val hasFood: Int? = 0,
    @SerializedName("distanceMeters")
    val distanceMeters: Int? = null,
    @SerializedName("averageRating")
    val averageRating: Double? = null,
    @SerializedName("ratingsCount")
    val ratingsCount: Int? = 0,
    @SerializedName("visitStatus")
    val visitStatus: String? = null,
    @SerializedName(value = "isVisited", alternate = ["is_visited"])
    val isVisited: Boolean? = null,
    @SerializedName("markerColor")
    val markerColor: String? = null,
    @SerializedName(
        value = "imageUrl",
        alternate = ["image_url", "image", "photoUrl", "photo_url", "thumbnailUrl"]
    )
    val imageUrl: String? = null,
    @SerializedName("images")
    val images: List<PlaceImage>? = null,
    @SerializedName(value = "city", alternate = ["miasto"])
    val city: String? = null,
    @SerializedName(
        value = "voivodeship",
        alternate = ["wojewodztwo", "province", "region"]
    )
    val voivodeship: String? = null
) {
    val isFood: Boolean
        get() = hasFood == 1 || category?.contains("Gastronomia", ignoreCase = true) == true || category?.contains("Kawiarnia", ignoreCase = true) == true

    val isLodging: Boolean
        get() = hasAccommodation == 1 || category?.contains("Nocleg", ignoreCase = true) == true || category?.contains("Hotel", ignoreCase = true) == true

    val distanceKm: Double?
        get() = distanceMeters?.let { it / 1000.0 }

    val visitState: VisitStatus
        get() = visitStatus?.let(VisitStatus::fromApiValue)
            ?: if (isVisited == true) VisitStatus.VISITED else VisitStatus.PLANNED

    fun matchesNameQuery(query: String): Boolean =
        query.isBlank() || normalizeSearchText(name).contains(normalizeSearchText(query.trim()))

    fun matchesAddressQuery(query: String): Boolean {
        if (query.isBlank()) return true

        val searchableAddress = normalizeSearchText(
            listOfNotNull(address, city, voivodeship).joinToString(" ")
        )
        return searchableAddress.contains(normalizeSearchText(query.trim()))
    }

    private companion object {
        fun normalizeSearchText(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
    }
}

data class PlaceImage(
    @SerializedName(value = "imageUrl", alternate = ["url"])
    val imageUrl: String,
    @SerializedName("caption")
    val caption: String? = null
)

class BooleanAsIntDeserializer : JsonDeserializer<Int> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Int? {
        if (json.isJsonNull) return null
        val value = json.asJsonPrimitive
        return when {
            value.isBoolean -> if (value.asBoolean) 1 else 0
            value.isNumber -> value.asInt
            value.isString -> value.asString.toIntOrNull()
            else -> null
        }
    }
}

enum class VisitStatus(val apiValue: String, val label: String) {
    PLANNED("planned", "Nieodwiedzone"),
    VISITED("visited", "Odwiedzone"),
    AVOID("avoid", "Odwiedzone · nie wracam");

    companion object {
        fun fromApiValue(value: String?): VisitStatus =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) } ?: PLANNED
    }
}
