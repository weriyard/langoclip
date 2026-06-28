package com.langoclip.app.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Schema for 5 usage examples of a phrase. minItems/maxItems enforce exactly 5. */
internal val EXAMPLES_SCHEMA: JsonElement = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("examples") {
            put("type", "ARRAY")
            put("minItems", 5)
            put("maxItems", 5)
            putJsonObject("items") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("english") { put("type", "STRING") }
                    putJsonObject("highlightedSpan") { put("type", "STRING") }
                    putJsonObject("translation") { put("type", "STRING") }
                    putJsonObject("usageNote") { put("type", "STRING") }
                }
                putJsonArray("required") {
                    add("english"); add("highlightedSpan"); add("translation"); add("usageNote")
                }
                putJsonArray("propertyOrdering") {
                    add("english"); add("highlightedSpan"); add("translation"); add("usageNote")
                }
            }
        }
    }
    putJsonArray("required") { add("examples") }
}
