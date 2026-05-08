package com.floatingclipboard.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Konwertuje schematy z formatu Gemini (UPPERCASE typy) do strict JSON Schema OpenAI:
 * - lowercases values pod kluczem `type` (`STRING` → `string`, `OBJECT` → `object`, ...).
 * - dorzuca `additionalProperties: false` do każdego obiektu (wymóg strict mode).
 * - usuwa pola niewspierane przez OpenAI strict: `propertyOrdering` (Gemini-specific),
 *   `minItems`/`maxItems` (nie supported w strict mode).
 *
 * Założenie: nasze schematy mają wszystkie pola w `required` (więc nie trzeba ich uzupełniać —
 * to też wymóg OpenAI strict). Jeśli kiedyś dodamy pole nullable, trzeba będzie tu rozszerzyć.
 */
internal fun toOpenAiStrictSchema(geminiSchema: JsonElement): JsonElement = convertNode(geminiSchema)

/**
 * Wariant pod Anthropic tool input_schema: lowercase typy, usuwamy Gemini-specific
 * `propertyOrdering`, ale zachowujemy minItems/maxItems (Anthropic je obsługuje) i nie wymuszamy
 * `additionalProperties: false`.
 */
internal fun toAnthropicSchema(geminiSchema: JsonElement): JsonElement = convertAnthropicNode(geminiSchema)

private fun convertAnthropicNode(node: JsonElement): JsonElement = when (node) {
    is JsonObject -> {
        val out = mutableMapOf<String, JsonElement>()
        for ((key, value) in node) {
            when (key) {
                "type" -> out[key] = if (value is JsonPrimitive && value.isString) {
                    JsonPrimitive(value.content.lowercase())
                } else value
                "propertyOrdering" -> Unit
                else -> out[key] = convertAnthropicNode(value)
            }
        }
        JsonObject(out)
    }
    is JsonArray -> JsonArray(node.map { convertAnthropicNode(it) })
    else -> node
}

private fun convertNode(node: JsonElement): JsonElement = when (node) {
    is JsonObject -> {
        val out = mutableMapOf<String, JsonElement>()
        for ((key, value) in node) {
            when (key) {
                "type" -> out[key] = if (value is JsonPrimitive && value.isString) {
                    JsonPrimitive(value.content.lowercase())
                } else value
                "propertyOrdering", "minItems", "maxItems" -> Unit  // skip
                else -> out[key] = convertNode(value)
            }
        }
        val type = out["type"]
        if (type is JsonPrimitive && type.content == "object" && "additionalProperties" !in out) {
            out["additionalProperties"] = JsonPrimitive(false)
        }
        JsonObject(out)
    }
    is JsonArray -> JsonArray(node.map { convertNode(it) })
    else -> node
}
