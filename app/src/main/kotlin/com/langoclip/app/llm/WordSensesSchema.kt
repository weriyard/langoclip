package com.langoclip.app.llm

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal val WORD_SENSES_SCHEMA: JsonElement = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("baseForm") { put("type", "STRING") }
        putJsonObject("senses") {
            put("type", "ARRAY")
            putJsonObject("items") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("partOfSpeech") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add("NOUN"); add("VERB"); add("ADJECTIVE"); add("ADVERB")
                            add("PRONOUN"); add("PREPOSITION"); add("IDIOM"); add("PHRASAL_VERB")
                            add("OTHER")
                        }
                    }
                    putJsonObject("meaning") { put("type", "STRING") }
                    putJsonObject("example") { put("type", "STRING") }
                    putJsonObject("exampleTranslation") { put("type", "STRING") }
                }
                putJsonArray("required") {
                    add("partOfSpeech"); add("meaning"); add("example"); add("exampleTranslation")
                }
                putJsonArray("propertyOrdering") {
                    add("partOfSpeech"); add("meaning"); add("example"); add("exampleTranslation")
                }
            }
        }
    }
    putJsonArray("required") { add("baseForm"); add("senses") }
    putJsonArray("propertyOrdering") { add("baseForm"); add("senses") }
}
