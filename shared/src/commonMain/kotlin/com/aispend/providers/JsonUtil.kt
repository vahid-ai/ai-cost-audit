package com.aispend.providers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull

val lenientJson = Json { ignoreUnknownKeys = true }

internal fun JsonObject.obj(key: String): JsonObject? =
    (this[key] as? JsonObject)

internal fun JsonObject.arr(key: String) =
    (this[key] as? kotlinx.serialization.json.JsonArray)?.jsonArray

internal fun JsonObject.text(key: String): String? =
    this[key]?.jsonPrimitive?.let { if (it.isString) it.content else it.content }

internal fun JsonObject.long(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull

internal fun JsonObject.bool(key: String): Boolean? =
    this[key]?.jsonPrimitive?.content?.let { it == "true" }

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

/** Parses a value that may be a JSON number or a stringified number. */
internal fun JsonObject.money(key: String): Double? {
    val el = this[key] ?: return null
    el.jsonPrimitive.doubleOrNull?.let { return it }
    return el.jsonPrimitive.content.toDoubleOrNull()
}
