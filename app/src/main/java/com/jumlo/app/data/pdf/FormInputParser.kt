package com.jumlo.app.data.pdf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** User input remains text until the actual AcroForm field determines its type. */
object FormInputParser {
    fun parse(rawInput: String): Map<String, FormEntry> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return emptyMap()
        if (trimmed.startsWith("{")) {
            val obj = Json.parseToJsonElement(trimmed) as? JsonObject
                ?: throw IllegalArgumentException("INVALID_FORM_INPUT")
            return obj.mapValues { (_, value) ->
                require(value is JsonPrimitive && value !== JsonNull) { "INVALID_FORM_INPUT" }
                FormEntry.Input(value.content)
            }
        }
        return buildMap {
            trimmed.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                require('=' in line) { "INVALID_FORM_INPUT" }
                val key = line.substringBefore('=').trim()
                require(key.isNotEmpty()) { "INVALID_FORM_INPUT" }
                put(key, FormEntry.Input(line.substringAfter('=').trim()))
            }
        }
    }
}
