package com.floatingclipboard.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts schemas from the Gemini format (UPPERCASE types) to OpenAI strict JSON Schema:
 * - lowercases values under the `type` key (`STRING` → `string`, `OBJECT` → `object`, ...).
 * - adds `additionalProperties: false` to every object (required by strict mode).
 * - removes fields unsupported by OpenAI strict: `propertyOrdering` (Gemini-specific),
 *   `minItems`/`maxItems` (not supported in strict mode).
 *
 * Assumption: our schemas have all fields in `required` (so we don't need to fill them in —
 * also an OpenAI strict requirement). If we ever add a nullable field, this needs extending.
 */
internal fun toOpenAiStrictSchema(geminiSchema: JsonElement): JsonElement = convertNode(geminiSchema)

/**
 * Variant for Anthropic tool input_schema: lowercase types, remove Gemini-specific
 * `propertyOrdering`, but keep minItems/maxItems (Anthropic supports them) and don't force
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
