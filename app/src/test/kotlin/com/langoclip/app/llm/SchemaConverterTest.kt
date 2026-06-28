package com.langoclip.app.llm

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schema conversion from the Gemini format (UPPERCASE types, propertyOrdering, minItems/maxItems)
 * to OpenAI strict and Anthropic shapes. Each provider tolerates different keywords, so this
 * mapping is load-bearing for structured output across providers.
 */
class SchemaConverterTest {

    // A representative Gemini schema: object -> array(minItems/maxItems) -> string item.
    private val gemini: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "ARRAY")
                put("minItems", 5)
                put("maxItems", 5)
                putJsonObject("items") { put("type", "STRING") }
            }
        }
        put("propertyOrdering", buildJsonArray { add("items") })
    }

    @Test
    fun `OpenAI strict lowercases types`() {
        val out = toOpenAiStrictSchema(gemini).jsonObject
        assertEquals("object", out["type"]!!.jsonPrimitive.content)
        val arr = out["properties"]!!.jsonObject["items"]!!.jsonObject
        assertEquals("array", arr["type"]!!.jsonPrimitive.content)
        assertEquals("string", arr["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `OpenAI strict adds additionalProperties false to objects and drops unsupported keys`() {
        val out = toOpenAiStrictSchema(gemini).jsonObject
        assertEquals(JsonPrimitive(false), out["additionalProperties"])
        assertNull("propertyOrdering must be removed", out["propertyOrdering"])
        val arr = out["properties"]!!.jsonObject["items"]!!.jsonObject
        assertNull("minItems unsupported in strict mode", arr["minItems"])
        assertNull("maxItems unsupported in strict mode", arr["maxItems"])
    }

    @Test
    fun `Anthropic keeps minItems and maxItems but lowercases types`() {
        val out = toAnthropicSchema(gemini).jsonObject
        assertEquals("object", out["type"]!!.jsonPrimitive.content)
        val arr = out["properties"]!!.jsonObject["items"]!!.jsonObject
        assertEquals(JsonPrimitive(5), arr["minItems"])
        assertEquals(JsonPrimitive(5), arr["maxItems"])
    }

    @Test
    fun `Anthropic drops propertyOrdering and does not force additionalProperties`() {
        val out = toAnthropicSchema(gemini).jsonObject
        assertNull("propertyOrdering must be removed", out["propertyOrdering"])
        assertFalse(
            "Anthropic schema should not force additionalProperties",
            out.containsKey("additionalProperties"),
        )
    }
}
