package com.langoclip.app.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAPI/Gemini schema enforcing the response structure when responseMimeType=application/json.
 * Gemini guarantees returning valid JSON conforming to this structure — no need to parse
 * markdown, the model cannot refuse the format.
 */
internal val BREAKDOWN_SCHEMA: JsonElement = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("fullTranslation") { put("type", "STRING") }
        putJsonObject("items") {
            put("type", "ARRAY")
            putJsonObject("items") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("original") { put("type", "STRING") }
                    putJsonObject("translation") { put("type", "STRING") }
                    putJsonObject("partOfSpeech") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add("NOUN"); add("VERB"); add("ADJECTIVE"); add("ADVERB")
                            add("PRONOUN"); add("PREPOSITION"); add("IDIOM"); add("PHRASAL_VERB")
                            add("OTHER")
                        }
                    }
                    putJsonObject("explanation") { put("type", "STRING") }
                }
                putJsonArray("required") {
                    add("original"); add("translation"); add("partOfSpeech"); add("explanation")
                }
                putJsonArray("propertyOrdering") {
                    add("original"); add("translation"); add("partOfSpeech"); add("explanation")
                }
            }
        }
    }
    putJsonArray("required") { add("fullTranslation"); add("items") }
    putJsonArray("propertyOrdering") { add("fullTranslation"); add("items") }
}
