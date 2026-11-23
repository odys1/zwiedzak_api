package com.zwiedzak.data.model

import com.google.gson.JsonParseException
import com.google.gson.JsonParser

data class DuplicatePlace(val name: String, val address: String?, val distanceMeters: Int?)

internal fun parseCreatePlaceConflict(body: String): List<DuplicatePlace>? {
    val root = try {
        JsonParser.parseString(body)
    } catch (_: JsonParseException) {
        return null
    }
    if (!root.isJsonObject) return null
    val objectValue = root.asJsonObject
    val error = objectValue.get("error")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    if (error !in listOf("POSSIBLE_DUPLICATES", "NEARBY_PLACES_REQUIRE_CONFIRMATION")) return null
    val candidates = objectValue.get("candidates")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    return candidates.mapNotNull { item ->
        if (!item.isJsonObject) return@mapNotNull null
        val candidate = item.asJsonObject
        val name = candidate.get("name")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val address = candidate.get("address")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val distance = candidate.get("distanceMeters")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        DuplicatePlace(name, address, distance)
    }.takeIf { it.isNotEmpty() }
}
