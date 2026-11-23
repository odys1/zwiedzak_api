package com.zwiedzak.data.model

import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal fun isCreatedAvoidVisitConfirmed(body: String?): Boolean {
    val root = try {
        JsonParser.parseString(body.orEmpty())
    } catch (_: JsonParseException) {
        return false
    }
    if (!root.isJsonObject) return false
    val result = root.asJsonObject
    val status = result.get("visitStatus")
    val lastVisit = result.get("lastVisitedAt")
    return status != null && status.isJsonPrimitive && status.asJsonPrimitive.isString &&
        status.asString == VisitStatus.AVOID.apiValue &&
        lastVisit != null && lastVisit.isJsonPrimitive && lastVisit.asJsonPrimitive.isString &&
        lastVisit.asString.isNotBlank()
}
