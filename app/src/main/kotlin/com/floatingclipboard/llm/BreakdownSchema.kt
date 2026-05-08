package com.floatingclipboard.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Schema OpenAPI/Gemini wymuszająca strukturę odpowiedzi przy responseMimeType=application/json.
 * Gemini gwarantuje zwrócenie poprawnego JSON-a zgodnego z tą strukturą — nie trzeba parsować
 * markdownu, model nie może odmówić formatu.
 */
internal val BREAKDOWN_SCHEMA: JsonElement = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
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
    putJsonArray("required") { add("items") }
}
