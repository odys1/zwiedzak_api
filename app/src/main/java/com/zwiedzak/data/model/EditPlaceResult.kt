package com.zwiedzak.data.model

import com.google.gson.JsonParser
import java.io.IOException

internal data class EditPlaceResult(val id: String, val copied: Boolean)

internal fun parseEditPlaceResult(body: String?, originalId: String): EditPlaceResult {
    val json = try {
        body?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: com.google.gson.JsonParseException) {
        null
    } ?: throw IOException("Serwer nie zwrócił prawidłowej odpowiedzi po zapisie.")
    val id = json.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.takeIf { it.isNotBlank() }
        ?: throw IOException("Serwer nie zwrócił identyfikatora zapisanego miejsca.")
    val copiedValue = json.get("copied")
    val copied = if (copiedValue == null || copiedValue.isJsonNull) {
        id != originalId
    } else {
        if (!copiedValue.isJsonPrimitive || !copiedValue.asJsonPrimitive.isBoolean) {
            throw IOException("Serwer zwrócił nieprawidłową informację o kopii.")
        }
        copiedValue.asBoolean
    }
    if (copied == (id == originalId)) {
        throw IOException("Serwer zwrócił niespójny identyfikator zapisanego miejsca.")
    }
    return EditPlaceResult(id, copied)
}
